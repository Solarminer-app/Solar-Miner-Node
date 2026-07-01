package de.verdox.pv_miner.finance.dto;

import de.verdox.pv_miner.util.Money;

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
        Money miningCost,
        Money effectiveYieldPerKwh,
        Money btcLiveValue,
        Money btcHistoricValue,
        Money householdSavings,
        Money feedInRevenue,
        Money feedInPricePerKwh
) {}
