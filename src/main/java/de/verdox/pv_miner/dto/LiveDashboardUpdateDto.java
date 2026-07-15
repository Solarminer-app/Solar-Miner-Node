package de.verdox.pv_miner.dto;

import java.util.List;

public record LiveDashboardUpdateDto(
        LiveKpiDto kpi,
        List<MinerLockStatusDto> lockStatusDtos,
        DailyFinancialStatsDto financials,
        String walletBalanceFormatted,
        LiveEnergyDto energy,
        DailyEnergySummaryDto day,
        MiningOverviewDto mining,
        DataQualityDto dataQuality
) {}
