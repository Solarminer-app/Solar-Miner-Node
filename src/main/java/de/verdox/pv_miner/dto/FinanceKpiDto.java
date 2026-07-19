package de.verdox.pv_miner.dto;

import java.time.LocalDate;

public record FinanceKpiDto(
        MoneyDto totalInvestment,
        MoneyDto realizedProfit,
        MoneyDto unrealizedValue,
        double allTimeMinedBtc,
        double allTimeSoldBtc,
        double unsoldBtc,
        double roiProgressPercent,
        MoneyDto totalOpex,
        MoneyDto totalHouseholdSavings,
        MoneyDto totalFeedInRevenue,
        LocalDate estimatedBreakEvenDate
) {}
