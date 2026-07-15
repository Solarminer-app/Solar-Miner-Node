package de.verdox.pv_miner.dashboard;

import com.influxdb.query.FluxRecord;
import de.verdox.pv_miner.dto.DashboardChartDataDto;
import de.verdox.pv_miner.influx.InfluxService;
import de.verdox.pv_miner.influx.InfluxUtil;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteInfluxStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static de.verdox.pv_miner.dto.DashboardPageDto.LivePowerSeriesDto;
import static de.verdox.pv_miner.dto.DashboardPageDto.SeriesPointDto;

@Service
public class DashboardChartQueryService {
    private static final List<String> LIVE_FIELDS = List.of(
            PVSiteInfluxStrategy.PV_POWER_IN_KW,
            PVSiteInfluxStrategy.FEED_IN_POWER_IN_KW,
            PVSiteInfluxStrategy.GRID_CONSUMPTION_POWER,
            PVSiteInfluxStrategy.LOADS_POWER_IN_KW,
            PVSiteInfluxStrategy.MINER_POWER_IN_KW
    );

    private final InfluxService influxService;
    private final Duration cacheDuration;
    private final ExecutorService queryExecutor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("dashboard-influx-query").daemon(true).factory()
    );
    private final Map<ChartQueryKey, CachedChartData> cache = new ConcurrentHashMap<>();
    private final Map<ChartQueryKey, CompletableFuture<DashboardChartDataDto>> activeLoads = new ConcurrentHashMap<>();

    public DashboardChartQueryService(
            InfluxService influxService,
            @Value("${influxdb.dashboard-chart-cache-duration:5m}") Duration cacheDuration
    ) {
        this.influxService = influxService;
        this.cacheDuration = cacheDuration.isNegative() || cacheDuration.isZero() ? Duration.ofMinutes(5) : cacheDuration;
    }

    public CompletableFuture<DashboardChartDataDto> load(
            PVSiteEntity site,
            long todayStartMillis,
            long todayEndMillis,
            long siteStartMillis
    ) {
        ChartQueryKey key = new ChartQueryKey(site.getId(), todayStartMillis, todayEndMillis, siteStartMillis);
        Instant now = Instant.now();
        CachedChartData cached = cache.get(key);
        if (cached != null && cached.loadedAt().plus(cacheDuration).isAfter(now)) {
            return CompletableFuture.completedFuture(cached.data());
        }

        CompletableFuture<DashboardChartDataDto> load = activeLoads.computeIfAbsent(
                key,
                ignored -> CompletableFuture.supplyAsync(
                        () -> queryChartData(site, todayStartMillis, todayEndMillis, siteStartMillis),
                        queryExecutor
                )
        );
        load.whenComplete((data, error) -> {
            if (error == null && data != null) {
                Instant loadedAt = Instant.now();
                cache.put(key, new CachedChartData(loadedAt, data));
                cache.entrySet().removeIf(entry ->
                        !entry.getKey().equals(key)
                                && entry.getValue().loadedAt().plus(cacheDuration).isBefore(loadedAt)
                );
            }
            activeLoads.remove(key, load);
        });
        return load;
    }

    private DashboardChartDataDto queryChartData(
            PVSiteEntity site,
            long todayStartMillis,
            long todayEndMillis,
            long siteStartMillis
    ) {
        List<FluxRecord> liveRecords = influxService.queryDataFromApi(
                site,
                Instant.ofEpochMilli(todayStartMillis),
                Instant.ofEpochMilli(todayEndMillis),
                query -> {
                    LIVE_FIELDS.forEach(query::addField);
                    query.setAggregation(InfluxUtil.AggregateOperation.MEAN, Duration.ofMinutes(1))
                            .setGroupByTime(false);
                }
        );

        List<FluxRecord> historyRecords = influxService.queryDataFromApi(
                site,
                Instant.ofEpochMilli(siteStartMillis),
                Instant.ofEpochMilli(todayEndMillis),
                query -> query
                        .addField(PVSiteInfluxStrategy.PV_POWER_IN_KW)
                        .setAggregation(InfluxUtil.AggregateOperation.MEDIAN, Duration.ofHours(1))
                        .setGroupByTime(false)
        );

        Map<String, TreeMap<Long, Double>> liveSeries = new ConcurrentHashMap<>();
        LIVE_FIELDS.forEach(field -> liveSeries.put(field, new TreeMap<>()));
        for (FluxRecord record : liveRecords) {
            if (record.getTime() == null || record.getField() == null || !(record.getValue() instanceof Number value)) {
                continue;
            }
            TreeMap<Long, Double> series = liveSeries.get(record.getField());
            if (series != null) {
                series.put(record.getTime().toEpochMilli(), value.doubleValue());
            }
        }

        TreeMap<Long, Double> history = new TreeMap<>();
        for (FluxRecord record : historyRecords) {
            if (record.getTime() != null && record.getValue() instanceof Number value) {
                history.put(record.getTime().toEpochMilli(), value.doubleValue());
            }
        }

        return new DashboardChartDataDto(
                new LivePowerSeriesDto(
                        toSeries(liveSeries.get(PVSiteInfluxStrategy.PV_POWER_IN_KW)),
                        toSeries(liveSeries.get(PVSiteInfluxStrategy.FEED_IN_POWER_IN_KW)),
                        toSeries(liveSeries.get(PVSiteInfluxStrategy.GRID_CONSUMPTION_POWER)),
                        toSeries(liveSeries.get(PVSiteInfluxStrategy.LOADS_POWER_IN_KW)),
                        toSeries(liveSeries.get(PVSiteInfluxStrategy.MINER_POWER_IN_KW))
                ),
                toSeries(history)
        );
    }

    private List<SeriesPointDto> toSeries(TreeMap<Long, Double> values) {
        List<SeriesPointDto> result = new ArrayList<>(values.size());
        values.forEach((timestamp, value) -> result.add(new SeriesPointDto(timestamp, value)));
        return List.copyOf(result);
    }

    @PreDestroy
    void close() {
        queryExecutor.shutdownNow();
    }

    private record ChartQueryKey(UUID siteId, long todayStartMillis, long todayEndMillis, long siteStartMillis) {
    }

    private record CachedChartData(Instant loadedAt, DashboardChartDataDto data) {
    }
}
