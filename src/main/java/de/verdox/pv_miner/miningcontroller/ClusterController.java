package de.verdox.pv_miner.miningcontroller;

import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.entity.EntityControllerService;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.entity.EntityService;
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
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClusterController {
    private static final Logger LOGGER = Logger.getLogger(ClusterController.class.getSimpleName());

    private final String clusterName;
    private final EntityService entityService;
    private final List<UUID> minerCluster;
    private final PVSiteEntity pvSite;
    private final MinerControllerConfig config;
    private final Consumer<MinerClusterService.ClusterInstance.ClusterStateSnapshot> stateLogger;
    private String tickEventMessage = null;

    private final Map<UUID, MinerLock> activeLocks = new ConcurrentHashMap<>();

    private ControllerDSL.OperatingMode activeMode = null;

    public ClusterController(String clusterName, EntityService entityService, List<UUID> minerCluster, PVSiteEntity pvSite, MinerControllerConfig config, Consumer<MinerClusterService.ClusterInstance.ClusterStateSnapshot> stateLogger) {
        this.clusterName = clusterName;
        this.entityService = entityService;
        this.minerCluster = minerCluster;
        this.pvSite = pvSite;
        this.config = config;
        this.stateLogger = stateLogger;
    }

    public Map<UUID, MinerLock> getActiveLocks() {
        return java.util.Collections.unmodifiableMap(this.activeLocks);
    }

    public void evaluate() {
        if (minerCluster.isEmpty()) return;
        this.tickEventMessage = null;

        EntityControllerService controllerService = SpringContextHelper.getBean(EntityControllerService.class);
        EntityQueryService queryService = SpringContextHelper.getBean(EntityQueryService.class);

        double totalClusterCapacity = calculateTotalClusterCapacity(queryService);
        double currentTickTargetPower = 0.0;

        try {
            ControllerDSL.OperatingMode nextMode = null;

            for (Map.Entry<String, ControllerDSL.OperatingMode> entry : config.getConfigEntries().entrySet()) {
                ControllerDSL.OperatingMode mode = entry.getValue();

                if (activeMode != null && activeMode.modeName().equals(mode.modeName())) {
                    if (!mode.stopCondition().evaluate(pvSite)) {
                        nextMode = mode;
                        break;
                    } else {
                        String msg = "Stop condition met for '" + mode.modeName() + "'";
                        LOGGER.info("Cluster " + clusterName + " dropping mode: " + msg);
                        this.tickEventMessage = msg;
                    }
                }
                else if (mode.startCondition().evaluate(pvSite)) {
                    String msg = "Start condition met for '" + mode.modeName() + "'";
                    LOGGER.info("Cluster " + clusterName + " starting mode: " + msg);
                    this.tickEventMessage = msg;
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

                    double allocated = activeLocks.values().stream().mapToDouble(MinerLock::expectedPowerWatts).sum();
                    stateLogger.accept(new MinerClusterService.ClusterInstance.ClusterStateSnapshot(Instant.now(), 0.0, allocated, "Idle", tickEventMessage));
                    return;
                }
            }

            if (activeMode != null) {
                for (ControllerDSL.ControllerAction action : activeMode.actions()) {
                    if (action.controllerActionType() != ControllerDSL.ControllerActionType.SET_POWER_TARGET) {
                        executeDynamicDistribution(controllerService, queryService, action, 0.0, activeMode);
                    }
                }

                ControllerDSL.ControllerAction powerAction = activeMode.actions().stream()
                        .filter(a -> a.controllerActionType() == ControllerDSL.ControllerActionType.SET_POWER_TARGET)
                        .findFirst()
                        .orElse(null);

                if (powerAction != null) {
                    double targetPowerWatts = powerAction.valueExpression().evaluate(pvSite, totalClusterCapacity);
                    currentTickTargetPower = targetPowerWatts;
                    executeDynamicDistribution(controllerService, queryService, powerAction, targetPowerWatts, activeMode);
                }
            }

            double totalAllocatedPower = activeLocks.values().stream()
                    .mapToDouble(MinerLock::expectedPowerWatts)
                    .sum();

            stateLogger.accept(new MinerClusterService.ClusterInstance.ClusterStateSnapshot(
                    Instant.now(),
                    currentTickTargetPower,
                    totalAllocatedPower,
                    activeMode != null ? activeMode.modeName() : "Idle",
                    tickEventMessage
            ));

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error evaluating state for cluster: " + clusterName, e);
        }
    }

    private List<MinerEntity<?>> getFreshMiners() {
        return entityService.getFreshList(minerCluster);
    }

    private void executeDynamicDistribution(EntityControllerService controllerService, EntityQueryService queryService, ControllerDSL.ControllerAction action, double targetPowerWatts, ControllerDSL.OperatingMode mode) {
        if (action.controllerActionType() != ControllerDSL.ControllerActionType.SET_POWER_TARGET) {
            for (MinerEntity<?> miner : getFreshMiners()) {
                boolean isEmergencyPause = action.controllerActionType() == ControllerDSL.ControllerActionType.PAUSE;

                if (isEmergencyPause || !isMinerStateLocked(miner)) {
                    var controller = controllerService.getController(miner);
                    action.controllerActionType().apply(controller, null);

                    boolean supportsScaling = miner.getOS().supportsDynamicPowerScaling();
                    var stats = queryService.getLastResult(miner, MinerStats.DEFAULT);
                    double expectedPower = isEmergencyPause ? 0.0 : (supportsScaling ? getMinPowerTargetForMiner(miner) : getMaxPowerForMiner(miner));

                    Instant stateUnlock = Instant.now().plus(isEmergencyPause ? getEffectiveMinIdleTime(miner, mode) : getEffectiveMinRunTime(miner, mode));
                    Instant powerUnlock = Instant.now();
                    activeLocks.put(miner.getId(), new MinerLock(stateUnlock, powerUnlock, Instant.now(), expectedPower));
                    String actionName = isEmergencyPause ? "⏸ Stop" : "▶ Start";
                    String minerName = miner.getName() != null ? miner.getName() : "Miner";
                    appendTickEvent(actionName + " (" + minerName + ")");
                }
            }
            return;
        }

        if (action.strategy() == ControllerDSL.MinerDistributionStrategy.SEQUENTIAL) {
            double remainingPower = targetPowerWatts;

            for (MinerEntity<?> miner : getFreshMiners()) {
                var stats = queryService.getLastResult(miner, MinerStats.DEFAULT);
                var controller = controllerService.getController(miner);
                MinerLock currentLock = activeLocks.get(miner.getId());

                boolean supportsScaling = miner.getOS().supportsDynamicPowerScaling();

                double maxPower = getMaxPowerForMiner(miner);
                double minPower = supportsScaling ? getMaxPowerForMiner(miner) : maxPower;
                int stepSize = getEffectiveStepSize(miner, action, supportsScaling);

                boolean isCurrentlyMining = currentLock != null && currentLock.expectedPowerWatts() > 0;

                if (isMinerPowerLocked(miner)) {
                    remainingPower -= currentLock.expectedPowerWatts();
                    continue;
                }

                double allocation = Math.min(remainingPower, maxPower);
                if (allocation < minPower) {
                    handleMinerShutdownOrUndervolt(miner, queryService, controller, mode, minPower, isCurrentlyMining, supportsScaling);
                } else {
                    double steppedAllocation = roundToStepDown(allocation, stepSize);
                    if (steppedAllocation >= minPower) {
                        applyPowerAndRecordState(miner, controller, action, steppedAllocation, mode, isCurrentlyMining);
                        remainingPower -= steppedAllocation;
                    } else {
                        handleMinerShutdownOrUndervolt(miner, queryService, controller, mode, minPower, isCurrentlyMining, supportsScaling);
                    }
                }
            }
        }

        else if (action.strategy() == ControllerDSL.MinerDistributionStrategy.EQUAL_DISTRIBUTION) {
            double remainingPower = targetPowerWatts;
            List<MinerEntity<?>> adjustableMiners = new ArrayList<>();

            for (MinerEntity<?> miner : getFreshMiners()) {
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
                    double minPower = supportsScaling ? getMinPowerTargetForMiner(miner) : getMaxPowerForMiner(miner);
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

                double maxPower = getMaxPowerForMiner(miner);
                double minPower = supportsScaling ? getMinPowerTargetForMiner(miner) : maxPower;

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

            for (MinerEntity<?> miner : getFreshMiners()) {
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
                    double minPower = supportsScaling ? getMinPowerTargetForMiner(miner) : getMaxPowerForMiner(miner);
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

                double maxPower = getMaxPowerForMiner(miner);
                double minPower = supportsScaling ? getMinPowerTargetForMiner(miner) : maxPower;
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

    private long getMinPowerTargetForMiner(MinerEntity<?> miner) {
        var stats = SpringContextHelper.getBean(EntityQueryService.class).getLastResult(miner, MinerStats.DEFAULT);
        return Math.max(miner.getMinPowerTarget(), stats.minPowerTarget());
    }

    private long getMaxPowerForMiner(MinerEntity<?> miner) {
        var stats = SpringContextHelper.getBean(EntityQueryService.class).getLastResult(miner, MinerStats.DEFAULT);
        return Math.min(miner.getMaxPowerTarget(), stats.maxPowerTarget());
    }

    private void handleMinerShutdownOrUndervolt(MinerEntity<?> miner, EntityQueryService queryService, Object controller, ControllerDSL.OperatingMode mode, double minPower, boolean isCurrentlyMining, boolean supportsScaling) {
        MinerLock currentLock = activeLocks.get(miner.getId());
        String minerName = miner.getName() != null ? miner.getName() : "Miner";

        if (isCurrentlyMining && isMinerStateLocked(miner)) {
            double targetWatts = supportsScaling ? minPower : getMaxPowerForMiner(miner);

            if (currentLock != null && currentLock.expectedPowerWatts() == targetWatts) return;

            ControllerDSL.ControllerActionType.SET_POWER_TARGET.apply((MinerEntityController) controller, String.valueOf((long) targetWatts));
            Instant existingStateUnlock = currentLock != null ? currentLock.stateUnlockTime() : Instant.now();
            activeLocks.put(miner.getId(), new MinerLock(existingStateUnlock, Instant.now().plus(Duration.ofMinutes(2)), Instant.now(), targetWatts));

            LOGGER.warning("Miner " + miner.getId() + " is in active state lock! Undervolted to " + targetWatts + "W.");
            appendTickEvent("⚠️ State-Lock undervolt (" + minerName + " -> " + targetWatts + "W)");
        } else {
            if (currentLock != null && currentLock.expectedPowerWatts() == 0.0) return;

            ControllerDSL.ControllerActionType.PAUSE.apply((MinerEntityController) controller, null);
            Instant stateUnlock = Instant.now().plus(getEffectiveMinIdleTime(miner, mode));
            activeLocks.put(miner.getId(), new MinerLock(stateUnlock, stateUnlock, Instant.now(), 0.0));
            appendTickEvent("⏸ Stop (" + minerName + " paused)");
        }
    }

    private void applyPowerAndRecordState(MinerEntity<?> miner, Object controller, ControllerDSL.ControllerAction action, double watts, ControllerDSL.OperatingMode mode, boolean wasAlreadyMining) {
        MinerLock currentLock = activeLocks.get(miner.getId());

        if (currentLock != null && currentLock.expectedPowerWatts() == watts) {
            return;
        }

        long min = miner.getMinPowerTarget();
        long max = miner.getMaxPowerTarget();

        action.controllerActionType().apply(
                (MinerEntityController) controller,
                String.valueOf((long) Math.clamp(watts, min, max))
        );

        Instant stateUnlock = wasAlreadyMining && currentLock != null
                ? currentLock.stateUnlockTime()
                : Instant.now().plus(getEffectiveMinRunTime(miner, mode));

        Instant powerUnlock = Instant.now().plus(Duration.ofMinutes(2));

        activeLocks.put(miner.getId(), new MinerLock(stateUnlock, powerUnlock, Instant.now(), watts));

        String actionName = (currentLock == null || currentLock.expectedPowerWatts() == 0) ? "▶ Start" : "⚡ Regelung";
        String minerName = miner.getName() != null ? miner.getName() : "Miner";
        appendTickEvent(actionName + " (" + minerName + " -> " + watts + "W)");
    }

    private void appendTickEvent(String msg) {
        if (this.tickEventMessage == null) {
            this.tickEventMessage = msg;
        } else if (!this.tickEventMessage.contains(msg)) {
            this.tickEventMessage += " | " + msg;
        }
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
        for (MinerEntity<?> miner : getFreshMiners()) {
            if (!isMinerStateLocked(miner)) {
                var controller = controllerService.getController(miner);
                ControllerDSL.ControllerActionType.PAUSE.apply((MinerEntityController) controller, null);
                Instant stateUnlock = Instant.now().plus(Duration.ofMinutes(5));
                activeLocks.put(miner.getId(), new MinerLock(stateUnlock, stateUnlock, Instant.now(), 0.0));
            }
        }
    }

    private double calculateTotalClusterCapacity(EntityQueryService queryService) {
        return getFreshMiners().stream()
                .mapToDouble(this::getMaxPowerForMiner)
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