package de.verdox.pv_miner.dashboard;

import de.verdox.pv_miner.entity.EntityMonitoringService;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.dto.*;
import de.verdox.pv_miner.globalconstants.GlobalConstantsService;
import de.verdox.pv_miner.lightning.LightningWalletService;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.miningpool.MiningPoolData;
import de.verdox.pv_miner.miningpool.MiningPoolEntity;
import de.verdox.pv_miner.miningcontroller.MinerClusterService;
import de.verdox.pv_miner.miningcontroller.MinerLock;
import de.verdox.pv_miner.pvsite.*;
import de.verdox.pv_miner.statistic.daily.DailyStatisticService;
import de.verdox.pv_miner.frontend.user.UserSessionContext;
import de.verdox.pv_miner.util.FormatUtil;
import de.verdox.pv_miner.util.Money;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

@Service
public class DashboardFacadeService {
    private static final Logger LOGGER = Logger.getLogger(DashboardFacadeService.class.getName());

    private final PVSiteRepository pVSiteRepository;
    private final EntityMonitoringService monitoringService;
    private final DailyStatisticService dailyStatisticService;
    private final EntityQueryService entityQueryService;
    private final GlobalConstantsService globalConstantsService;
    private final DashboardChartQueryService dashboardChartQueryService;
    private final LightningWalletService walletService;
    private final PVStatisticsAccumulator pvAccumulator = new PVStatisticsAccumulator();
    private final MinerClusterService minerClusterService;

    public DashboardFacadeService(PVSiteRepository pVSiteRepository,
                                  EntityMonitoringService monitoringService,
                                  DailyStatisticService dailyStatisticService,
                                  EntityQueryService entityQueryService,
                                  GlobalConstantsService globalConstantsService,
                                  DashboardChartQueryService dashboardChartQueryService,
                                  LightningWalletService walletService, MinerClusterService minerClusterService) {
        this.pVSiteRepository = pVSiteRepository;
        this.monitoringService = monitoringService;
        this.dailyStatisticService = dailyStatisticService;
        this.entityQueryService = entityQueryService;
        this.globalConstantsService = globalConstantsService;
        this.dashboardChartQueryService = dashboardChartQueryService;
        this.walletService = walletService;
        this.minerClusterService = minerClusterService;
    }

    public PVSiteEntity getSiteEntity(UUID siteId) {
        return pVSiteRepository.findById(siteId).orElseThrow();
    }

    public CompletableFuture<DashboardChartDataDto> loadChartData(PVSiteEntity pvSite, long startTodayMilli, long endTodayMilli, long pvSiteStartMilli) {
        return dashboardChartQueryService.load(pvSite, startTodayMilli, endTodayMilli, pvSiteStartMilli);
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
        double allocatedImport = Math.min(Math.max(0, totalImported), Math.max(0, totalConsumption));
        double miningConsumptionShare = totalConsumption > 0
                ? clamp(totalConsumptionMiners / totalConsumption, 0, 1)
                : 0;
        double miningImport = Math.min(totalConsumptionMiners, allocatedImport * miningConsumptionShare);
        double householdImport = Math.min(pureHouseholdConsumption, Math.max(0, allocatedImport - miningImport));
        double householdEigenverbrauch = Math.max(0, pureHouseholdConsumption - householdImport);
        double miningEigenverbrauch = Math.max(0, totalConsumptionMiners - miningImport);

        double householdSavings = householdEigenverbrauch * stromPreis;
        double revenue = totalExported * einspeiseVerguetung;
        double totalImportCosts = totalImported * stromPreis;
        double miningOpportunityCosts = miningEigenverbrauch * einspeiseVerguetung;

        List<MinerStats> currentMinerStats = pvSiteEntity.getMiners().stream()
                .map(miner -> entityQueryService.getLastResult(miner, MinerStats.DEFAULT))
                .toList();
        double teraHashPerSecond = currentMinerStats.stream().mapToDouble(MinerStats::terahashPerSecond).sum();
        double actualMinerPowerWatts = currentMinerStats.stream().mapToDouble(MinerStats::approximatedPowerUsageWatts).sum();

        long amountRunningMiners = currentMinerStats.stream()
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

        double production = Math.max(0, todayStats.getProductionKwh());
        double selfConsumedProduction = Math.max(0, Math.min(production, production - totalExported));
        double selfConsumptionPercent = percentage(selfConsumedProduction, production);
        double autarkyPercent = percentage(Math.max(0, totalConsumption - totalImported), totalConsumption);
        double miningGridCost = miningImport * stromPreis;

        long minedSats = Math.round(pvSiteEntity.getConnectedMiningPools().stream()
                .mapToDouble(this::safeMiningRewardToday)
                .sum());
        double btcRate = globalConstantsService.getExchangeRate(CustomCurrency.getInstance("BTC"), userCurrency);
        double miningRevenue = btcRate > 0 ? minedSats / 100_000_000.0 * btcRate : 0;
        double miningNetResult = miningRevenue - miningGridCost - miningOpportunityCosts;

        double batteryPower = pvSiteData.getBatteryPower();
        double batteryCapacityKwh = Math.max(0, pvSiteEntity.getBatteryCapacityWh()) / 1000.0;
        Double batteryRuntimeHours = batteryPower < -0.01 && batteryCapacityKwh > 0
                ? batteryCapacityKwh * clamp(pvSiteData.getBatterySoC(), 0, 100) / 100.0 / Math.abs(batteryPower)
                : null;
        LiveEnergyDto energyDto = new LiveEnergyDto(
                finitePositive(pvSiteData.getPvPower()),
                finitePositive(pvSiteData.getLoadPowerKw() - pvSiteData.getTotalMinerPowerKw()),
                finitePositive(pvSiteData.getTotalMinerPowerKw()),
                finitePositive(pvSiteData.getLoadPowerKw()),
                finitePositive(pvSiteData.getImportPowerKw()),
                finitePositive(pvSiteData.getExportPowerKw()),
                Double.isFinite(batteryPower) ? batteryPower : 0,
                clamp(pvSiteData.getBatterySoC(), 0, 100),
                batteryCapacityKwh,
                batteryRuntimeHours,
                batteryPower > 0.01 ? "CHARGING" : batteryPower < -0.01 ? "DISCHARGING" : "IDLE"
        );

        DailyEnergySummaryDto dayDto = new DailyEnergySummaryDto(
                production,
                totalConsumption,
                pureHouseholdConsumption,
                totalConsumptionMiners,
                totalImported,
                totalExported,
                selfConsumedProduction,
                selfConsumptionPercent,
                autarkyPercent,
                miningEigenverbrauch,
                miningImport,
                revenue,
                totalImportCosts,
                householdSavings,
                miningOpportunityCosts,
                minedSats,
                miningRevenue,
                miningNetResult,
                currencySymbol
        );

        String clusterName = pvSiteEntity.getMiners().stream()
                .map(miner -> miner.getClusterName())
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElseGet(() -> minerClusterService.getAvailableClusterNames().stream().sorted().findFirst().orElse(""));
        var clusterInstance = clusterName.isBlank() ? null : minerClusterService.getCluster(pvSiteEntity, clusterName);
        List<MinerClusterService.ClusterInstance.ClusterStateSnapshot> controllerHistory = clusterInstance == null
                ? List.of()
                : clusterInstance.getHistory();
        var lastControllerState = controllerHistory.isEmpty() ? null : controllerHistory.getLast();
        String lastControllerAction = "";
        for (int index = controllerHistory.size() - 1; index >= 0; index--) {
            String event = controllerHistory.get(index).eventDescription();
            if (event != null && !event.isBlank()) {
                lastControllerAction = event;
                break;
            }
        }

        double minerShare = pvSiteData.getLoadPowerKw() > 0
                ? clamp(pvSiteData.getTotalMinerPowerKw() / pvSiteData.getLoadPowerKw(), 0, 1)
                : 0;
        double estimatedGridPowerWatts = Math.min(actualMinerPowerWatts, pvSiteData.getImportPowerKw() * 1000 * minerShare);
        double estimatedBatteryPowerWatts = Math.min(
                Math.max(0, actualMinerPowerWatts - estimatedGridPowerWatts),
                Math.max(0, -batteryPower) * 1000 * minerShare
        );
        double estimatedPvPowerWatts = Math.max(0, actualMinerPowerWatts - estimatedGridPowerWatts - estimatedBatteryPowerWatts);
        MiningOverviewDto miningDto = new MiningOverviewDto(
                teraHashPerSecond,
                actualMinerPowerWatts,
                lastControllerState == null ? 0 : lastControllerState.targetPowerWatts(),
                teraHashPerSecond > 0 ? actualMinerPowerWatts / teraHashPerSecond : 0,
                Math.toIntExact(amountRunningMiners),
                pvSiteEntity.getMiners().size(),
                estimatedPvPowerWatts,
                estimatedBatteryPowerWatts,
                estimatedGridPowerWatts,
                clusterName,
                clusterInstance != null && clusterInstance.isRunning(),
                lastControllerState == null ? "IDLE" : lastControllerState.activeModeName(),
                lastControllerAction
        );

        Instant measuredAt = entityQueryService.getLastSuccessfulQueryAt(pvSiteEntity).orElse(null);
        Instant failedAt = entityQueryService.getLastFailedQueryAt(pvSiteEntity).orElse(null);
        long ageSeconds = measuredAt == null ? -1 : Math.max(0, Duration.between(measuredAt, Instant.now()).toSeconds());
        String sourceStatus = measuredAt == null
                ? (failedAt == null ? "WAITING" : "OFFLINE")
                : failedAt != null && failedAt.isAfter(measuredAt)
                    ? "OFFLINE"
                    : ageSeconds > 15 ? "STALE" : "ONLINE";
        DataQualityDto dataQualityDto = new DataQualityDto(
                sourceStatus,
                pvSiteEntity.getClass().getSimpleName(),
                measuredAt,
                ageSeconds
        );

        String walletFormatted = convertSatsToUserCurrencyString(walletService.getBalanceSat(), userCurrency, locale);

        List<MinerLockStatusDto> lockStatuses = pvSiteEntity.getMiners().stream().map(miner -> {
            var minerCluster = miner.getClusterName() == null || miner.getClusterName().isBlank()
                    ? null
                    : minerClusterService.getCluster(pvSiteEntity, miner.getClusterName());
            Map<UUID, MinerLock> locks = minerCluster == null ? Map.of() : minerCluster.getActiveLocks();
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
        }).filter(lock -> lock.stateLockRemainingSeconds() > 0 || lock.powerLockRemainingSeconds() > 0).toList();

        return new LiveDashboardUpdateDto(
                kpiDto,
                lockStatuses,
                financialDto,
                walletFormatted,
                energyDto,
                dayDto,
                miningDto,
                dataQualityDto
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private double safeMiningRewardToday(MiningPoolEntity<?> pool) {
        try {
            MiningPoolData data = (MiningPoolData) entityQueryService.getLastResult((MiningPoolEntity) pool, MiningPoolData.DEFAULT);
            return Math.max(0, data.calculateSatoshiRewardToday());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private double percentage(double part, double total) {
        return total > 0 ? clamp(part / total * 100, 0, 100) : 0;
    }

    private double finitePositive(double value) {
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }

    private double clamp(double value, double minimum, double maximum) {
        return Double.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : minimum;
    }

    private String convertSatsToUserCurrencyString(long sats, CustomCurrency userCurrency, Locale locale) {
        double btc = sats / 100000000.0;
        double rate = globalConstantsService.getExchangeRate(CustomCurrency.getInstance("BTC"), userCurrency);
        if (rate <= 0.0) return "-1";

        return String.format("%,d sats (%s%,.2f)", sats, userCurrency.getSymbol(locale), btc * rate);
    }
}
