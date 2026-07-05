package de.verdox.pv_miner.finance;

import de.verdox.pv_miner.finance.dto.FinanceKpiDto;
import de.verdox.pv_miner.finance.dto.PVStatisticDto;
import de.verdox.pv_miner.statistic.daily.DailyStatisticService;
import de.verdox.pv_miner.globalconstants.GlobalConstantsService;
import de.verdox.pv_miner.miningpool.MiningPoolEntity;
import de.verdox.pv_miner.miningpool.MiningPoolStatisticsAccumulator;
import de.verdox.pv_miner.miningpool.MiningPoolStatisticsPerDay;
import de.verdox.pv_miner.pvsite.*;
import de.verdox.pv_miner.util.Money;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class PVFinanceService {

    private final DailyStatisticService dailyStatisticService;
    private final GlobalConstantsService globalConstantsService;

    private final Map<UUID, HistoricalBtcCache> allTimeHistoricalBtcCache = new ConcurrentHashMap<>();
    private final Map<UUID, Object> entityLocks = new ConcurrentHashMap<>();

    private record HistoricalBtcCache(LocalDate cachedDate, double historicalBtcAmount) {
    }

    public PVFinanceService(DailyStatisticService dailyStatisticService, GlobalConstantsService globalConstantsService) {
        this.dailyStatisticService = dailyStatisticService;
        this.globalConstantsService = globalConstantsService;
    }

    public List<PVStatisticDto> getFinanceData(PVSiteEntity pvSiteEntity, LocalDate filterDateFrom, LocalDate filterDateTo, ZoneId zoneId, CustomCurrency targetCurrency) {
        Object entityLock = entityLocks.computeIfAbsent(pvSiteEntity.getId(), k -> new Object());

        synchronized (entityLock) {
            Instant from = filterDateFrom.atStartOfDay(zoneId).toInstant();
            Instant to = filterDateTo.plusDays(1).atStartOfDay(zoneId).toInstant().minusMillis(1);

            PVStatisticsAccumulator pvAccumulator = new PVStatisticsAccumulator();

            List<PVStatisticPerDay> allPvStats = new ArrayList<>(dailyStatisticService.getHistoricalStatistics(pvSiteEntity, "PV_DAILY", from, to, pvAccumulator));

            if (!filterDateTo.isBefore(LocalDate.now(zoneId))) {
                allPvStats.add(dailyStatisticService.getLiveDailyStatistic(pvSiteEntity, "PV_DAILY", pvAccumulator));
            }

            Map<LocalDate, PVStatisticPerDay> pvStatsByDate = allPvStats.stream().collect(Collectors.toMap(
                    s -> Instant.ofEpochMilli(s.getStartOfDayUnixTimestampUTC()).atZone(zoneId).toLocalDate(),
                    s -> s, (s1, s2) -> s2
            ));

            MiningPoolStatisticsAccumulator poolAccumulator = new MiningPoolStatisticsAccumulator();
            List<MiningPoolStatisticsPerDay> allPoolStats = new ArrayList<>();

            for (MiningPoolEntity<?> pool : pvSiteEntity.getConnectedMiningPools()) {
                allPoolStats.addAll(dailyStatisticService.getHistoricalStatistics(pool, "MINING_POOL_DAILY", from, to, poolAccumulator));
                if (!filterDateTo.isBefore(LocalDate.now(zoneId))) {
                    allPoolStats.add(dailyStatisticService.getLiveDailyStatistic(pool, "MINING_POOL_DAILY", poolAccumulator));
                }
            }

            Map<LocalDate, Long> rewardsPerDay = allPoolStats.stream().collect(Collectors.toMap(
                    stats -> Instant.ofEpochMilli(stats.getStartOfDayUnixTimestampUTC()).atZone(zoneId).toLocalDate(),
                    MiningPoolStatisticsPerDay::getAmountCryptoCurrency,
                    Long::sum
            ));

            Map<LocalDate, Double> minerConsumptionPerDay = pvStatsByDate.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().getConsumptionKwhMining()
            ));

            Set<LocalDate> allAvailableDays = new HashSet<>();
            allAvailableDays.addAll(rewardsPerDay.keySet());
            allAvailableDays.addAll(pvStatsByDate.keySet());

            return allAvailableDays.stream()
                    .filter(date -> !date.isBefore(filterDateFrom) && !date.isAfter(filterDateTo))
                    .sorted(Comparator.reverseOrder())
                    .map(currentDay -> calculateDailyStatistic(currentDay, pvSiteEntity, minerConsumptionPerDay, rewardsPerDay, pvStatsByDate, targetCurrency))
                    .toList();
        }
    }

    public double fetchAllTimeMinedBtc(PVSiteEntity pvSiteEntity, ZoneId zoneId) {
        LocalDate today = LocalDate.now(zoneId);
        HistoricalBtcCache cache = allTimeHistoricalBtcCache.get(pvSiteEntity.getId());

        double historicalBtc = 0.0;
        MiningPoolStatisticsAccumulator poolAcc = new MiningPoolStatisticsAccumulator();

        if (cache != null) {
            if (cache.cachedDate().equals(today)) {
                historicalBtc = cache.historicalBtcAmount();
            } else {
                Instant startOfCacheDate = cache.cachedDate().atStartOfDay(zoneId).toInstant();
                Instant startOfToday = today.atStartOfDay(zoneId).toInstant();

                long missingSats = 0;
                for (MiningPoolEntity<?> pool : pvSiteEntity.getConnectedMiningPools()) {
                    List<MiningPoolStatisticsPerDay> gapData = dailyStatisticService.getHistoricalStatistics(pool, "POOL_DAILY", startOfCacheDate, startOfToday.minusMillis(1), poolAcc);
                    missingSats += gapData.stream().mapToLong(MiningPoolStatisticsPerDay::getAmountCryptoCurrency).sum();
                }

                historicalBtc = cache.historicalBtcAmount() + (missingSats / 100_000_000.0);
                allTimeHistoricalBtcCache.put(pvSiteEntity.getId(), new HistoricalBtcCache(today, historicalBtc));
            }
        } else {
            Instant startOfToday = today.atStartOfDay(zoneId).toInstant();
            Instant epochZero = Instant.ofEpochMilli(0);

            long totalHistSats = 0;
            for (MiningPoolEntity<?> pool : pvSiteEntity.getConnectedMiningPools()) {
                List<MiningPoolStatisticsPerDay> history = dailyStatisticService.getHistoricalStatistics(pool, "POOL_DAILY", epochZero, startOfToday.minusMillis(1), poolAcc);
                totalHistSats += history.stream().mapToLong(MiningPoolStatisticsPerDay::getAmountCryptoCurrency).sum();
            }
            historicalBtc = totalHistSats / 100_000_000.0;
            allTimeHistoricalBtcCache.put(pvSiteEntity.getId(), new HistoricalBtcCache(today, historicalBtc));
        }

        long todaySats = 0;
        for (MiningPoolEntity<?> pool : pvSiteEntity.getConnectedMiningPools()) {
            todaySats += dailyStatisticService.getLiveDailyStatistic(pool, "MINING_POOL_TODAY", poolAcc).getAmountCryptoCurrency();
        }

        return historicalBtc + (todaySats / 100_000_000.0);
    }

    private PVStatisticDto calculateDailyStatistic(LocalDate currentDay, PVSiteEntity pvSiteEntity, Map<LocalDate, Double> minerConsumptionPerDay, Map<LocalDate, Long> rewardsPerDay, Map<LocalDate, PVStatisticPerDay> pvStatsByDate, CustomCurrency targetCurrency) {
        double minerConsumption = minerConsumptionPerDay.getOrDefault(currentDay, 0.0);
        long btcEarningsSats = rewardsPerDay.getOrDefault(currentDay, 0L);
        double btcAmount = btcEarningsSats / 100_000_000.0;

        double totalPvProduction = 0.0;
        double miningPvUsage = 0.0;
        double miningGridUsage = minerConsumption;

        double householdEigenverbrauch = 0.0;
        double totalExportKwh = 0.0;

        PVStatisticPerDay siteDayStat = pvStatsByDate.get(currentDay);
        if (siteDayStat != null) {
            double totalSiteConsumption = siteDayStat.getConsumptionKwh();
            double totalImportKwh = siteDayStat.getImportKwh();
            totalExportKwh = siteDayStat.getExportKwh();

            double totalEigenverbrauch = Math.max(0, totalSiteConsumption - totalImportKwh);

            totalPvProduction = totalEigenverbrauch + totalExportKwh;

            miningPvUsage = Math.min(minerConsumption, totalEigenverbrauch);
            miningGridUsage = Math.max(0, minerConsumption - miningPvUsage);
            householdEigenverbrauch = Math.max(0, totalEigenverbrauch - miningPvUsage);
        }

        Money histFeedIn = getPriceForDate(pvSiteEntity.getFeedInTariffHistory(), currentDay);
        Money histGridPrice = getPriceForDate(pvSiteEntity.getElectricityPriceHistory(), currentDay);

        double feedInRate = globalConstantsService.convertHistorical(histFeedIn.getRawMoneyAmount(), histFeedIn.getCurrency(), targetCurrency, currentDay);
        if (feedInRate < 0) feedInRate = globalConstantsService.convert(histFeedIn, targetCurrency).getRawMoneyAmount();

        double gridRate = globalConstantsService.convertHistorical(histGridPrice.getRawMoneyAmount(), histGridPrice.getCurrency(), targetCurrency, currentDay);
        if (gridRate < 0) gridRate = globalConstantsService.convert(histGridPrice, targetCurrency).getRawMoneyAmount();

        double dailyCostFiat = (miningPvUsage * feedInRate) + (miningGridUsage * gridRate);
        Money miningCost = new Money(dailyCostFiat, targetCurrency);

        double savingsFiat = householdEigenverbrauch * gridRate;
        double feedInFiat = totalExportKwh * feedInRate;

        Money householdSavings = new Money(savingsFiat, targetCurrency);
        Money feedInRevenue = new Money(feedInFiat, targetCurrency);

        double btcHistoricFiat = globalConstantsService.convertHistorical(btcAmount, CustomCurrency.getInstance("BTC"), targetCurrency, currentDay);
        double btcLiveFiat = globalConstantsService.convert(new Money(btcAmount, CustomCurrency.getInstance("BTC")), targetCurrency).getRawMoneyAmount();
        Money btcLiveValue = new Money(btcLiveFiat, targetCurrency);
        Money btcHistoricValue = new Money(btcHistoricFiat, targetCurrency);

        double effYield = minerConsumption > 0 ? (btcHistoricFiat / minerConsumption) : 0.0;

        Money effectiveYield = new Money(effYield, targetCurrency);



        return new PVStatisticDto(currentDay, totalPvProduction, minerConsumption, miningPvUsage, miningGridUsage, householdEigenverbrauch, totalExportKwh, btcAmount, miningCost, effectiveYield, btcLiveValue, btcHistoricValue, householdSavings, feedInRevenue, histFeedIn);
    }

    public FinanceKpiDto calculateKPIs(PVSiteEntity pvSiteEntity, List<PVStatisticDto> currentStats, CustomCurrency targetCurrency, ZoneId zoneId) {
        double pvInvestRaw = pvSiteEntity.getPvCost() != null ? globalConstantsService.convert(pvSiteEntity.getPvCost(), targetCurrency).getRawMoneyAmount() : 0.0;
        double hardwareInvestRaw = pvSiteEntity.getMiners().stream()
                .map(m -> globalConstantsService.convert(m.getMinerCost(), targetCurrency).getRawMoneyAmount())
                .reduce(Double::sum).orElse(0.0);
        double totalInvestment = pvInvestRaw + hardwareInvestRaw;

        double allTimeMinedBtc = fetchAllTimeMinedBtc(pvSiteEntity, zoneId);

        double allTimeSoldBtc = 0.0;
        double btcSalesFiat = 0.0;
        for (BitcoinSale sale : pvSiteEntity.getBitcoinSales()) {
            allTimeSoldBtc += sale.getAmountBtc();
            btcSalesFiat += globalConstantsService.convert(sale.getFiatValue(), targetCurrency).getRawMoneyAmount();
        }

        double allTimeHouseholdSavings = currentStats.stream().mapToDouble(s -> s.householdSavings().getRawMoneyAmount()).sum();
        double allTimeFeedInRevenue = currentStats.stream().mapToDouble(s -> s.feedInRevenue().getRawMoneyAmount()).sum();

        double totalRealizedFiat = btcSalesFiat;

        double unsoldBtc = Math.max(0, allTimeMinedBtc - allTimeSoldBtc);
        double unrealizedFiat = globalConstantsService.convert(new Money(unsoldBtc, CustomCurrency.getInstance("BTC")), targetCurrency).getRawMoneyAmount();

        double allTimeOpex = currentStats.stream().mapToDouble(s -> s.miningCost().getRawMoneyAmount()).sum();

        double totalValue = totalRealizedFiat + unrealizedFiat + allTimeHouseholdSavings + allTimeFeedInRevenue;
        double totalCosts = totalInvestment + allTimeOpex;

        double roiProgress = totalCosts > 0 ? (totalValue / totalCosts) * 100.0 : 100.0;

        LocalDate setupDate = pvSiteEntity.getSetupDate() != null ? pvSiteEntity.getSetupDate() : LocalDate.now(zoneId);
        LocalDate today = LocalDate.now(zoneId);
        long daysActive = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(setupDate, today));

        double dailyAverageProfit = totalValue / daysActive;
        LocalDate estimatedBreakEvenDate = null;

        if (roiProgress < 100.0 && dailyAverageProfit > 0) {
            double remainingCost = totalCosts - totalValue;
            long daysToBreakEven = (long) (remainingCost / dailyAverageProfit);
            estimatedBreakEvenDate = today.plusDays(daysToBreakEven);
        } else if (roiProgress >= 100.0) {
            estimatedBreakEvenDate = today;
        }

        return new FinanceKpiDto(
                new Money(totalInvestment, targetCurrency),
                new Money(totalRealizedFiat, targetCurrency),
                new Money(unrealizedFiat, targetCurrency),
                allTimeMinedBtc,
                allTimeSoldBtc,
                unsoldBtc,
                roiProgress,
                new Money(allTimeOpex, targetCurrency),
                new Money(allTimeHouseholdSavings, targetCurrency),
                new Money(allTimeFeedInRevenue, targetCurrency),
                estimatedBreakEvenDate
        );
    }

    public Money getPriceForDate(List<HistoricalPrice> history, LocalDate date) {
        if (history == null || history.isEmpty()) return new Money(0.0, CustomCurrency.getInstance("EUR"));
        return history.stream()
                .filter(hp -> !hp.getValidFrom().isAfter(date))
                .findFirst()
                .map(HistoricalPrice::getPrice)
                .orElse(history.get(history.size() - 1).getPrice());
    }
}