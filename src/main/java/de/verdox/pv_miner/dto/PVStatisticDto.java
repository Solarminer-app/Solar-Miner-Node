package de.verdox.pv_miner.dto;

import java.time.LocalDate;
import java.util.List;

public record PVStatisticDto(
        LocalDate date,
        double totalPvProduction,
        double minerConsumption,
        double miningPvUsage,
        double miningGridUsage,
        double miningBatteryUsage,
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
        MoneyDto feedInPricePerKwh,
        MoneyDto miningRevenueHistoric,
        MoneyDto miningRevenueLive,
        boolean miningRevenueComplete,
        List<CoinMiningDayDto> miningCoins,
        double gridImportKwh,
        MoneyDto gridPricePerKwh,
        String energyAllocationQuality
) {
    public PVStatisticDto(LocalDate date, double totalPvProduction, double minerConsumption, double miningPvUsage,
                          double miningGridUsage, double householdPvUsage, double exportedKwh, double minedBtc,
                          MoneyDto miningCost, MoneyDto miningGridCost, MoneyDto miningOpportunityCost,
                          MoneyDto effectiveYieldPerKwh, MoneyDto btcLiveValue, MoneyDto btcHistoricValue,
                          MoneyDto householdSavings, MoneyDto feedInRevenue, MoneyDto feedInPricePerKwh,
                          MoneyDto miningRevenueHistoric, MoneyDto miningRevenueLive, boolean miningRevenueComplete,
                          List<CoinMiningDayDto> miningCoins, double gridImportKwh, MoneyDto gridPricePerKwh) {
        this(date, totalPvProduction, minerConsumption, miningPvUsage, miningGridUsage, 0, householdPvUsage,
                exportedKwh, minedBtc, miningCost, miningGridCost, miningOpportunityCost, effectiveYieldPerKwh,
                btcLiveValue, btcHistoricValue, householdSavings, feedInRevenue, feedInPricePerKwh,
                miningRevenueHistoric, miningRevenueLive, miningRevenueComplete, miningCoins, gridImportKwh,
                gridPricePerKwh, "DAILY_ESTIMATE");
    }

    public PVStatisticDto(LocalDate date, double totalPvProduction, double minerConsumption, double miningPvUsage,
                          double miningGridUsage, double householdPvUsage, double exportedKwh, double minedBtc,
                          MoneyDto miningCost, MoneyDto miningGridCost, MoneyDto miningOpportunityCost,
                          MoneyDto effectiveYieldPerKwh, MoneyDto btcLiveValue, MoneyDto btcHistoricValue,
                          MoneyDto householdSavings, MoneyDto feedInRevenue, MoneyDto feedInPricePerKwh) {
        this(date, totalPvProduction, minerConsumption, miningPvUsage, miningGridUsage, 0, householdPvUsage, exportedKwh,
                minedBtc, miningCost, miningGridCost, miningOpportunityCost, effectiveYieldPerKwh, btcLiveValue,
                btcHistoricValue, householdSavings, feedInRevenue, feedInPricePerKwh, btcHistoricValue, btcLiveValue,
                true, List.of(new CoinMiningDayDto("bitcoin", "BTC", null, minedBtc, btcHistoricValue,
                        "POOL_DAILY", "AVAILABLE")), 0, zeroMoney(), "DAILY_ESTIMATE");
    }

    private static MoneyDto zeroMoney() {
        return new MoneyDto(0, "EUR");
    }
}
