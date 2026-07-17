package de.verdox.pv_miner.controller;

import de.verdox.pv_miner.dashboard.DashboardChartQueryService;
import de.verdox.pv_miner.dashboard.DashboardFacadeService;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.dto.*;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.miningpool.MiningPoolData;
import de.verdox.pv_miner.miningpool.MiningPoolEntity;
import de.verdox.pv_miner.miningcontroller.MinerClusterService;
import de.verdox.pv_miner.pvsite.PVSiteDataDTO;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static de.verdox.pv_miner.dto.DashboardPageDto.*;

@RestController
@RequestMapping("/api/pv-site/{siteId}/dashboard")
@CrossOrigin(origins = "http://localhost:3000") // Erlaubt den Zugriff von deinem lokalen Next.js Dev-Server
public class DashboardController {

    private final PVSiteRepository pvSiteRepository;
    private final MinerClusterService clusterService;
    private final DashboardFacadeService dashboardFacadeService;
    private final EntityQueryService entityQueryService;
    private final DashboardChartQueryService dashboardChartQueryService;

    public DashboardController(PVSiteRepository pvSiteRepository,
                               MinerClusterService clusterService,
                               DashboardFacadeService dashboardFacadeService,
                               EntityQueryService entityQueryService, DashboardChartQueryService dashboardChartQueryService) {
        this.pvSiteRepository = pvSiteRepository;
        this.clusterService = clusterService;
        this.dashboardFacadeService = dashboardFacadeService;
        this.entityQueryService = entityQueryService;
        this.dashboardChartQueryService = dashboardChartQueryService;
    }

    /**
     * INIT ENDPOINT: Langlebige Daten (Miner-Liste, Pools, Chart-Historie)
     */
    @GetMapping("/init")
    public ResponseEntity<DashboardInitDto> getInitialData(@PathVariable UUID siteId) {
        PVSiteEntity pvSite = pvSiteRepository.findById(siteId).orElseThrow(() -> new IllegalArgumentException("PV-Site nicht gefunden"));
        List<MinerDashboardItemDTO> miners = new ArrayList<>();
        pvSite.getMiners().forEach(miner -> {
            var stats = entityQueryService.getLastResult(miner, MinerStats.DEFAULT);
            var cluster = miner.getClusterName() == null || miner.getClusterName().isBlank()
                    ? null
                    : clusterService.getCluster(pvSite.getId(), miner.getClusterName());
            var lock = cluster == null ? null : cluster.getActiveLocks().get(miner.getId());
            long stateRemaining = lock == null ? 0 : remainingSeconds(lock.runStateUnlockTime());
            long powerRemaining = lock == null ? 0 : remainingSeconds(lock.powerChangeUnlockTime());
            String displayName = miner.getName() != null && !miner.getName().isBlank()
                    ? miner.getName()
                    : stats.minerIdentity().minerModel();
            String pool = stats.pools().stream().findFirst().map(value -> value.poolUrl()).orElse("");
            miners.add(new MinerDashboardItemDTO(
                    displayName,
                    miner.getIP(),
                    stats.miningStatus() == null ? "UNKNOWN" : stats.miningStatus().name(),
                    stats.terahashPerSecond() + " TH/s",
                    stats.approximatedPowerUsageWatts() + " W",
                    stats.temperatureCelsius() + " °C",
                    pool,
                    stateRemaining,
                    powerRemaining,
                    stats.powerTargetWatts()
            ));
        });

        List<PoolDto> pools = pvSite.getConnectedMiningPools().stream().map(pool -> new PoolDto(
                pool.getId().toString(),
                pool.getStratumV1Url(),
                getPoolWorker(pool),
                queryStatus(pool)
        )).toList();

        return ResponseEntity.ok(new DashboardInitDto(pvSite.getName(), miners, pools, List.of()));
    }

    /**
     * LIVE ENDPOINT: Wird alle 3 Sekunden vom React-Client gepollt.
     * Nutzt den Facade Service, um den UI-Thread nicht zu blockieren.
     */
    @GetMapping("/live")
    public ResponseEntity<LiveDashboardUpdateDto> getLiveUpdates(
            @PathVariable UUID siteId,
            @RequestParam(value = "locale", defaultValue = "de") String locale,
            @RequestParam(value = "currency", defaultValue = "EUR") String currency
    ) {
        PVSiteEntity pvSite = pvSiteRepository.findById(siteId).orElseThrow(() -> new IllegalArgumentException("PV-Site nicht gefunden"));

        LiveDashboardUpdateDto dto = dashboardFacadeService.getLiveDashboardData(pvSite, Locale.of(locale), CustomCurrency.getInstance(currency), entityQueryService.getLastResult(pvSite, PVSiteDataDTO.createDefault()));
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/charts")
    public ResponseEntity<DashboardChartsDto> getCharts(
            @PathVariable UUID siteId,
            @RequestParam(value = "timeZone", defaultValue = "Europe/Berlin") String timeZone,
            @RequestParam(value = "cluster", defaultValue = "Standard") String clusterName
    ) {
        PVSiteEntity pvSite = pvSiteRepository.findById(siteId)
                .orElseThrow(() -> new IllegalArgumentException("PV-Site nicht gefunden"));

        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(timeZone);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Ungültige Zeitzone", exception);
        }

        long siteStart = pvSite.getSetupDate().atStartOfDay(zoneId).toInstant().toEpochMilli();
        long todayStart = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli();
        long todayEnd = LocalDate.now(zoneId)
                .atTime(LocalTime.of(23, 59, 59, 999_000_000))
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli();

        DashboardChartDataDto statistics = dashboardChartQueryService.load(pvSite, todayStart, todayEnd, siteStart).join();

        List<String> clusterNames = clusterService.getAvailableClusterNames().stream().sorted().toList();
        String selectedCluster = clusterNames.contains(clusterName)
                ? clusterName
                : clusterNames.stream().findFirst().orElse("");
        MinerClusterService.ClusterInstance cluster = selectedCluster.isBlank()
                ? null
                : clusterService.getCluster(pvSite.getId(), selectedCluster);

        List<ControllerChartPointDto> controllerPoints = cluster == null
                ? List.of()
                : cluster.getHistory().stream()
                .map(snapshot -> new ControllerChartPointDto(
                        snapshot.timestamp().toEpochMilli(),
                        snapshot.targetPowerWatts(),
                        snapshot.allocatedPowerWatts(),
                        snapshot.activeModeName(),
                        snapshot.eventDescription()
                ))
                .toList();

        return ResponseEntity.ok(new DashboardChartsDto(
                statistics.live(),
                statistics.pvHistory(),
                new ControllerChartDto(
                        selectedCluster,
                        cluster != null && cluster.isRunning(),
                        controllerPoints
                ),
                clusterNames
        ));
    }

    private long remainingSeconds(Instant unlockTime) {
        return unlockTime == null ? 0 : Math.max(0, Duration.between(Instant.now(), unlockTime).toSeconds());
    }

    private String queryStatus(de.verdox.pv_miner.entity.QueryEntity<?> entity) {
        Instant success = entityQueryService.getLastSuccessfulQueryAt(entity).orElse(null);
        Instant failure = entityQueryService.getLastFailedQueryAt(entity).orElse(null);
        if (success == null) return failure == null ? "WAITING" : "OFFLINE";
        if (failure != null && failure.isAfter(success)) return "OFFLINE";
        return Duration.between(success, Instant.now()).toSeconds() > 30 ? "STALE" : "ONLINE";
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private String getPoolWorker(MiningPoolEntity<?> pool) {
        MiningPoolData data = (MiningPoolData) entityQueryService.getLastResult((MiningPoolEntity) pool, MiningPoolData.DEFAULT);
        String worker = data.getDefaultWorkerName();
        return worker == null ? "" : worker;
    }

}
