package de.verdox.pv_miner.finance;

import de.verdox.pv_miner.dto.FinanceKpiDto;
import de.verdox.pv_miner.dto.FinanceInsightsDto;
import de.verdox.pv_miner.dto.MoneyDto;
import de.verdox.pv_miner.dto.PVStatisticDto;
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
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class PVFinanceService {
    private static final LocalDate MAX_WEB_DATE = LocalDate.of(9999, 12, 31);

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
            totalPvProduction = siteDayStat.getProductionKwh() > 0
                    ? siteDayStat.getProductionKwh()
                    : totalEigenverbrauch + totalExportKwh;

            double miningConsumptionShare = totalSiteConsumption > 0
                    ? Math.max(0, Math.min(1, minerConsumption / totalSiteConsumption))
                    : 0;
            miningPvUsage = Math.min(minerConsumption, totalEigenverbrauch * miningConsumptionShare);
            miningGridUsage = Math.max(0, minerConsumption - miningPvUsage);
            householdEigenverbrauch = Math.max(0, totalEigenverbrauch - miningPvUsage);
        }

        Money histFeedIn = getPriceForDate(pvSiteEntity.getFeedInTariffHistory(), currentDay);
        Money histGridPrice = getPriceForDate(pvSiteEntity.getElectricityPriceHistory(), currentDay);

        double feedInRate = globalConstantsService.convertHistorical(histFeedIn.getRawMoneyAmount(), histFeedIn.getCurrency(), targetCurrency, currentDay);
        if (feedInRate < 0) feedInRate = globalConstantsService.convert(histFeedIn, targetCurrency).getRawMoneyAmount();

        double gridRate = globalConstantsService.convertHistorical(histGridPrice.getRawMoneyAmount(), histGridPrice.getCurrency(), targetCurrency, currentDay);
        if (gridRate < 0) gridRate = globalConstantsService.convert(histGridPrice, targetCurrency).getRawMoneyAmount();

        double miningOpportunityCostFiat = miningPvUsage * feedInRate;
        double miningGridCostFiat = miningGridUsage * gridRate;
        double dailyCostFiat = miningOpportunityCostFiat + miningGridCostFiat;
        Money miningCost = new Money(dailyCostFiat, targetCurrency);
        Money miningGridCost = new Money(miningGridCostFiat, targetCurrency);
        Money miningOpportunityCost = new Money(miningOpportunityCostFiat, targetCurrency);

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



        return new PVStatisticDto(
                currentDay,
                totalPvProduction,
                minerConsumption,
                miningPvUsage,
                miningGridUsage,
                householdEigenverbrauch,
                totalExportKwh,
                btcAmount,
                MoneyDto.from(miningCost),
                MoneyDto.from(miningGridCost),
                MoneyDto.from(miningOpportunityCost),
                MoneyDto.from(effectiveYield),
                MoneyDto.from(btcLiveValue),
                MoneyDto.from(btcHistoricValue),
                MoneyDto.from(householdSavings),
                MoneyDto.from(feedInRevenue),
                MoneyDto.from(histFeedIn)
        );
    }

    public FinanceInsightsDto calculateInsights(PVSiteEntity site,
                                                List<PVStatisticDto> statistics,
                                                FinanceKpiDto kpis,
                                                CustomCurrency targetCurrency,
                                                ZoneId zoneId) {
        double miningRevenueHistoric = statistics.stream().mapToDouble(day -> day.btcHistoricValue().getRawMoneyAmount()).sum();
        double miningRevenueLive = statistics.stream().mapToDouble(day -> day.btcLiveValue().getRawMoneyAmount()).sum();
        double miningEnergyCost = statistics.stream().mapToDouble(day -> day.miningCost().getRawMoneyAmount()).sum();
        double miningGridCost = statistics.stream().mapToDouble(day -> day.miningGridCost().getRawMoneyAmount()).sum();
        double miningOpportunityCost = statistics.stream().mapToDouble(day -> day.miningOpportunityCost().getRawMoneyAmount()).sum();
        double householdSavings = statistics.stream().mapToDouble(day -> day.householdSavings().getRawMoneyAmount()).sum();
        double feedInRevenue = statistics.stream().mapToDouble(day -> day.feedInRevenue().getRawMoneyAmount()).sum();
        double minedBtc = statistics.stream().mapToDouble(PVStatisticDto::minedBtc).sum();
        double miningEnergyKwh = statistics.stream().mapToDouble(PVStatisticDto::minerConsumption).sum();
        double miningGridKwh = statistics.stream().mapToDouble(PVStatisticDto::miningGridUsage).sum();

        double miningNetHistoric = miningRevenueHistoric - miningEnergyCost;
        double miningNetLive = miningRevenueLive - miningEnergyCost;
        double totalValueCreated = miningRevenueHistoric + householdSavings + feedInRevenue;
        double operatingResult = totalValueCreated - miningEnergyCost;
        int daysWithData = statistics.size();
        double averageDailyOperatingResult = daysWithData > 0 ? operatingResult / daysWithData : 0;
        double costPerMinedBtc = minedBtc > 0 ? miningEnergyCost / minedBtc : 0;
        double breakEvenBtcPrice = costPerMinedBtc;
        double gridShare = miningEnergyKwh > 0 ? Math.max(0, Math.min(100, miningGridKwh / miningEnergyKwh * 100)) : 0;
        int profitableMiningDays = (int) statistics.stream()
                .filter(day -> day.btcHistoricValue().getRawMoneyAmount() >= day.miningCost().getRawMoneyAmount())
                .count();

        Comparator<PVStatisticDto> byOperatingResult = Comparator.comparingDouble(this::dailyOperatingResult);
        PVStatisticDto bestDay = statistics.stream().max(byOperatingResult).orElse(null);
        PVStatisticDto worstDay = statistics.stream().min(byOperatingResult).orElse(null);

        double totalCapitalValue = kpis.realizedProfit().getRawMoneyAmount()
                + kpis.unrealizedValue().getRawMoneyAmount()
                + kpis.totalHouseholdSavings().getRawMoneyAmount()
                + kpis.totalFeedInRevenue().getRawMoneyAmount();
        double totalCapitalCost = kpis.totalInvestment().getRawMoneyAmount() + kpis.totalOpex().getRawMoneyAmount();
        double netPosition = totalCapitalValue - totalCapitalCost;
        double remainingToBreakEven = Math.max(0, totalCapitalCost - totalCapitalValue);
        LocalDate setupDate = site.getSetupDate() == null ? LocalDate.now(zoneId) : site.getSetupDate();
        long activeDays = Math.max(1, ChronoUnit.DAYS.between(setupDate, LocalDate.now(zoneId)) + 1);
        double averageDailyCapitalValue = totalCapitalValue / activeDays;

        return new FinanceInsightsDto(
                money(miningRevenueHistoric, targetCurrency),
                money(miningRevenueLive, targetCurrency),
                money(miningEnergyCost, targetCurrency),
                money(miningGridCost, targetCurrency),
                money(miningOpportunityCost, targetCurrency),
                money(miningNetHistoric, targetCurrency),
                money(miningNetLive, targetCurrency),
                money(householdSavings, targetCurrency),
                money(feedInRevenue, targetCurrency),
                money(totalValueCreated, targetCurrency),
                money(operatingResult, targetCurrency),
                money(averageDailyOperatingResult, targetCurrency),
                money(costPerMinedBtc, targetCurrency),
                money(breakEvenBtcPrice, targetCurrency),
                money(totalCapitalValue, targetCurrency),
                money(totalCapitalCost, targetCurrency),
                money(netPosition, targetCurrency),
                money(remainingToBreakEven, targetCurrency),
                money(averageDailyCapitalValue, targetCurrency),
                minedBtc,
                miningEnergyKwh,
                gridShare,
                profitableMiningDays,
                daysWithData,
                bestDay == null ? null : bestDay.date(),
                money(bestDay == null ? 0 : dailyOperatingResult(bestDay), targetCurrency),
                worstDay == null ? null : worstDay.date(),
                money(worstDay == null ? 0 : dailyOperatingResult(worstDay), targetCurrency)
        );
    }

    private double dailyOperatingResult(PVStatisticDto day) {
        return day.btcHistoricValue().getRawMoneyAmount()
                + day.householdSavings().getRawMoneyAmount()
                + day.feedInRevenue().getRawMoneyAmount()
                - day.miningCost().getRawMoneyAmount();
    }

    private MoneyDto money(double amount, CustomCurrency currency) {
        return MoneyDto.of(Double.isFinite(amount) ? amount : 0, currency);
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

        double dailyAverageProfit = (totalValue - allTimeOpex) / daysActive;
        LocalDate estimatedBreakEvenDate = estimateBreakEvenDate(today, totalCosts, totalValue, dailyAverageProfit);

        return new FinanceKpiDto(
                MoneyDto.of(totalInvestment, targetCurrency),
                MoneyDto.of(totalRealizedFiat, targetCurrency),
                MoneyDto.of(unrealizedFiat, targetCurrency),
                allTimeMinedBtc,
                allTimeSoldBtc,
                unsoldBtc,
                roiProgress,
                MoneyDto.of(allTimeOpex, targetCurrency),
                MoneyDto.of(allTimeHouseholdSavings, targetCurrency),
                MoneyDto.of(allTimeFeedInRevenue, targetCurrency),
                estimatedBreakEvenDate
        );
    }

    static LocalDate estimateBreakEvenDate(LocalDate today, double totalCosts, double totalValue, double dailyAverageProfit) {
        if (!Double.isFinite(totalCosts) || !Double.isFinite(totalValue)) {
            return null;
        }
        if (totalValue >= totalCosts) {
            return today;
        }
        if (!Double.isFinite(dailyAverageProfit) || dailyAverageProfit <= 0) {
            return null;
        }

        double daysToBreakEven = (totalCosts - totalValue) / dailyAverageProfit;
        long maximumSupportedDays = ChronoUnit.DAYS.between(today, MAX_WEB_DATE);
        if (!Double.isFinite(daysToBreakEven) || daysToBreakEven > maximumSupportedDays) {
            return null;
        }
        return today.plusDays((long) Math.ceil(daysToBreakEven));
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
