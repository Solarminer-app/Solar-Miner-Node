package de.verdox.pv_miner.dto;

public record LiveEnergyDto(
        double pvPowerKw,
        double householdPowerKw,
        double minerPowerKw,
        double totalLoadKw,
        double gridImportKw,
        double gridExportKw,
        double batteryPowerKw,
        double batterySocPercent,
        double batteryCapacityKwh,
        Double estimatedBatteryRuntimeHours,
        String batteryState
) {}
