package de.verdox.pv_miner.dashboard;

import de.verdox.pv_miner.entity.EntityMonitoringService;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.frontend.pvsite.dashboard.dto.*;
import de.verdox.pv_miner.globalconstants.GlobalConstantsService;
import de.verdox.pv_miner.lightning.LightningWalletService;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.miningcontroller.MinerClusterService;
import de.verdox.pv_miner.miningcontroller.MinerLock;
import de.verdox.pv_miner.pvsite.*;
import de.verdox.pv_miner.statistic.daily.DailyStatisticService;
import de.verdox.pv_miner.frontend.user.UserSessionContext;
import de.verdox.pv_miner.statistic.live.EntityStatisticsService;
import de.verdox.pv_miner.util.FormatUtil;
import de.verdox.pv_miner.util.Money;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@Service
public class DashboardFacadeService {
    private static final Logger LOGGER = Logger.getLogger(DashboardFacadeService.class.getName());

    private final PVSiteRepository pVSiteRepository;
    private final EntityMonitoringService monitoringService;
    private final DailyStatisticService dailyStatisticService;
    private final EntityQueryService entityQueryService;
    private final GlobalConstantsService globalConstantsService;
    private final EntityStatisticsService entityStatisticsService;
    private final LightningWalletService walletService;
    private final PVStatisticsAccumulator pvAccumulator = new PVStatisticsAccumulator();
    private final MinerClusterService minerClusterService;
    private final Map<UUID, CompletableFuture<DashboardChartDataDto>> activeChartLoads = new ConcurrentHashMap<>();

    public DashboardFacadeService(PVSiteRepository pVSiteRepository,
                                  EntityMonitoringService monitoringService,
                                  DailyStatisticService dailyStatisticService,
                                  EntityQueryService entityQueryService,
                                  GlobalConstantsService globalConstantsService,
                                  EntityStatisticsService entityStatisticsService,
                                  LightningWalletService walletService, MinerClusterService minerClusterService) {
        this.pVSiteRepository = pVSiteRepository;
        this.monitoringService = monitoringService;
        this.dailyStatisticService = dailyStatisticService;
        this.entityQueryService = entityQueryService;
        this.globalConstantsService = globalConstantsService;
        this.entityStatisticsService = entityStatisticsService;
        this.walletService = walletService;
        this.minerClusterService = minerClusterService;
    }

    public PVSiteEntity getSiteEntity(UUID siteId) {
        return pVSiteRepository.findById(siteId).orElseThrow();
    }

    public CompletableFuture<DashboardChartDataDto> loadChartData(PVSiteEntity pvSite, long startTodayMilli, long endTodayMilli, long pvSiteStartMilli) {
        return activeChartLoads.computeIfAbsent(pvSite.getId(), id -> {

            var pvPowerFuture = CompletableFuture.supplyAsync(() -> entityStatisticsService.loadStatistic(entityStatisticsService.PV_POWER_DAY_STATISTIC, pvSite, startTodayMilli, endTodayMilli, false));
            var importFuture = CompletableFuture.supplyAsync(() -> entityStatisticsService.loadStatistic(entityStatisticsService.PV_IMPORT, pvSite, startTodayMilli, endTodayMilli, false));
            var exportFuture = CompletableFuture.supplyAsync(() -> entityStatisticsService.loadStatistic(entityStatisticsService.PV_GRID_EXPORT, pvSite, startTodayMilli, endTodayMilli, false));
            var consumptionFuture = CompletableFuture.supplyAsync(() -> entityStatisticsService.loadStatistic(entityStatisticsService.CONSUMPTION, pvSite, startTodayMilli, endTodayMilli, false));
            var minerConsumptionFuture = CompletableFuture.supplyAsync(() -> entityStatisticsService.loadStatistic(entityStatisticsService.MINER_CONSUMPTION, pvSite, startTodayMilli, endTodayMilli, false));
            var historyFuture = CompletableFuture.supplyAsync(() -> entityStatisticsService.loadStatistic(entityStatisticsService.PV_POWER_PER_HOUR_STATISTIC, pvSite, pvSiteStartMilli, endTodayMilli, false));

            return CompletableFuture.allOf(pvPowerFuture, importFuture, exportFuture, consumptionFuture, minerConsumptionFuture, historyFuture)
                    .thenApply(v -> new DashboardChartDataDto(
                            pvPowerFuture.join(),
                            importFuture.join(),
                            exportFuture.join(),
                            consumptionFuture.join(),
                            minerConsumptionFuture.join(),
                            historyFuture.join()
                    ))
                    .whenComplete((res, ex) -> activeChartLoads.remove(id));
        });
    }

    public Flux<LiveDashboardUpdateDto> subscribeToLiveUpdates(PVSiteEntity pvSiteEntity, UserSessionContext sessionContext) {
        return monitoringService.hookIntoLiveData(pvSiteEntity).map(pvSiteData -> {
            return getLiveDashboardData(pvSiteEntity, sessionContext.getLocale(), sessionContext.getCurrency(), pvSiteData);
        });
    }

    public @NonNull LiveDashboardUpdateDto getLiveDashboardData(PVSiteEntity pvSiteEntity, Locale locale, CustomCurrency userCurrency, PVSiteDataDTO pvSiteData) {
        PVStatisticPerDay todayStats = dailyStatisticService.getLiveDailyStatistic(pvSiteEntity, "PV_DAILY", pvAccumulator);

        Money currentStrom = pvSiteEntity.getCurrentElectricityPrice();
        Money currentFeedIn = pvSiteEntity.getCurrentFeedInTariff();
        double stromPreis = globalConstantsService.convert(currentStrom, userCurrency).getRawMoneyAmount();
        double einspeiseVerguetung = globalConstantsService.convert(currentFeedIn, userCurrency).getRawMoneyAmount();

        double totalExported = todayStats.getExportKwh();
        double totalConsumption = todayStats.getConsumptionKwh();
        double totalConsumptionMiners = todayStats.getConsumptionKwhMining();
        double totalImported = todayStats.getImportKwh();

        double pureHouseholdConsumption = Math.max(0, totalConsumption - totalConsumptionMiners);
        double totalEigenverbrauch = Math.max(0, totalConsumption - totalImported);
        double householdEigenverbrauch = Math.min(pureHouseholdConsumption, totalEigenverbrauch);
        double miningEigenverbrauch = Math.max(0, totalEigenverbrauch - householdEigenverbrauch);
        double householdImport = Math.max(0, pureHouseholdConsumption - householdEigenverbrauch);
        double miningImport = Math.max(0, totalConsumptionMiners - miningEigenverbrauch);

        double householdSavings = householdEigenverbrauch * stromPreis;
        double revenue = totalExported * einspeiseVerguetung;
        double totalImportCosts = totalImported * stromPreis;
        double miningOpportunityCosts = miningEigenverbrauch * einspeiseVerguetung;

        double teraHashPerSecond = pvSiteEntity.getMiners().stream()
                .map(miner -> entityQueryService.getLastResult(miner, MinerStats.DEFAULT))
                .mapToDouble(MinerStats::terahashPerSecond).sum();

        long amountRunningMiners = pvSiteEntity.getMiners().stream()
                .map(miner -> entityQueryService.getLastResult(miner, MinerStats.DEFAULT))
                .filter(minerStats -> minerStats.terahashPerSecond() > 0).count();

        String currencySymbol = userCurrency.getSymbol(locale);

        LiveKpiDto kpiDto = new LiveKpiDto(
                FormatUtil.formatNumber(pvSiteData.getPvPower()) + " kW",
                FormatUtil.formatNumber(pvSiteData.getTotalMinerPowerKw()) + " kW",
                FormatUtil.formatNumber(pvSiteData.getLoadPowerKw()) + " kW",
                FormatUtil.formatNumber(pvSiteData.getImportPowerKw()) + " kW",
                FormatUtil.formatNumber(pvSiteData.getExportPowerKw()) + " kW",
                pvSiteData.getBatterySoC() + " %",
                (pvSiteData.getBatteryPower() > 0 ? "+" : "") + FormatUtil.formatNumber(pvSiteData.getBatteryPower()) + " kW",
                FormatUtil.formatHashrateFromTHs(teraHashPerSecond),
                amountRunningMiners + " / " + pvSiteEntity.getMiners().size()
        );

        DailyFinancialStatsDto financialDto = new DailyFinancialStatsDto(
                FormatUtil.formatNumber(totalExported) + " kWh",
                FormatUtil.formatNumber(revenue) + " " + currencySymbol,
                FormatUtil.formatNumber(totalImported) + " kWh",
                FormatUtil.formatNumber(totalImportCosts) + " " + currencySymbol,
                FormatUtil.formatNumber(pureHouseholdConsumption) + " kWh",
                FormatUtil.formatNumber(householdSavings) + " " + currencySymbol,
                FormatUtil.formatNumber(totalConsumptionMiners) + " kWh",
                FormatUtil.formatNumber(miningOpportunityCosts) + " " + currencySymbol
        );

        String walletFormatted = convertSatsToUserCurrencyString(walletService.getBalanceSat(), userCurrency, locale);

        var clusterInstance = minerClusterService.getCluster(pvSiteEntity, "Standard");
        Map<UUID, MinerLock> locks = clusterInstance != null ? clusterInstance.getActiveLocks() : Map.of();

        List<MinerLockStatusDto> lockStatuses = pvSiteEntity.getMiners().stream().map(miner -> {
            MinerLock lock = locks.get(miner.getId());
            long stateRemaining = 0;
            long powerRemaining = 0;
            double expectedPower = 0;

            if (lock != null) {
                Instant now = Instant.now();
                stateRemaining = Math.max(0, java.time.Duration.between(now, lock.runStateUnlockTime()).toSeconds());
                powerRemaining = Math.max(0, java.time.Duration.between(now, lock.powerChangeUnlockTime()).toSeconds());
                expectedPower = lock.expectedPowerWatts();
            }

            return new MinerLockStatusDto(
                    miner.getName() != null ? miner.getName() : "Miner",
                    miner.getIP(),
                    stateRemaining,
                    powerRemaining,
                    expectedPower
            );
        }).toList();

        return new LiveDashboardUpdateDto(kpiDto, lockStatuses, financialDto, walletFormatted);
    }

    private String convertSatsToUserCurrencyString(long sats, CustomCurrency userCurrency, Locale locale) {
        double btc = sats / 100000000.0;
        double rate = globalConstantsService.getExchangeRate(CustomCurrency.getInstance("BTC"), userCurrency);
        if (rate <= 0.0) return "-1";

        return String.format("%,d sats (%s%,.2f)", sats, userCurrency.getSymbol(locale), btc * rate);
    }
}
