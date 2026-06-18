package de.verdox.pv_miner.finance;

import de.verdox.pv_miner.util.Money;

import java.time.LocalDate;

public record FinanceKpiDto(
        Money totalInvestment,
        Money realizedProfit,
        Money unrealizedValue,
        double allTimeMinedBtc,
        double allTimeSoldBtc,
        double unsoldBtc,
        double roiProgressPercent,
        Money totalOpex,
        Money totalHouseholdSavings,
        Money totalFeedInRevenue,
        LocalDate estimatedBreakEvenDate
) {}