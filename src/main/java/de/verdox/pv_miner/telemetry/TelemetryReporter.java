package de.verdox.pv_miner.telemetry;

import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import de.verdox.pv_miner.pvsite.PVStatisticsAccumulator;
import de.verdox.pv_miner.pvsite.PVStatisticPerDay;
import de.verdox.pv_miner.pvsite.inverter.InverterDataDTO;
import de.verdox.pv_miner.statistic.daily.DailyStatisticService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/**
 * Opt-in, outbound, anonymized telemetry for the SolarMiner network statistics
 * (ARCHITECTURE §4/§7). Every ~5 min, for every PV site the owner has opted in
 * to ({@code telemetry_opt_in}), it POSTs a small, privacy-minimized sample to the
 * admin service at {@code solarmining.admin.telemetry.url + /api/telemetry/batch}.
 *
 * <p>What is sent: a stable random {@code uuid} (the node's {@code node_identity},
 * minted locally and kept separate from the primary key), software version, bound
 * referral <i>code</i> (not name), combined hashrate (TH/s), today's solar
 * generation (kWh), lifetime solar generation (kWh), installed PWp, miner count,
 * and an operator-configured coarse location grid. What is NOT sent: names,
 * addresses, GPS, wallet, IPs, or any raw device data.
 *
 * <p>Telemetry is best-effort: any failure is logged, never propagated, and the
 * node keeps mining. When the opt-in flag is off, nothing is sent at all.
 */
@Service
public class TelemetryReporter {
    private static final Logger log = LoggerFactory.getLogger(TelemetryReporter.class);

    private final PVSiteRepository pvSiteRepository;
    private final EntityQueryService queryService;
    private final DailyStatisticService dailyStatisticService;
    private final RestClient restClient;
    private final String version;
    private final String locationGrid;
    private final PVStatisticsAccumulator pvAccumulator = new PVStatisticsAccumulator();

    public TelemetryReporter(
            PVSiteRepository pvSiteRepository,
            EntityQueryService queryService,
            DailyStatisticService dailyStatisticService,
            RestClient.Builder restClientBuilder,
            @Value("${solarmining.admin.telemetry.url:}") String telemetryUrl,
            @Value("${solarminer.version:dev}") String version,
            @Value("${solarmining.admin.telemetry.location-grid:}") String locationGrid) {
        this.pvSiteRepository = pvSiteRepository;
        this.queryService = queryService;
        this.dailyStatisticService = dailyStatisticService;
        String base = trimEnd(telemetryUrl);
        this.restClient = base.isBlank()
                ? null
                : restClientBuilder.baseUrl(base).build();
        this.version = version;
        this.locationGrid = locationGrid == null || locationGrid.isBlank() ? null : locationGrid.trim();
    }

    /**
     * Reports for opted-in sites. fixedDelay (not fixedRate) so a slow tick cannot
     * queue up; an initial delay lets the first monitoring round populate the
     * live-stat cache before we read it.
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void report() {
        if (restClient == null) {
            return; // no telemetry endpoint configured (self-hosted/offline)
        }
        for (PVSiteEntity site : pvSiteRepository.findAll()) {
            if (!site.isTelemetryOptIn()) {
                continue;
            }
            try {
                TelemetryBatch payload = buildPayload(site);
                restClient.post()
                        .uri("/api/telemetry/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .toBodilessEntity();
            } catch (RuntimeException e) {
                // Best-effort: never let telemetry disturb the control loop.
                log.info("Telemetry send failed for site {}: {}", site.getId(), e.getMessage());
            }
        }
    }

    private TelemetryBatch buildPayload(PVSiteEntity site) {
        String identity = ensureIdentity(site);

        int minerCount = site.getMiners().size();
        double hashrateThs = 0.0;
        for (MinerEntity<?> miner : site.getMiners()) {
            MinerStats stats = queryService.getLastResult(miner, MinerStats.DEFAULT);
            if (stats != null) {
                hashrateThs += stats.terahashPerSecond();
            }
        }

        double solarTodayKwh = 0.0;
        try {
            PVStatisticPerDay today = dailyStatisticService.getLiveDailyStatistic(site, "PV_DAILY", pvAccumulator);
            solarTodayKwh = Math.max(0, today.getProductionKwh());
        } catch (RuntimeException ignored) {
            // No live stat yet; report 0 for the day rather than failing the tick.
        }

        double solarTotalKwh = Math.max(0,
                site.getInverters().stream()
                        .map(inverter -> queryService.getLastResult(inverter, InverterDataDTO.DEFAULT))
                        .filter(java.util.Objects::nonNull)
                        .mapToDouble(InverterDataDTO::totalEnergyYieldWh)
                        .sum()) / 1000.0;

        return new TelemetryBatch(
                identity,
                version,
                site.getReferralCode(),
                true,
                round(site.getKwp()),
                minerCount,
                round(hashrateThs),
                null,
                round(solarTodayKwh),
                round(solarTotalKwh),
                locationGrid);
    }

    /** Mint + persist a stable random node identity on first use (separate from the PK). */
    private String ensureIdentity(PVSiteEntity site) {
        String identity = site.getNodeIdentity();
        if (identity == null || identity.isBlank()) {
            identity = UUID.randomUUID().toString();
            site.setNodeIdentity(identity);
            pvSiteRepository.save(site);
        }
        return identity;
    }

    private static double round(double v) {
        if (!Double.isFinite(v) || v < 0) {
            return 0.0;
        }
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static String trimEnd(String s) {
        if (s == null) {
            return "";
        }
        String t = s.strip();
        return t.endsWith("/") ? t.substring(0, t.length() - 1) : t;
    }

    /** Anonymized sample. Field names match the admin service's TelemetryBatchRequest. */
    public record TelemetryBatch(
            String uuid,
            String version,
            String referralCode,
            Boolean telemetryOptIn,
            Double pvKwp,
            Integer minerCount,
            Double hashrateTotal,
            Map<String, Double> hashrateByCoin,
            Double solarKwhToday,
            Double solarKwhTotal,
            String locationGrid) {
    }
}
