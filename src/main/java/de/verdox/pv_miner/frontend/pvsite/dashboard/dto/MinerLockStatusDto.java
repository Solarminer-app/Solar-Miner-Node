package de.verdox.pv_miner.frontend.pvsite.dashboard.dto;

public record MinerLockStatusDto(
        String minerName,
        String ipAddress,
        long stateLockRemainingSeconds,
        long powerLockRemainingSeconds,
        double expectedPowerWatts
) {}