package de.verdox.pv_miner.controller;

import de.verdox.pv_miner.dashboard.DashboardFacadeService;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.frontend.pvsite.dashboard.dto.*;
import de.verdox.pv_miner.miner.data.MinerStats;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/pv-site/{siteId}/dashboard")
@CrossOrigin(origins = "http://localhost:3000") // Erlaubt den Zugriff von deinem lokalen Next.js Dev-Server
public class DashboardController {

    private final PVSiteRepository pvSiteRepository;
    private final MinerClusterService clusterService;
    private final DashboardFacadeService dashboardFacadeService;
    private final EntityQueryService entityQueryService;

    public DashboardController(PVSiteRepository pvSiteRepository,
                               MinerClusterService clusterService,
                               DashboardFacadeService dashboardFacadeService,
                               EntityQueryService entityQueryService) {
        this.pvSiteRepository = pvSiteRepository;
        this.clusterService = clusterService;
        this.dashboardFacadeService = dashboardFacadeService;
        this.entityQueryService = entityQueryService;
    }

    /**
     * INIT ENDPOINT: Langlebige Daten (Miner-Liste, Pools, Chart-Historie)
     */
    @GetMapping("/init")
    public ResponseEntity<DashboardInitData> getInitialData(@PathVariable UUID siteId) {
        PVSiteEntity pvSite = pvSiteRepository.findById(siteId).orElseThrow(() -> new IllegalArgumentException("PV-Site nicht gefunden"));
        List<MinerDashboardItemDTO> miners = new ArrayList<>();
        pvSite.getMiners().forEach(miner -> {
            var stats = entityQueryService.getLastResult(miner, MinerStats.DEFAULT);
            miners.add(new MinerDashboardItemDTO(
                    miner.getName(), miner.getIP(), stats.miningStatus().name(),
                    stats.terahashPerSecond()+" TH/s", stats.approximatedPowerUsageWatts()+" W", stats.temperatureCelsius()+" °C", "Pool", 0, 0, 0
            ));
        });

        List<PoolDto> pools = pvSite.getConnectedMiningPools().stream().map(miningPoolEntity -> new PoolDto(miningPoolEntity.getId().toString(), miningPoolEntity.getStratumV1Url(), "", "")).toList();

        var zoneId = ZoneId.systemDefault(); //TODO: Get from frontend
        long pvSiteStartMilli = pvSite.getSetupDate().atStartOfDay(zoneId).toInstant().toEpochMilli();
        long startTodayMilli = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli();
        long endTodayMilli = LocalDate.now(zoneId).atTime(LocalTime.of(23, 59, 59, 999)).atZone(zoneId).toInstant().toEpochMilli();

        //List<ChartDatapoint> chartHistory = dashboardFacadeService.loadChartData(pvSite, startTodayMilli, endTodayMilli, pvSiteStartMilli).join();

        return ResponseEntity.ok(new DashboardInitData(pvSite.getName(), miners, pools, List.of()));
    }

    /**
     * LIVE ENDPOINT: Wird alle 3 Sekunden vom React-Client gepollt.
     * Nutzt den Facade Service, um den UI-Thread nicht zu blockieren.
     */
    @GetMapping("/live")
    public ResponseEntity<LiveDashboardUpdateDto> getLiveUpdates(
            @PathVariable UUID siteId,
            @RequestParam(value = "locale", defaultValue = "de") String locale
    ) {
        PVSiteEntity pvSite = pvSiteRepository.findById(siteId).orElseThrow(() -> new IllegalArgumentException("PV-Site nicht gefunden"));

        LiveDashboardUpdateDto dto = dashboardFacadeService.getLiveDashboardData(pvSite, Locale.of(locale), CustomCurrency.getInstance("EUR"), entityQueryService.getLastResult(pvSite, PVSiteDataDTO.createDefault()));
        return ResponseEntity.ok(dto);
    }

    public record DashboardInitData(String siteName, List<MinerDashboardItemDTO> miners, List<PoolDto> pools, List<ChartDatapoint> chartData) {}

    public record PoolDto(String id, String url, String worker, String status) {}
    public record ChartDatapoint(long timestamp, double powerKw, double allocatedKw) {}
}