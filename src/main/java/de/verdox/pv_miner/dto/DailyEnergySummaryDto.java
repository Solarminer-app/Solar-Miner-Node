package de.verdox.pv_miner.dto;

import java.util.List;

public record DailyEnergySummaryDto(
        double productionKwh,
        double consumptionKwh,
        double householdConsumptionKwh,
        double miningConsumptionKwh,
        double gridImportKwh,
        double gridExportKwh,
        double selfConsumedKwh,
        double selfConsumptionPercent,
        double autarkyPercent,
        double miningLocalEnergyKwh,
        double miningGridEnergyKwh,
        double exportRevenue,
        double importCost,
        double householdSavings,
        double miningOpportunityCost,
        long minedSats,
        Double miningRevenue,
        Double miningNetResult,
        String currencySymbol,
        Double miningRevenueEuro,
        List<MiningRevenueByCoinDto> miningRevenueByCoin
) {}
