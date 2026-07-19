package de.verdox.pv_miner.dto;

import java.time.LocalDate;

public record PVStatisticDto(
        LocalDate date,
        double totalPvProduction,
        double minerConsumption,
        double miningPvUsage,
        double miningGridUsage,
        double householdPvUsage,
        double exportedKwh,
        double minedBtc,
        MoneyDto miningCost,
        MoneyDto miningGridCost,
        MoneyDto miningOpportunityCost,
        MoneyDto effectiveYieldPerKwh,
        MoneyDto btcLiveValue,
        MoneyDto btcHistoricValue,
        MoneyDto householdSavings,
        MoneyDto feedInRevenue,
        MoneyDto feedInPricePerKwh
) {}
