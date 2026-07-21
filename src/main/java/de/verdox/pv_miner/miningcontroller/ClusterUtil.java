package de.verdox.pv_miner.miningcontroller;

import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.miningcontroller.dsl.ControllerDSL;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ClusterUtil {
    public static double roundToStepDown(double value, int stepSize) {
        if (stepSize <= 1) return value;
        return Math.floor(value / stepSize) * stepSize;
    }

    public static Duration getEffectiveMinRunTime(MinerEntity<?> miner, ControllerDSL.OperatingMode mode) {
        if (miner.getMinRunTimeMinutes() != null && miner.getMinRunTimeMinutes() > 0) {
            return Duration.ofMinutes(miner.getMinRunTimeMinutes());
        }
        return mode.minRunTime();
    }

    public static Duration getEffectiveMinIdleTime(MinerEntity<?> miner, ControllerDSL.OperatingMode mode) {
        if (miner.getMinIdleTimeMinutes() != null && miner.getMinIdleTimeMinutes() > 0) {
            return Duration.ofMinutes(miner.getMinIdleTimeMinutes());
        }
        return mode.minIdleTime();
    }

    public static int getEffectiveStepSize(MinerEntity<?> miner, ControllerDSL.ControllerAction action, boolean supportsScaling) {
        if (!supportsScaling) return 1;

        if (miner.getPowerStepSizeWatts() != null && miner.getPowerStepSizeWatts() > 0) {
            return miner.getPowerStepSizeWatts();
        }
        return action.stepSizeWatts() > 0 ? action.stepSizeWatts() : 250;
    }

    public static long getMinPowerTargetForMiner(MinerEntity<?> miner) {
        var stats = SpringContextHelper.getBean(EntityQueryService.class).getLastResult(miner, MinerStats.DEFAULT);
        if (stats == null) {
            return miner.getMinPowerTarget();
        }
        return Math.max(miner.getMinPowerTarget(), stats.minPowerTarget());
    }

    public static long getMaxPowerForMiner(MinerEntity<?> miner) {
        var stats = SpringContextHelper.getBean(EntityQueryService.class).getLastResult(miner, MinerStats.DEFAULT);
        if (stats == null) {
            return miner.getMaxPowerTarget();
        }
        return Math.min(miner.getMaxPowerTarget(), stats.maxPowerTarget());
    }

    /**
     * Plans a strict priority cascade. A lower-priority miner receives power only after every
     * higher-priority miner has reached its maximum. A power-locked miner below its maximum
     * therefore blocks new lower-priority allocations until it can be increased.
     */
    public static Map<UUID, Double> allocateEfficiencyFirst(List<EfficiencyCandidate> candidates,
                                                             double targetPowerWatts) {
        List<EfficiencyCandidate> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparingDouble(EfficiencyCandidate::efficiencyWattsPerTerahash)
                .thenComparing(candidate -> candidate.minerId().toString()));

        double lockedPower = ordered.stream()
                .filter(EfficiencyCandidate::powerLocked)
                .mapToDouble(candidate -> Math.max(0, candidate.currentPowerWatts()))
                .sum();
        double remainingPower = Math.max(0, targetPowerWatts - lockedPower);
        boolean priorityBlocked = false;
        Map<UUID, Double> allocations = new LinkedHashMap<>();

        for (EfficiencyCandidate candidate : ordered) {
            double reachableMaximum = Math.max(
                    candidate.minPowerWatts(),
                    roundToStepDown(candidate.maxPowerWatts(), candidate.stepSizeWatts())
            );
            if (candidate.powerLocked()) {
                double current = Math.max(0, candidate.currentPowerWatts());
                allocations.put(candidate.minerId(), current);
                if (remainingPower > 0 && current + 0.1 < reachableMaximum) {
                    priorityBlocked = true;
                }
                continue;
            }

            if (priorityBlocked || remainingPower <= 0) {
                allocations.put(candidate.minerId(), 0.0);
                continue;
            }

            double requested = Math.min(remainingPower, reachableMaximum);
            double allocation = roundToStepDown(requested, candidate.stepSizeWatts());
            if (allocation + 0.1 < candidate.minPowerWatts()) {
                allocations.put(candidate.minerId(), 0.0);
                priorityBlocked = true;
                continue;
            }

            allocations.put(candidate.minerId(), allocation);
            remainingPower = Math.max(0, remainingPower - allocation);
            if (allocation + 0.1 < reachableMaximum) {
                priorityBlocked = true;
            }
        }
        return allocations;
    }

    public record EfficiencyCandidate(
            UUID minerId,
            double efficiencyWattsPerTerahash,
            double minPowerWatts,
            double maxPowerWatts,
            int stepSizeWatts,
            double currentPowerWatts,
            boolean powerLocked
    ) {
    }
}
