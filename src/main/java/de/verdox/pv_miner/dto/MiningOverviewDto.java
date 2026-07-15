package de.verdox.pv_miner.dto;

public record MiningOverviewDto(
        double totalHashrateThs,
        double actualPowerWatts,
        double targetPowerWatts,
        double efficiencyJPerTh,
        int activeMiners,
        int totalMiners,
        double estimatedPvPowerWatts,
        double estimatedBatteryPowerWatts,
        double estimatedGridPowerWatts,
        String clusterName,
        boolean controllerRunning,
        String controllerMode,
        String lastControllerAction
) {}
