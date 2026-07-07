package de.verdox.pv_miner.frontend.pvsite.dashboard.dto;

public record LiveKpiDto(
        String pvPower,
        String minerPower,
        String powerTotal,
        String liveImport,
        String liveExport,
        String batterySoc,
        String batteryPower,
        String totalHashrate,
        String activeMiners
) {}
