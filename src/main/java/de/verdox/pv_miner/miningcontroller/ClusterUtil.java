package de.verdox.pv_miner.miningcontroller;

import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.miningcontroller.dsl.ControllerDSL;

import java.time.Duration;

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
        return Math.max(miner.getMinPowerTarget(), stats.minPowerTarget());
    }

    public static long getMaxPowerForMiner(MinerEntity<?> miner) {
        var stats = SpringContextHelper.getBean(EntityQueryService.class).getLastResult(miner, MinerStats.DEFAULT);
        return Math.min(miner.getMaxPowerTarget(), stats.maxPowerTarget());
    }
}
