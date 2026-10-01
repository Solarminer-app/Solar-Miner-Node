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
        LocalDate estimatedBreakEvenDate,
        MoneyDto otherMiningRevenue
) {
    public FinanceKpiDto(MoneyDto totalInvestment, MoneyDto realizedProfit, MoneyDto unrealizedValue,
                         double allTimeMinedBtc, double allTimeSoldBtc, double unsoldBtc, double roiProgressPercent,
                         MoneyDto totalOpex, MoneyDto totalHouseholdSavings, MoneyDto totalFeedInRevenue,
                         LocalDate estimatedBreakEvenDate) {
        this(totalInvestment, realizedProfit, unrealizedValue, allTimeMinedBtc, allTimeSoldBtc, unsoldBtc,
                roiProgressPercent, totalOpex, totalHouseholdSavings, totalFeedInRevenue, estimatedBreakEvenDate,
                MoneyDto.of(0, de.verdox.pv_miner.util.currency.CustomCurrency.getInstance(totalInvestment.currency())));
    }
}
