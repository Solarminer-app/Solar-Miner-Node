package de.verdox.pv_miner_extensions.pools.braiins;

public class BrainsPoolDTOs {
    public record BraiinsProfileData(
            String username,
            double currentBalance,
            double todayReward,
            double allTimeReward,
            double estimatedReward,
            double hashRate24h,
            int okWorkers,
            int offWorkers
    ) {
    }
}
