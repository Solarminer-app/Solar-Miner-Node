package de.verdox.pv_miner.miningcontroller;

import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.entity.EntityControllerService;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.entity.EntityService;
import de.verdox.pv_miner.miner.MinerEntityController;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.miningcontroller.dsl.ControllerDSL;
import de.verdox.pv_miner.pvsite.PVSiteRef;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClusterController {
    private static final Logger LOGGER = Logger.getLogger(ClusterController.class.getSimpleName());

    private final String clusterName;
    private final EntityService entityService;
    private final List<UUID> minerCluster;
    private final PVSiteRef pvSiteReference;
    private volatile MinerControllerConfig config;
    private final Consumer<MinerClusterService.ClusterInstance.ClusterStateSnapshot> stateLogger;
    private String tickEventMessage = null;

    private final Map<UUID, MinerLock> activeLocks = new ConcurrentHashMap<>();

    private final InfluxValueProvider influxValueProvider = new InfluxValueProvider();
    private final ControllerDecisionEngine decisionEngine = new ControllerDecisionEngine();

    public ClusterController(String clusterName, EntityService entityService, List<UUID> minerCluster, PVSiteRef pvSiteReference, MinerControllerConfig config, Consumer<MinerClusterService.ClusterInstance.ClusterStateSnapshot> stateLogger) {
        this.clusterName = clusterName;
        this.entityService = entityService;
        this.minerCluster = minerCluster;
        this.pvSiteReference = pvSiteReference;
        this.config = config;
        this.stateLogger = stateLogger;
    }

    public Map<UUID, MinerLock> getActiveLocks() {
        return java.util.Collections.unmodifiableMap(this.activeLocks);
    }

    public synchronized void updateConfig(MinerControllerConfig config) {
        this.config = Objects.requireNonNull(config);
        this.decisionEngine.reset();
    }

    public synchronized void evaluate() {
        if (minerCluster.isEmpty()) return;
        this.tickEventMessage = null;
        var pvSite = pvSiteReference.read();

        EntityControllerService controllerService = SpringContextHelper.getBean(EntityControllerService.class);
        EntityQueryService queryService = SpringContextHelper.getBean(EntityQueryService.class);

        try {
            double totalClusterCapacity = calculateTotalClusterCapacity(queryService);
            ControllerDecisionEngine.Decision decision = decisionEngine.evaluate(
                    config,
                    influxValueProvider,
                    pvSite,
                    totalClusterCapacity
            );
            ControllerDSL.OperatingMode activeMode = decision.activeMode();

            if (decision.modeChanged()) {
                LOGGER.info("Cluster " + clusterName + " State Transition: " +
                        (decision.previousMode() != null ? decision.previousMode().modeName() : "NONE") + " -> " +
                        (activeMode != null ? activeMode.modeName() : "NONE"));

                if (activeMode == null) {
                    pauseAllUnlockedMiners(controllerService, queryService);

                    double allocated = activeLocks.values().stream().mapToDouble(MinerLock::expectedPowerWatts).sum();
                    stateLogger.accept(new MinerClusterService.ClusterInstance.ClusterStateSnapshot(Instant.now(), 0.0, allocated, "Idle", tickEventMessage));
                    return;
                }
            }

            if (activeMode != null) {
                for (ControllerDSL.ControllerAction action : decision.actions()) {
                    if (action.controllerActionType() != ControllerDSL.ControllerActionType.SET_POWER_TARGET) {
                        executeDynamicDistribution(controllerService, queryService, action, 0.0, activeMode);
                    }
                }

                ControllerDSL.ControllerAction powerAction = decision.powerAction();
                if (powerAction != null) {
                    executeDynamicDistribution(controllerService, queryService, powerAction, decision.targetPowerWatts(), activeMode);
                }
            }

            double totalAllocatedPower = activeLocks.values().stream()
                    .mapToDouble(MinerLock::expectedPowerWatts)
                    .sum();

            stateLogger.accept(new MinerClusterService.ClusterInstance.ClusterStateSnapshot(
                    Instant.now(),
                    decision.targetPowerWatts(),
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
            handleNonPowerActions(controllerService, queryService, action, mode);
            return;
        }

        distributePower(controllerService, queryService, action, targetPowerWatts, mode);
    }

    private void distributePower(EntityControllerService controllerService, EntityQueryService queryService, ControllerDSL.ControllerAction action, double targetPowerWatts, ControllerDSL.OperatingMode mode) {
        double remainingPower = targetPowerWatts;
        List<MinerEntity<?>> adjustableMiners = new ArrayList<>();

        for (MinerEntity<?> miner : getFreshMiners()) {
            if (isMinerPowerLocked(miner)) {
                MinerLock currentLock = activeLocks.get(miner.getId());
                remainingPower -= (currentLock != null) ? currentLock.expectedPowerWatts() : 0.0;
            } else {
                adjustableMiners.add(miner);
            }
        }

        if (remainingPower <= 0) {
            shutdownOrUndervoltCluster(controllerService, mode, adjustableMiners);
            return;
        }

        if (action.strategy() == ControllerDSL.MinerDistributionStrategy.EFFICIENCY_FIRST) {
            adjustableMiners.sort(Comparator.comparingDouble(miner -> {
                var stats = queryService.getLastResult(miner, MinerStats.DEFAULT);
                if (stats == null || stats.terahashPerSecond() <= 0) return Double.MAX_VALUE;
                return stats.approximatedPowerUsageWatts() / stats.terahashPerSecond();
            }));

            for (MinerEntity<?> miner : adjustableMiners) {
                var controller = controllerService.getController(miner);
                MinerLock currentLock = activeLocks.get(miner.getId());
                boolean supportsScaling = miner.getOS().supportsDynamicPowerScaling();
                boolean isCurrentlyMining = currentLock != null && currentLock.expectedPowerWatts() > 0;

                double maxPower = ClusterUtil.getMaxPowerForMiner(miner);
                double minPower = supportsScaling ? ClusterUtil.getMinPowerTargetForMiner(miner) : maxPower;
                int stepSize = ClusterUtil.getEffectiveStepSize(miner, action, supportsScaling);

                double allocation = Math.min(remainingPower, maxPower);
                double steppedAllocation = ClusterUtil.roundToStepDown(allocation, stepSize);

                if (steppedAllocation >= minPower && remainingPower >= minPower) {
                    applyPowerAndRecordState(miner, controller, action, steppedAllocation, mode, isCurrentlyMining);
                    remainingPower -= steppedAllocation;
                } else {
                    handleMinerShutdownOrUndervolt(miner, controller, mode, minPower, isCurrentlyMining, supportsScaling);
                }
            }
        }

        else if (action.strategy() == ControllerDSL.MinerDistributionStrategy.EQUAL_DISTRIBUTION) {
            adjustableMiners.sort(Comparator.comparing(miner -> {
                MinerLock lock = activeLocks.get(miner.getId());
                return lock != null && lock.expectedPowerWatts() > 0 ? 0 : 1;
            }));

            List<MinerEntity<?>> minersToActivate = new ArrayList<>();
            Map<UUID, Double> finalAllocations = new HashMap<>();

            for (MinerEntity<?> miner : adjustableMiners) {
                finalAllocations.put(miner.getId(), 0.0);
            }

            double powerForMinima = remainingPower;
            for (MinerEntity<?> miner : adjustableMiners) {
                boolean supportsScaling = miner.getOS().supportsDynamicPowerScaling();
                double minPower = supportsScaling ? ClusterUtil.getMinPowerTargetForMiner(miner) : ClusterUtil.getMaxPowerForMiner(miner);

                if (powerForMinima >= minPower) {
                    minersToActivate.add(miner);
                    finalAllocations.put(miner.getId(), minPower);
                    powerForMinima -= minPower;
                }
            }

            if (minersToActivate.isEmpty()) {
                shutdownOrUndervoltCluster(controllerService, mode, adjustableMiners);
                return;
            }

            remainingPower = powerForMinima;

            boolean progress = true;
            while (remainingPower > 0 && progress) {
                progress = false;

                List<MinerEntity<?>> ungesaetigteMiner = new ArrayList<>();
                for (MinerEntity<?> miner : minersToActivate) {
                    double maxPower = ClusterUtil.getMaxPowerForMiner(miner);
                    if (finalAllocations.get(miner.getId()) < maxPower) {
                        ungesaetigteMiner.add(miner);
                    }
                }

                if (ungesaetigteMiner.isEmpty()) break;

                double share = remainingPower / ungesaetigteMiner.size();

                for (MinerEntity<?> miner : ungesaetigteMiner) {
                    double maxPower = ClusterUtil.getMaxPowerForMiner(miner);
                    double currentAlloc = finalAllocations.get(miner.getId());
                    int stepSize = ClusterUtil.getEffectiveStepSize(miner, action, miner.getOS().supportsDynamicPowerScaling());

                    double maximalMoeglicherZuwachs = maxPower - currentAlloc;
                    double gewuenschterZuwachs = Math.min(share, maximalMoeglicherZuwachs);

                    // Stufenregelung (StepSize) einhalten
                    double neuesTarget = currentAlloc + gewuenschterZuwachs;
                    double gestuftesTarget = ClusterUtil.roundToStepDown(neuesTarget, stepSize);

                    if (gestuftesTarget > currentAlloc) {
                        double tatsaechlicherZuwachs = gestuftesTarget - currentAlloc;
                        finalAllocations.put(miner.getId(), gestuftesTarget);
                        remainingPower -= tatsaechlicherZuwachs;
                        progress = true;
                    }
                }
            }

            for (MinerEntity<?> miner : adjustableMiners) {
                var controller = controllerService.getController(miner);
                double allocation = finalAllocations.get(miner.getId());
                MinerLock currentLock = activeLocks.get(miner.getId());
                boolean isCurrentlyMining = currentLock != null && currentLock.expectedPowerWatts() > 0;
                boolean supportsScaling = miner.getOS().supportsDynamicPowerScaling();
                double minPower = supportsScaling ? ClusterUtil.getMinPowerTargetForMiner(miner) : ClusterUtil.getMaxPowerForMiner(miner);

                if (allocation >= minPower) {
                    applyPowerAndRecordState(miner, controller, action, allocation, mode, isCurrentlyMining);
                } else {
                    handleMinerShutdownOrUndervolt(miner, controller, mode, minPower, isCurrentlyMining, supportsScaling);
                }
            }
        }
    }

    private void shutdownOrUndervoltCluster(EntityControllerService controllerService, ControllerDSL.OperatingMode mode, List<MinerEntity<?>> adjustableMiners) {
        for (MinerEntity<?> miner : adjustableMiners) {
            var controller = controllerService.getController(miner);
            MinerLock lock = activeLocks.get(miner.getId());
            boolean isMining = lock != null && lock.expectedPowerWatts() > 0;
            boolean supportsScaling = miner.getOS().supportsDynamicPowerScaling();
            double minPower = supportsScaling ? ClusterUtil.getMinPowerTargetForMiner(miner) : ClusterUtil.getMaxPowerForMiner(miner);
            handleMinerShutdownOrUndervolt(miner, controller, mode, minPower, isMining, supportsScaling);
        }
    }

    private void handleNonPowerActions(EntityControllerService controllerService, EntityQueryService queryService, ControllerDSL.ControllerAction action, ControllerDSL.OperatingMode mode) {
        for (MinerEntity<?> miner : getFreshMiners()) {
            boolean isEmergencyPause = action.controllerActionType() == ControllerDSL.ControllerActionType.PAUSE;

            if(!isEmergencyPause && isMinerStateLocked(miner)) {
                continue;
            }

            var controller = controllerService.getController(miner);
            action.controllerActionType().apply(controller, null);

            boolean supportsScaling = miner.getOS().supportsDynamicPowerScaling();
            double expectedPower = isEmergencyPause ? 0.0 : (supportsScaling ? ClusterUtil.getMinPowerTargetForMiner(miner) : ClusterUtil.getMaxPowerForMiner(miner));

            Instant stateUnlock = Instant.now().plus(isEmergencyPause ? ClusterUtil.getEffectiveMinIdleTime(miner, mode) : ClusterUtil.getEffectiveMinRunTime(miner, mode));
            Instant powerUnlock = Instant.now();
            activeLocks.put(miner.getId(), new MinerLock(stateUnlock, powerUnlock, Instant.now(), expectedPower));
            String actionName = isEmergencyPause ? "⏸ Stop" : "▶ Start";
            String minerName = miner.getName() != null ? miner.getName() : "Miner";
            appendTickEvent(actionName + " (" + minerName + ")");
        }
    }

    private void handleMinerShutdownOrUndervolt(MinerEntity<?> miner, MinerEntityController controller, ControllerDSL.OperatingMode mode, double minPower, boolean isCurrentlyMining, boolean supportsScaling) {
        MinerLock currentLock = activeLocks.get(miner.getId());
        String minerName = miner.getName() != null ? miner.getName() : "Miner";

        if (isCurrentlyMining && isMinerStateLocked(miner)) {
            double targetWatts = supportsScaling ? minPower : ClusterUtil.getMaxPowerForMiner(miner);

            if (currentLock != null && Math.abs(currentLock.expectedPowerWatts() - targetWatts) < 0.1) return;

            ControllerDSL.ControllerActionType.SET_POWER_TARGET.apply(controller, String.valueOf((long) targetWatts));
            Instant existingStateUnlock = currentLock != null ? currentLock.runStateUnlockTime() : Instant.now();

            Duration lockTime = mode.powerChangeLockTime() != null ? mode.powerChangeLockTime() : Duration.ofMinutes(8);
            activeLocks.put(miner.getId(), new MinerLock(existingStateUnlock, Instant.now().plus(lockTime), Instant.now(), targetWatts));

            if (supportsScaling) {
                LOGGER.warning("Miner " + miner.getId() + " is in active state lock! Undervolted to " + targetWatts + "W.");
                appendTickEvent("⚠️ State-Lock Undervolt (" + minerName + " -> " + targetWatts + "W)");
            } else {
                LOGGER.warning("Miner " + miner.getId() + " is in active state lock but cannot scale! Forced to MAX power: " + targetWatts + "W.");
                appendTickEvent("⚠️ State-Lock Max-Power (" + minerName + " -> " + targetWatts + "W)");
            }
        } else {
            if (currentLock != null && currentLock.expectedPowerWatts() == 0.0) return;

            ControllerDSL.ControllerActionType.PAUSE.apply(controller, null);
            Instant stateUnlock = Instant.now().plus(ClusterUtil.getEffectiveMinIdleTime(miner, mode));
            activeLocks.put(miner.getId(), new MinerLock(stateUnlock, stateUnlock, Instant.now(), 0.0));
            appendTickEvent("⏸ Stop (" + minerName + " paused)");
        }
    }

    private void applyPowerAndRecordState(MinerEntity<?> miner, Object controller, ControllerDSL.ControllerAction action, double watts, ControllerDSL.OperatingMode mode, boolean wasAlreadyMining) {
        MinerLock currentLock = activeLocks.get(miner.getId());

        long min = miner.getMinPowerTarget();
        long max = miner.getMaxPowerTarget();
        long targetWattsLong = (long) Math.max(min, Math.min(max, watts));

        boolean powerChanged = currentLock == null || Math.abs(currentLock.expectedPowerWatts() - targetWattsLong) > 0.1;
        boolean forcePush = !isMinerPowerLocked(miner);

        if (powerChanged || forcePush) {
            action.controllerActionType().apply(
                    (MinerEntityController) controller,
                    String.valueOf(targetWattsLong)
            );
        }

        Instant stateUnlock = wasAlreadyMining && currentLock != null
                ? currentLock.runStateUnlockTime()
                : Instant.now().plus(ClusterUtil.getEffectiveMinRunTime(miner, mode));

        Duration powerLockDuration = mode.powerChangeLockTime() != null ? mode.powerChangeLockTime() : Duration.ofMinutes(8);
        Instant powerUnlock = Instant.now().plus(powerLockDuration);

        activeLocks.put(miner.getId(), new MinerLock(stateUnlock, powerUnlock, Instant.now(), targetWattsLong));

        if (powerChanged) {
            String actionName = (currentLock == null || currentLock.expectedPowerWatts() == 0) ? "▶ Start" : "⚡ Regelung";
            String minerName = miner.getName() != null ? miner.getName() : "Miner";
            appendTickEvent(actionName + " (" + minerName + " -> " + targetWattsLong + "W)");
        }
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
        return !Instant.now().isAfter(lock.runStateUnlockTime());
    }

    private boolean isMinerPowerLocked(MinerEntity<?> miner) {
        MinerLock lock = activeLocks.get(miner.getId());
        if (lock == null) return false;
        return !Instant.now().isAfter(lock.powerChangeUnlockTime());
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
                .mapToDouble(ClusterUtil::getMaxPowerForMiner)
                .sum();
    }
}
