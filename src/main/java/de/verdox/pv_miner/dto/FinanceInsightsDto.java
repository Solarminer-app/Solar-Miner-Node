package de.verdox.pv_miner.dto;

import java.time.LocalDate;

public record FinanceInsightsDto(
        MoneyDto miningRevenueHistoric,
        MoneyDto miningRevenueLive,
        MoneyDto miningEnergyCost,
        MoneyDto miningGridCost,
        MoneyDto miningOpportunityCost,
        MoneyDto miningNetHistoric,
        MoneyDto miningNetLive,
        MoneyDto householdSavings,
        MoneyDto feedInRevenue,
        MoneyDto totalValueCreated,
        MoneyDto operatingResult,
        MoneyDto averageDailyOperatingResult,
        MoneyDto costPerMinedBtc,
        MoneyDto breakEvenBtcPrice,
        MoneyDto totalCapitalValue,
        MoneyDto totalCapitalCost,
        MoneyDto netPosition,
        MoneyDto remainingToBreakEven,
        MoneyDto averageDailyCapitalValue,
        double minedBtc,
        double miningEnergyKwh,
        double gridMiningSharePercent,
        int profitableMiningDays,
        int daysWithData,
        LocalDate bestDay,
        MoneyDto bestDayResult,
        LocalDate worstDay,
        MoneyDto worstDayResult
) {}
