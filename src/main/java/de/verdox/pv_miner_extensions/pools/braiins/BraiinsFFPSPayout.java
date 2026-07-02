package de.verdox.pv_miner_extensions.pools.braiins;

import de.verdox.pv_miner.miningpool.PoolPayoutEstimation;

public class BraiinsFFPSPayout implements PoolPayoutEstimation {

    @Override
    public double calculateRewardForDay(
            long networkDifficulty,
            int blockSubsidySat,
            int averageTxPriceSat,
            double sharesLast24HoursDiff1,
            float poolPercentageFee
    ) {
        double totalBlockRewardSat = blockSubsidySat + averageTxPriceSat;
        double rewardPerDiff1Share = totalBlockRewardSat / (double) networkDifficulty;

        double totalRewardSat = sharesLast24HoursDiff1 * rewardPerDiff1Share;
        return totalRewardSat * (1.0 - poolPercentageFee);
    }
}