package de.verdox.pv_miner.miningcontroller;

import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.entity.EntityControllerService;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.miner.MinerEntityController;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.miningcontroller.dsl.ControllerDSL;
import de.verdox.pv_miner.pvsite.PVSiteEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClusterController {
    private static final Logger LOGGER = Logger.getLogger(ClusterController.class.getSimpleName());

    private final String clusterName;
    private final List<MinerEntity<?>> minerCluster;
    private final PVSiteEntity pvSite;
    private final MinerControllerConfig config;

    private final Map<UUID, MinerLock> activeLocks = new ConcurrentHashMap<>();

    private ControllerDSL.OperatingMode activeMode = null;

    public ClusterController(String clusterName, List<MinerEntity<?>> minerCluster, PVSiteEntity pvSite, MinerControllerConfig config) {
        this.clusterName = clusterName;
        this.minerCluster = minerCluster;
        this.pvSite = pvSite;
        this.config = config;
    }

    public void evaluate() {
        if (minerCluster.isEmpty()) return;

        EntityControllerService controllerService = SpringContextHelper.getBean(EntityControllerService.class);
        EntityQueryService queryService = SpringContextHelper.getBean(EntityQueryService.class);

        double totalClusterCapacity = calculateTotalClusterCapacity(queryService);

        try {
            ControllerDSL.OperatingMode nextMode = null;

            for (Map.Entry<String, ControllerDSL.OperatingMode> entry : config.getConfigEntries().entrySet()) {
                ControllerDSL.OperatingMode mode = entry.getValue();

                if (activeMode != null && activeMode.modeName().equals(mode.modeName())) {
                    if (!mode.stopCondition().evaluate(pvSite)) {
                        nextMode = mode;
                        break;
                    } else {
                        LOGGER.info("Cluster " + clusterName + " dropping mode: Stop condition met for '" + mode.modeName() + "'.");
                    }
                }
                else if (mode.startCondition().evaluate(pvSite)) {
                    LOGGER.info("Cluster " + clusterName + " starting mode: Start condition met for '" + mode.modeName() + "'.");
                    nextMode = mode;
                    break;
                }
            }

            if (activeMode != nextMode) {
                LOGGER.info("Cluster " + clusterName + " State Transition: " +
                        (activeMode != null ? activeMode.modeName() : "NONE") + " -> " +
                        (nextMode != null ? nextMode.modeName() : "NONE"));

                activeMode = nextMode;

                if (activeMode == null) {
                    pauseAllUnlockedMiners(controllerService, queryService);
                    return;
                }
            }

            if (activeMode != null) {
                for (ControllerDSL.ControllerAction action : activeMode.actions()) {
                    double targetPowerWatts = action.valueExpression().evaluate(pvSite, totalClusterCapacity);
                    executeDynamicDistribution(controllerService, queryService, action, targetPowerWatts, activeMode);
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error evaluating state for cluster: " + clusterName, e);
        }
    }

    private void executeDynamicDistribution(EntityControllerService controllerService, EntityQueryService queryService, ControllerDSL.ControllerAction action, double targetPowerWatts, ControllerDSL.OperatingMode mode) {
        if (action.controllerActionType() != ControllerDSL.ControllerActionType.SET_POWER_TARGET) {
            for (MinerEntity<?> miner : minerCluster) {
                boolean isEmergencyPause = action.controllerActionType() == ControllerDSL.ControllerActionType.PAUSE;

                if (isEmergencyPause || !isMinerStateLocked(miner)) {
                    var controller = controllerService.getController(miner);
                    action.controllerActionType().apply(controller, null);

                    boolean supportsScaling = miner.getOS().supportsDynamicPowerScaling();
                    var stats = queryService.getLastResult(miner, MinerStats.DEFAULT);
                    double expectedPower = isEmergencyPause ? 0.0 : (supportsScaling ? stats.minPowerTarget() : stats.maxPowerTarget());

                    Instant stateUnlock = Instant.now().plus(isEmergencyPause ? getEffectiveMinIdleTime(miner, mode) : getEffectiveMinRunTime(miner, mode));
                    activeLocks.put(miner.getId(), new MinerLock(stateUnlock, stateUnlock, Instant.now(), expectedPower));
                }
            }
            return;
        }

        if (action.strategy() == ControllerDSL.MinerDistributionStrategy.SEQUENTIAL) {
            double remainingPower = targetPowerWatts;

            for (MinerEntity<?> miner : minerCluster) {
                var stats = queryService.getLastResult(miner, MinerStats.DEFAULT);
                var controller = controllerService.getController(miner);
                MinerLock currentLock = activeLocks.get(miner.getId());

                boolean supportsScaling = miner.getOS().supportsDynamicPowerScaling();

                double maxPower = stats.maxPowerTarget();
                double minPower = supportsScaling ? stats.minPowerTarget() : maxPower;
                int stepSize = getEffectiveStepSize(miner, action, supportsScaling);

                boolean isCurrentlyMining = currentLock != null && currentLock.expectedPowerWatts() > 0;

                if (isMinerPowerLocked(miner)) {
                    remainingPower -= stats.approximatedPowerUsageWatts();
                    continue;
                }

                if (remainingPower >= minPower) {
                    double allocation = Math.min(remainingPower, maxPower);
                    double steppedAllocation = roundToStepDown(allocation, stepSize);

                    if (steppedAllocation >= minPower) {
                        applyPowerAndRecordState(miner, controller, action, steppedAllocation, mode, isCurrentlyMining);
                        remainingPower -= steppedAllocation;
                    } else {
                        handleMinerShutdownOrUndervolt(miner, queryService, controller, mode, minPower, isCurrentlyMining, supportsScaling);
                    }
                } else {
                    handleMinerShutdownOrUndervolt(miner, queryService, controller, mode, minPower, isCurrentlyMining, supportsScaling);
                }
            }
        }

        else if (action.strategy() == ControllerDSL.MinerDistributionStrategy.EQUAL_DISTRIBUTION) {
            double remainingPower = targetPowerWatts;
            List<MinerEntity<?>> adjustableMiners = new ArrayList<>();

            for (MinerEntity<?> miner : minerCluster) {
                if (isMinerPowerLocked(miner)) {
                    var stats = queryService.getLastResult(miner, MinerStats.DEFAULT);
                    remainingPower -= stats.approximatedPowerUsageWatts();
                } else {
                    adjustableMiners.add(miner);
                }
            }

            if (remainingPower <= 0) {
                for (MinerEntity<?> miner : adjustableMiners) {
                    var controller = controllerService.getController(miner);
                    MinerLock lock = activeLocks.get(miner.getId());
                    boolean isMining = lock != null && lock.expectedPowerWatts() > 0;
                    boolean supportsScaling = miner.getOS().supportsDynamicPowerScaling();
                    double minPower = supportsScaling ? queryService.getLastResult(miner, MinerStats.DEFAULT).minPowerTarget() : queryService.getLastResult(miner, MinerStats.DEFAULT).maxPowerTarget();
                    handleMinerShutdownOrUndervolt(miner, queryService, controller, mode, minPower, isMining, supportsScaling);
                }
                return;
            }

            adjustableMiners.sort(java.util.Comparator.comparing(m -> m.getOS().supportsDynamicPowerScaling()));

            int minersLeft = adjustableMiners.size();
            if (minersLeft == 0) return;

            for (MinerEntity<?> miner : adjustableMiners) {
                var stats = queryService.getLastResult(miner, MinerStats.DEFAULT);
                var controller = controllerService.getController(miner);
                MinerLock lock = activeLocks.get(miner.getId());
                boolean isMining = lock != null && lock.expectedPowerWatts() > 0;
                boolean supportsScaling = miner.getOS().supportsDynamicPowerScaling();

                double maxPower = stats.maxPowerTarget();
                double minPower = supportsScaling ? stats.minPowerTarget() : maxPower;

                double fairShare = remainingPower / minersLeft;
                double allocation = Math.min(fairShare, maxPower);
                int stepSize = supportsScaling ? (action.stepSizeWatts() > 0 ? action.stepSizeWatts() : 1) : 1;
                double steppedAllocation = roundToStepDown(allocation, stepSize);

                if (steppedAllocation >= minPower) {
                    applyPowerAndRecordState(miner, controller, action, steppedAllocation, mode, isMining);
                    remainingPower -= steppedAllocation;
                } else {
                    handleMinerShutdownOrUndervolt(miner, queryService, controller, mode, minPower, isMining, supportsScaling);
                }
                minersLeft--;
            }
        }
        else if (action.strategy() == ControllerDSL.MinerDistributionStrategy.EFFICIENCY_FIRST) {
            double remainingPower = targetPowerWatts;
            List<MinerEntity<?>> adjustableMiners = new ArrayList<>();

            for (MinerEntity<?> miner : minerCluster) {
                if (isMinerPowerLocked(miner)) {
                    var stats = queryService.getLastResult(miner, MinerStats.DEFAULT);
                    remainingPower -= stats.approximatedPowerUsageWatts();
                } else {
                    adjustableMiners.add(miner);
                }
            }

            if (remainingPower <= 0) {
                for (MinerEntity<?> miner : adjustableMiners) {
                    var controller = controllerService.getController(miner);
                    MinerLock lock = activeLocks.get(miner.getId());
                    boolean isMining = lock != null && lock.expectedPowerWatts() > 0;
                    boolean supportsScaling = miner.getOS().supportsDynamicPowerScaling();
                    double minPower = supportsScaling ? queryService.getLastResult(miner, MinerStats.DEFAULT).minPowerTarget() : queryService.getLastResult(miner, MinerStats.DEFAULT).maxPowerTarget();
                    handleMinerShutdownOrUndervolt(miner, queryService, controller, mode, minPower, isMining, supportsScaling);
                }
                return;
            }

            adjustableMiners.sort(java.util.Comparator.comparingDouble(miner -> {
                var stats = queryService.getLastResult(miner, MinerStats.DEFAULT);
                if (stats.terahashPerSecond() <= 0) return Double.MAX_VALUE;
                return stats.approximatedPowerUsageWatts() / stats.terahashPerSecond();
            }));

            for (MinerEntity<?> miner : adjustableMiners) {
                var stats = queryService.getLastResult(miner, MinerStats.DEFAULT);
                var controller = controllerService.getController(miner);
                MinerLock currentLock = activeLocks.get(miner.getId());
                boolean supportsScaling = miner.getOS().supportsDynamicPowerScaling();

                double maxPower = stats.maxPowerTarget();
                double minPower = supportsScaling ? stats.minPowerTarget() : maxPower;
                int stepSize = getEffectiveStepSize(miner, action, supportsScaling);
                boolean isCurrentlyMining = currentLock != null && currentLock.expectedPowerWatts() > 0;

                if (remainingPower >= minPower) {
                    double allocation = Math.min(remainingPower, maxPower);
                    double steppedAllocation = roundToStepDown(allocation, stepSize);

                    if (steppedAllocation >= minPower) {
                        applyPowerAndRecordState(miner, controller, action, steppedAllocation, mode, isCurrentlyMining);
                        remainingPower -= steppedAllocation;
                    } else {
                        handleMinerShutdownOrUndervolt(miner, queryService, controller, mode, minPower, isCurrentlyMining, supportsScaling);
                    }
                } else {
                    handleMinerShutdownOrUndervolt(miner, queryService, controller, mode, minPower, isCurrentlyMining, supportsScaling);
                }
            }
        }
    }

    private void handleMinerShutdownOrUndervolt(MinerEntity<?> miner, EntityQueryService queryService, Object controller, ControllerDSL.OperatingMode mode, double minPower, boolean isCurrentlyMining, boolean supportsScaling) {
        if (isCurrentlyMining && isMinerStateLocked(miner)) {
            double targetWatts = supportsScaling ? minPower : queryService.getLastResult(miner, MinerStats.DEFAULT).maxPowerTarget();

            ControllerDSL.ControllerActionType.SET_POWER_TARGET.apply((MinerEntityController) controller, String.valueOf((long) targetWatts));
            Instant existingStateUnlock = activeLocks.get(miner.getId()).stateUnlockTime();
            activeLocks.put(miner.getId(), new MinerLock(existingStateUnlock, Instant.now().plus(Duration.ofMinutes(2)), Instant.now(), targetWatts));

            LOGGER.warning("Miner " + miner.getId() + " is in active state lock! " +
                    (supportsScaling ? "Undervolt to min power target (" + targetWatts + "W)." : "OS does not support dynamic power scaling. It must run on (" + targetWatts + "W)."));
        } else {
            ControllerDSL.ControllerActionType.PAUSE.apply((MinerEntityController) controller, null);
            Instant stateUnlock = Instant.now().plus(getEffectiveMinIdleTime(miner, mode));
            activeLocks.put(miner.getId(), new MinerLock(stateUnlock, stateUnlock, Instant.now(), 0.0));
        }
    }
    private void applyPowerAndRecordState(MinerEntity<?> miner, Object controller, ControllerDSL.ControllerAction action, double watts, ControllerDSL.OperatingMode mode, boolean wasAlreadyMining) {
        action.controllerActionType().apply((MinerEntityController) controller, String.valueOf((long) watts));

        Instant stateUnlock = wasAlreadyMining && activeLocks.containsKey(miner.getId())
                ? activeLocks.get(miner.getId()).stateUnlockTime()
                : Instant.now().plus(getEffectiveMinRunTime(miner, mode));

        Instant powerUnlock = Instant.now().plus(Duration.ofMinutes(2));

        activeLocks.put(miner.getId(), new MinerLock(stateUnlock, powerUnlock, Instant.now(), watts));
    }

    private boolean isMinerStateLocked(MinerEntity<?> miner) {
        MinerLock lock = activeLocks.get(miner.getId());
        if (lock == null) return false;
        if (Instant.now().isAfter(lock.stateUnlockTime())) {
            return false;
        }
        return true;
    }

    private boolean isMinerPowerLocked(MinerEntity<?> miner) {
        MinerLock lock = activeLocks.get(miner.getId());
        if (lock == null) return false;
        if (Instant.now().isAfter(lock.powerUnlockTime())) {
            return false;
        }
        return true;
    }

    private void pauseAllUnlockedMiners(EntityControllerService controllerService, EntityQueryService queryService) {
        for (MinerEntity<?> miner : minerCluster) {
            if (!isMinerStateLocked(miner)) {
                var controller = controllerService.getController(miner);
                ControllerDSL.ControllerActionType.PAUSE.apply((MinerEntityController) controller, null);
                Instant stateUnlock = Instant.now().plus(Duration.ofMinutes(5));
                activeLocks.put(miner.getId(), new MinerLock(stateUnlock, stateUnlock, Instant.now(), 0.0));
            }
        }
    }

    private double calculateTotalClusterCapacity(EntityQueryService queryService) {
        return minerCluster.stream()
                .mapToDouble(miner -> queryService.getLastResult(miner, MinerStats.DEFAULT).maxPowerTarget())
                .sum();
    }

    private double roundToStepDown(double value, int stepSize) {
        if (stepSize <= 1) return value;
        return Math.floor(value / stepSize) * stepSize;
    }

    private Duration getEffectiveMinRunTime(MinerEntity<?> miner, ControllerDSL.OperatingMode mode) {
        if (miner.getMinRunTimeMinutes() != null && miner.getMinRunTimeMinutes() > 0) {
            return Duration.ofMinutes(miner.getMinRunTimeMinutes());
        }
        return mode.minRunTime();
    }

    private Duration getEffectiveMinIdleTime(MinerEntity<?> miner, ControllerDSL.OperatingMode mode) {
        if (miner.getMinIdleTimeMinutes() != null && miner.getMinIdleTimeMinutes() > 0) {
            return Duration.ofMinutes(miner.getMinIdleTimeMinutes());
        }
        return mode.minIdleTime();
    }

    private int getEffectiveStepSize(MinerEntity<?> miner, ControllerDSL.ControllerAction action, boolean supportsScaling) {
        if (!supportsScaling) return 1;

        if (miner.getPowerStepSizeWatts() != null && miner.getPowerStepSizeWatts() > 0) {
            return miner.getPowerStepSizeWatts();
        }
        return action.stepSizeWatts() > 0 ? action.stepSizeWatts() : 250;
    }
}