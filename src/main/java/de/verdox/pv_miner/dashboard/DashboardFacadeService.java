package de.verdox.pv_miner.dashboard;

import de.verdox.pv_miner.entity.EntityMonitoringService;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.frontend.pvsite.dashboard.dto.DailyFinancialStatsDto;
import de.verdox.pv_miner.frontend.pvsite.dashboard.dto.LiveDashboardUpdateDto;
import de.verdox.pv_miner.frontend.pvsite.dashboard.dto.LiveKpiDto;
import de.verdox.pv_miner.frontend.pvsite.dashboard.dto.MinerLockStatusDto;
import de.verdox.pv_miner.globalconstants.GlobalConstantsService;
import de.verdox.pv_miner.lightning.LightningWalletService;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.miningcontroller.MinerClusterService;
import de.verdox.pv_miner.miningcontroller.MinerLock;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import de.verdox.pv_miner.pvsite.PVStatisticPerDay;
import de.verdox.pv_miner.pvsite.PVStatisticsAccumulator;
import de.verdox.pv_miner.statistic.daily.DailyStatisticService;
import de.verdox.pv_miner.frontend.user.UserSessionContext;
import de.verdox.pv_miner.util.FormatUtil;
import de.verdox.pv_miner.util.Money;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

@Service
public class DashboardFacadeService {
    private static final Logger LOGGER = Logger.getLogger(DashboardFacadeService.class.getName());

    private final PVSiteRepository pVSiteRepository;
    private final EntityMonitoringService monitoringService;
    private final DailyStatisticService dailyStatisticService;
    private final EntityQueryService entityQueryService;
    private final GlobalConstantsService globalConstantsService;
    private final LightningWalletService walletService;
    private final PVStatisticsAccumulator pvAccumulator = new PVStatisticsAccumulator();
    private final MinerClusterService minerClusterService;

    public DashboardFacadeService(PVSiteRepository pVSiteRepository,
                                  EntityMonitoringService monitoringService,
                                  DailyStatisticService dailyStatisticService,
                                  EntityQueryService entityQueryService,
                                  GlobalConstantsService globalConstantsService,
                                  LightningWalletService walletService, MinerClusterService minerClusterService) {
        this.pVSiteRepository = pVSiteRepository;
        this.monitoringService = monitoringService;
        this.dailyStatisticService = dailyStatisticService;
        this.entityQueryService = entityQueryService;
        this.globalConstantsService = globalConstantsService;
        this.walletService = walletService;
        this.minerClusterService = minerClusterService;
    }

    public PVSiteEntity getSiteEntity(UUID siteId) {
        return pVSiteRepository.findById(siteId).orElseThrow();
    }

    public Flux<LiveDashboardUpdateDto> subscribeToLiveUpdates(PVSiteEntity pvSiteEntity, UserSessionContext sessionContext) {
        return monitoringService.hookIntoLiveData(pvSiteEntity).map(pvSiteData -> {
            PVStatisticPerDay todayStats = dailyStatisticService.getLiveDailyStatistic(pvSiteEntity, "PV_DAILY", pvAccumulator);
            CustomCurrency userCurrency = sessionContext.getCurrency() != null ? sessionContext.getCurrency() : CustomCurrency.getInstance("EUR");

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

            String currencySymbol = userCurrency.getSymbol(sessionContext.getLocale());

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

            String walletFormatted = convertSatsToUserCurrencyString(walletService.getBalanceSat(), sessionContext);

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
        });
    }

    private String convertSatsToUserCurrencyString(long sats, UserSessionContext sessionContext) {
        double btc = sats / 100000000.0;
        CustomCurrency userCurrency = sessionContext != null && sessionContext.getCurrency() != null
                ? sessionContext.getCurrency() : CustomCurrency.getInstance("EUR");
        double rate = globalConstantsService.getExchangeRate(CustomCurrency.getInstance("BTC"), userCurrency);
        if (rate <= 0.0) return "-1";

        return String.format("%,d sats (%s%,.2f)", sats, userCurrency.getSymbol(sessionContext != null ? sessionContext.getLocale() : null), btc * rate);
    }
}
