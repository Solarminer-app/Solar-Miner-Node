package de.verdox.pv_miner.miningpool;

public interface PoolPayoutEstimation {
    double calculateRewardForDay(long networkDifficulty, int blockSubsidy, int averageTxPrice, double sharesLast24Hours, float poolPercentageFee);
}
