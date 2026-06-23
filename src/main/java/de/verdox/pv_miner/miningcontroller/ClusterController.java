package de.verdox.pv_miner.miningcontroller;

import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.entity.EntityControllerService;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.miner.Miner;
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

    private record MinerLock(Instant unlockTime, Instant lockStartTime, double expectedPowerWatts) {}
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

                if (isEmergencyPause || !isMinerLocked(miner, queryService)) {
                    var controller = controllerService.getController(miner);

                    action.controllerActionType().apply(controller, null);

                    double expectedPower = isEmergencyPause
                            ? 0.0
                            : queryService.getLastResult(miner, MinerStats.DEFAULT).minPowerTarget();

                    activeLocks.put(miner.getId(), new MinerLock(Instant.now().plus(mode.minRunTime()), Instant.now(), expectedPower));
                }
            }
            return;
        }

        if (action.strategy() == ControllerDSL.MinerDistributionStrategy.SEQUENTIAL) {
            double remainingPower = targetPowerWatts;

            for (MinerEntity<?> miner : minerCluster) {
                var stats = queryService.getLastResult(miner, MinerStats.DEFAULT);
                var controller = controllerService.getController(miner);

                if (isMinerLocked(miner, queryService)) {
                    remainingPower -= stats.approximatedPowerUsageWatts();
                    continue;
                }

                double maxPower = stats.maxPowerTarget();
                double minPower = stats.minPowerTarget();
                int stepSize = action.stepSizeWatts() > 0 ? action.stepSizeWatts() : 250;

                if (remainingPower >= maxPower) {
                    double steppedMax = roundToStepDown(maxPower, stepSize);

                    if (steppedMax >= minPower) {
                        applyPowerAndRecordState(miner, controller, action, String.valueOf((long) steppedMax), mode);
                        remainingPower -= steppedMax;
                    } else {
                        pauseMiner(miner, controller, mode);
                    }
                } else {
                    double allocation = roundToStepDown(remainingPower, stepSize);

                    if (allocation >= minPower) {
                        applyPowerAndRecordState(miner, controller, action, String.valueOf((long) allocation), mode);
                        remainingPower -= allocation;
                    } else {
                        pauseMiner(miner, controller, mode);
                    }
                }
            }
        }
        else if (action.strategy() == ControllerDSL.MinerDistributionStrategy.EQUAL_DISTRIBUTION) {
            double remainingPower = targetPowerWatts;
            List<MinerEntity<?>> unlockedMiners = new ArrayList<>();

            for (MinerEntity<?> miner : minerCluster) {
                if (isMinerLocked(miner, queryService)) {
                    var stats = queryService.getLastResult(miner, MinerStats.DEFAULT);
                    remainingPower -= stats.approximatedPowerUsageWatts();
                } else {
                    unlockedMiners.add(miner);
                }
            }

            if (remainingPower <= 0) {
                for (MinerEntity<?> miner : unlockedMiners) {
                    var controller = controllerService.getController(miner);
                    pauseMiner(miner, controller, mode);
                }
                return;
            }

            unlockedMiners.sort(java.util.Comparator.comparingDouble(m ->
                    queryService.getLastResult(m, MinerStats.DEFAULT).maxPowerTarget()
            ));

            int minersLeftToAllocate = unlockedMiners.size();

            for (MinerEntity<?> miner : unlockedMiners) {
                var stats = queryService.getLastResult(miner, MinerStats.DEFAULT);
                var controller = controllerService.getController(miner);

                double maxPower = stats.maxPowerTarget();
                double minPower = stats.minPowerTarget();

                double fairShare = remainingPower / minersLeftToAllocate;
                double allocation = Math.min(fairShare, maxPower);

                int stepSize = action.stepSizeWatts() > 0 ? action.stepSizeWatts() : 1;
                double steppedAllocation = roundToStepDown(allocation, stepSize);

                if (steppedAllocation >= minPower) {
                    applyPowerAndRecordState(miner, controller, action, String.valueOf((long) steppedAllocation), mode);
                    remainingPower -= steppedAllocation;
                } else {
                    pauseMiner(miner, controller, mode);
                }

                minersLeftToAllocate--;
            }
        }
    } private void applyPowerAndRecordState(MinerEntity<?> miner, Object controller, ControllerDSL.ControllerAction action, String valueStr, ControllerDSL.OperatingMode mode) {
        action.controllerActionType().apply((Miner) controller, valueStr);
        double expectedPower = Double.parseDouble(valueStr);
        activeLocks.put(miner.getId(), new MinerLock(Instant.now().plus(mode.minRunTime()), Instant.now(), expectedPower));
    }

    private void pauseMiner(MinerEntity<?> miner, Object controller, ControllerDSL.OperatingMode mode) {
        ControllerDSL.ControllerActionType.PAUSE.apply((Miner) controller, null);
        activeLocks.put(miner.getId(), new MinerLock(Instant.now().plus(mode.minIdleTime()), Instant.now(), 0.0));
    }

    private boolean isMinerLocked(MinerEntity<?> miner, EntityQueryService queryService) {
        MinerLock lock = activeLocks.get(miner.getId());

        if (lock == null || Instant.now().isAfter(lock.unlockTime())) {
            if (lock != null) activeLocks.remove(miner.getId());
            return false;
        }

        if (Duration.between(lock.lockStartTime(), Instant.now()).toMinutes() < 3) {
            return true;
        }

        var stats = queryService.getLastResult(miner, MinerStats.DEFAULT);
        double actualPower = stats.approximatedPowerUsageWatts();

        boolean expectedToMine = lock.expectedPowerWatts() > 0;
        boolean actuallyMining = actualPower > 50.0;

        if (expectedToMine && !actuallyMining) {
            activeLocks.remove(miner.getId());
            return false;
        }

        if (!expectedToMine && actuallyMining) {
            activeLocks.remove(miner.getId());
            return false;
        }

        return true;
    }

    private void pauseAllUnlockedMiners(EntityControllerService controllerService, EntityQueryService queryService) {
        for (MinerEntity<?> miner : minerCluster) {
            if (!isMinerLocked(miner, queryService)) {
                var controller = controllerService.getController(miner);
                ControllerDSL.ControllerActionType.PAUSE.apply((Miner) controller, null);
                activeLocks.put(miner.getId(), new MinerLock(Instant.now().plus(Duration.ofMinutes(5)), Instant.now(), 0.0));
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
}