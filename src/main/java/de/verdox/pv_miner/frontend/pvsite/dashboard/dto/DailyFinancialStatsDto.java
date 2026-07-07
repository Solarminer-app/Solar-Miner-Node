package de.verdox.pv_miner.frontend.pvsite.dashboard.dto;

public record DailyFinancialStatsDto(
        String exportedToday,
        String revenueExportToday,
        String importToday,
        String costImportToday,
        String loadHomeTotalToday,
        String avoidedEnergyCost,
        String loadMinerTotalToday,
        String minerNotExported
) {}
