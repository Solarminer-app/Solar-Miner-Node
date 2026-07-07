package de.verdox.pv_miner.frontend.pvsite.dashboard.dto;

import java.util.List;

public record LiveDashboardUpdateDto(
        LiveKpiDto kpi,
        List<MinerLockStatusDto> lockStatusDtos,
        DailyFinancialStatsDto financials,
        String walletBalanceFormatted
) {}
