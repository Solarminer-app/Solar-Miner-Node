package de.verdox.pv_miner.dto;

public record MinerDashboardItemDTO(
        String name,
        String ip,
        String status,
        String hashrate,
        String power,
        String temp,
        String pool,
        long stateLockRemainingSeconds,
        long powerLockRemainingSeconds,
        long controllerPowerTarget
) {}
