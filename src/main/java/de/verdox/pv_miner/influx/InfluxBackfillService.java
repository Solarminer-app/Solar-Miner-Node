package de.verdox.pv_miner.influx;

import com.influxdb.client.BucketsApi;
import com.influxdb.client.domain.Bucket;
import com.influxdb.client.domain.BucketRetentionRules;
import com.influxdb.query.FluxRecord;
import de.verdox.pv_miner.miner.MinerInfluxStrategy;
import de.verdox.pv_miner.miner.MinerStatisticsAccumulator;
import de.verdox.pv_miner.pvsite.PVSiteInfluxStrategy;
import de.verdox.pv_miner.pvsite.PVStatisticsAccumulator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class InfluxBackfillService {
    private static final Logger LOGGER = Logger.getLogger(InfluxBackfillService.class.getSimpleName());

    private final InfluxService influxService;
    private final Duration rawRetention;
    private final Clock clock;
    private final AtomicBoolean auditRunning = new AtomicBoolean();

    @Autowired
    public InfluxBackfillService(
            InfluxService influxService,
            @Value("${influxdb.raw-retention:90d}") Duration rawRetention) {
        this(influxService, rawRetention, Clock.systemUTC());
    }

    InfluxBackfillService(InfluxService influxService, Duration rawRetention, Clock clock) {
        this.influxService = Objects.requireNonNull(influxService);
        this.rawRetention = Objects.requireNonNull(rawRetention);
        this.clock = Objects.requireNonNull(clock);
        if (rawRetention.isNegative()) {
            throw new IllegalArgumentException("Influx raw retention must not be negative");
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void checkForRequiredBackfills() {
        runAuditAsync();
    }

    @Scheduled(cron = "${influxdb.downsampling-audit-cron:0 30 0 * * *}", zone = "UTC")
    public void auditDownsamplingDaily() {
        runAuditAsync();
    }

    private void runAuditAsync() {
        if (!auditRunning.compareAndSet(false, true)) {
            LOGGER.info("InfluxDB downsampling audit is already running.");
            return;
        }
        CompletableFuture.runAsync(this::auditDownsamplingAndRetention)
                .whenComplete((ignored, error) -> {
                    auditRunning.set(false);
                    if (error != null) {
                        LOGGER.log(Level.SEVERE, "InfluxDB downsampling audit failed", error);
                    }
                });
    }

    boolean auditDownsamplingAndRetention() {
        LOGGER.info("Checking InfluxDB downsampling coverage...");
        try {
            if (!ensureDownsampleBucketExistsWithInfiniteRetention()) {
                return false;
            }

            Instant now = clock.instant();
            Instant completedDayStop = now.truncatedTo(ChronoUnit.DAYS);
            Instant completedHourStop = now.truncatedTo(ChronoUnit.HOURS);

            List<BackfillDefinition> definitions = List.of(
                    new BackfillDefinition(
                            "PV daily",
                            PVSiteInfluxStrategy.MEASUREMENT_KEY,
                            new PVStatisticsAccumulator().getDownsampledMeasurementName(),
                            InfluxDownsamplingQueries.PV_POWER_FIELDS,
                            Duration.ofDays(1),
                            BackfillKind.PV_DAILY
                    ),
                    new BackfillDefinition(
                            "miner daily",
                            MinerInfluxStrategy.MEASUREMENT,
                            new MinerStatisticsAccumulator().getDownsampledMeasurementName(),
                            List.of(MinerInfluxStrategy.POWER_USAGE_WATTS),
                            Duration.ofDays(1),
                            BackfillKind.MINER_DAILY
                    ),
                    new BackfillDefinition(
                            "PV hourly",
                            PVSiteInfluxStrategy.MEASUREMENT_KEY,
                            PVSiteInfluxStrategy.HOURLY_MEASUREMENT_KEY,
                            InfluxDownsamplingQueries.PV_POWER_FIELDS,
                            Duration.ofHours(1),
                            BackfillKind.PV_HOURLY
                    )
            );

            boolean complete = true;
            for (BackfillDefinition definition : definitions) {
                Instant stop = definition.window().equals(Duration.ofDays(1))
                        ? completedDayStop
                        : completedHourStop;
                Instant start = definition.kind() == BackfillKind.PV_HOURLY
                        ? stop.minus(rawRetention.isZero() ? Duration.ofDays(7) : rawRetention)
                        : Instant.EPOCH;
                complete &= backfillMissingWindows(definition, start, stop);
            }

            if (!complete) {
                LOGGER.warning("Raw Influx retention was not changed because downsampling coverage is incomplete.");
                return false;
            }

            boolean retentionUpdated = ensureRawBucketRetention();
            if (retentionUpdated) {
                LOGGER.info("InfluxDB downsampling coverage and raw retention are healthy.");
            }
            return retentionUpdated;
        } catch (Throwable error) {
            LOGGER.log(Level.SEVERE, "Could not audit InfluxDB downsampling coverage", error);
            return false;
        }
    }

    private boolean backfillMissingWindows(
            BackfillDefinition definition,
            Instant coverageStart,
            Instant completedStop) {
        Set<CoverageKey> sourceCoverage = loadCoverage(
                influxService.getInfluxBucket(),
                definition.sourceMeasurement(),
                definition.fields(),
                definition.window(),
                coverageStart,
                completedStop,
                true
        );
        if (sourceCoverage.isEmpty()) {
            LOGGER.info("No completed raw windows found for " + definition.label() + ".");
            return true;
        }

        Set<CoverageKey> targetCoverage = loadCoverage(
                influxService.getDownSampledInfluxBucket(),
                definition.targetMeasurement(),
                definition.fields(),
                definition.window(),
                coverageStart,
                completedStop.plusMillis(1),
                false
        );

        Set<CoverageKey> missing = new HashSet<>(sourceCoverage);
        missing.removeAll(targetCoverage);
        if (missing.isEmpty()) {
            LOGGER.info("Downsampling coverage is complete for " + definition.label() + ".");
            return true;
        }

        TreeSet<LocalDate> daysToBackfill = new TreeSet<>();
        for (CoverageKey key : missing) {
            daysToBackfill.add(key.windowEnd().minusNanos(1).atZone(ZoneOffset.UTC).toLocalDate());
        }
        LOGGER.info("Backfilling " + daysToBackfill.size() + " day(s) for " + definition.label() + ".");

        for (LocalDate day : daysToBackfill) {
            if (!executeBackfill(definition.kind(), day)) {
                LOGGER.warning("Backfill failed for " + definition.label() + " on " + day + ".");
                return false;
            }
        }

        Set<CoverageKey> refreshedTargetCoverage = loadCoverage(
                influxService.getDownSampledInfluxBucket(),
                definition.targetMeasurement(),
                definition.fields(),
                definition.window(),
                coverageStart,
                completedStop.plusMillis(1),
                false
        );
        if (!refreshedTargetCoverage.containsAll(sourceCoverage)) {
            LOGGER.warning("Downsampling coverage is still incomplete for " + definition.label() + ".");
            return false;
        }
        return true;
    }

    private Set<CoverageKey> loadCoverage(
            String bucket,
            String measurement,
            List<String> fields,
            Duration window,
            Instant start,
            Instant stop,
            boolean aggregateRawData) {
        StringBuilder query = new StringBuilder(String.format(
                "from(bucket: \"%s\") |> range(start: %s, stop: %s)" +
                        " |> filter(fn: (r) => r[\"_measurement\"] == \"%s\")" +
                        " |> filter(fn: (r) => %s)",
                bucket, start, stop, measurement, InfluxDownsamplingQueries.fieldFilter(fields)
        ));
        if (aggregateRawData) {
            query.append(" |> group(columns: [\"_measurement\", \"_field\", \"entity\"])")
                    .append(String.format(
                            " |> aggregateWindow(every: %s, fn: count, timeSrc: \"_stop\", timeDst: \"_time\", createEmpty: false)",
                            formatDuration(window)
                    ));
        }
        query.append(" |> keep(columns: [\"_time\", \"_field\", \"entity\"])");

        Set<CoverageKey> result = new HashSet<>();
        for (FluxRecord record : influxService.queryRawFluxOrThrow(query.toString())) {
            Object entity = record.getValueByKey(InfluxEntityStrategy.ENTITY_TAG);
            if (record.getTime() != null && record.getField() != null && entity != null) {
                result.add(new CoverageKey(record.getTime(), entity.toString(), record.getField()));
            }
        }
        return result;
    }

    private boolean executeBackfill(BackfillKind kind, LocalDate day) {
        Instant start = day.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant stop = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return switch (kind) {
            case PV_DAILY -> influxService.executeRawFluxQuery(pvDailyBackfill(start, stop));
            case MINER_DAILY -> influxService.executeRawFluxQuery(minerDailyBackfill(start, stop));
            case PV_HOURLY -> influxService.executeRawFluxQuery(pvHourlyBackfill(start, stop));
        };
    }

    private String pvDailyBackfill(Instant start, Instant stop) {
        String targetMeasurement = new PVStatisticsAccumulator().getDownsampledMeasurementName();
        return String.format(
                """
                        pv_all = from(bucket: "%s")
                          |> range(start: %s, stop: %s)
                          |> filter(fn: (r) => r["_measurement"] == "%s")
                          |> filter(fn: (r) => %s)

                        pv_entity_arr = pv_all
                          |> keep(columns: ["entity"])
                          |> limit(n: 1)
                          |> findColumn(fn: (key) => true, column: "entity")

                        pv_entity = if length(arr: pv_entity_arr) > 0 then pv_entity_arr[0] else "unknown"

                        miner_pv_count = pv_all
                          |> filter(fn: (r) => r["_field"] == "%s")
                          |> count()
                          |> findColumn(fn: (key) => true, column: "_value")

                        has_miner_in_pv = if length(arr: miner_pv_count) > 0 and miner_pv_count[0] > 0 then true else false

                        pv_aggregated = pv_all
                          |> group(columns: ["_measurement", "_field", "entity"])
                          |> aggregateWindow(every: 1d, fn: (column, tables=<-) => tables |> integral(unit: 1h), timeSrc: "_stop", timeDst: "_time", createEmpty: false)

                        miner_fallback = from(bucket: "%s")
                          |> range(start: %s, stop: %s)
                          |> filter(fn: (r) => r["_measurement"] == "%s")
                          |> filter(fn: (r) => r["_field"] == "%s")
                          |> filter(fn: (r) => not has_miner_in_pv)
                          |> group(columns: ["_measurement", "_field", "entity"])
                          |> map(fn: (r) => ({r with _value: r._value / 1000.0}))
                          |> aggregateWindow(every: 1d, fn: (column, tables=<-) => tables |> integral(unit: 1h), timeSrc: "_stop", timeDst: "_time", createEmpty: false)
                          |> group(columns: ["_time"])
                          |> sum(column: "_value")
                          |> map(fn: (r) => ({r with _field: "%s", _measurement: "%s", entity: pv_entity}))

                        union(tables: [pv_aggregated, miner_fallback])
                          |> group(columns: ["_measurement", "_field", "entity"])
                          |> set(key: "_measurement", value: "%s")
                          |> to(bucket: "%s")""",
                influxService.getInfluxBucket(), start, stop, PVSiteInfluxStrategy.MEASUREMENT_KEY,
                InfluxDownsamplingQueries.fieldFilter(InfluxDownsamplingQueries.PV_POWER_FIELDS),
                PVSiteInfluxStrategy.MINER_POWER_IN_KW,
                influxService.getInfluxBucket(), start, stop, MinerInfluxStrategy.MEASUREMENT,
                MinerInfluxStrategy.POWER_USAGE_WATTS,
                PVSiteInfluxStrategy.MINER_POWER_IN_KW, targetMeasurement,
                targetMeasurement, influxService.getDownSampledInfluxBucket()
        );
    }

    private String minerDailyBackfill(Instant start, Instant stop) {
        return String.format(
                """
                        from(bucket: "%s")
                          |> range(start: %s, stop: %s)
                          |> filter(fn: (r) => r["_measurement"] == "%s")
                          |> filter(fn: (r) => r["_field"] == "%s")
                          |> group(columns: ["_measurement", "_field", "entity"])
                          |> map(fn: (r) => ({r with _value: r._value / 1000.0}))
                          |> aggregateWindow(every: 1d, fn: (column, tables=<-) => tables |> integral(unit: 1h), timeSrc: "_stop", timeDst: "_time", createEmpty: false)
                          |> set(key: "_measurement", value: "%s")
                          |> to(bucket: "%s")""",
                influxService.getInfluxBucket(), start, stop, MinerInfluxStrategy.MEASUREMENT,
                MinerInfluxStrategy.POWER_USAGE_WATTS,
                new MinerStatisticsAccumulator().getDownsampledMeasurementName(),
                influxService.getDownSampledInfluxBucket()
        );
    }

    private String pvHourlyBackfill(Instant start, Instant stop) {
        return String.format(
                """
                        from(bucket: "%s")
                          |> range(start: %s, stop: %s)
                          |> filter(fn: (r) => r["_measurement"] == "%s")
                          |> filter(fn: (r) => %s)
                          |> group(columns: ["_measurement", "_field", "entity"])
                          |> aggregateWindow(every: 1h, fn: mean, timeSrc: "_stop", timeDst: "_time", createEmpty: false)
                          |> set(key: "_measurement", value: "%s")
                          |> to(bucket: "%s")""",
                influxService.getInfluxBucket(), start, stop, PVSiteInfluxStrategy.MEASUREMENT_KEY,
                InfluxDownsamplingQueries.fieldFilter(InfluxDownsamplingQueries.PV_POWER_FIELDS),
                PVSiteInfluxStrategy.HOURLY_MEASUREMENT_KEY,
                influxService.getDownSampledInfluxBucket()
        );
    }

    private boolean ensureDownsampleBucketExistsWithInfiniteRetention() {
        try {
            var client = influxService.getInfluxDBClient();
            BucketsApi bucketsApi = client.getBucketsApi();
            Bucket bucket = bucketsApi.findBucketByName(influxService.getDownSampledInfluxBucket());
            if (bucket == null) {
                String orgId = client.getOrganizationsApi().findOrganizations().stream()
                        .filter(org -> org.getName().equals(influxService.getInfluxOrg()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "Influx organization '" + influxService.getInfluxOrg() + "' not found"
                        ))
                        .getId();
                bucketsApi.createBucket(influxService.getDownSampledInfluxBucket(), orgId);
                LOGGER.info("Created infinite-retention bucket '" + influxService.getDownSampledInfluxBucket() + "'.");
                return true;
            }

            boolean finiteRetention = bucket.getRetentionRules() != null && bucket.getRetentionRules().stream()
                    .anyMatch(rule -> rule.getEverySeconds() != null && rule.getEverySeconds() > 0);
            if (finiteRetention) {
                bucket.setRetentionRules(List.of());
                bucketsApi.updateBucket(bucket);
                LOGGER.info("Changed downsample bucket to infinite retention.");
            }
            return true;
        } catch (Exception error) {
            LOGGER.log(Level.SEVERE, "Could not prepare downsample bucket", error);
            return false;
        }
    }

    private boolean ensureRawBucketRetention() {
        try {
            BucketsApi bucketsApi = influxService.getInfluxDBClient().getBucketsApi();
            Bucket bucket = bucketsApi.findBucketByName(influxService.getInfluxBucket());
            if (bucket == null) {
                throw new IllegalStateException("Raw Influx bucket '" + influxService.getInfluxBucket() + "' not found");
            }

            int desiredSeconds = Math.toIntExact(rawRetention.toSeconds());
            int currentSeconds = 0;
            if (bucket.getRetentionRules() != null && !bucket.getRetentionRules().isEmpty()) {
                Integer configured = bucket.getRetentionRules().getFirst().getEverySeconds();
                currentSeconds = configured == null ? 0 : configured;
            }
            if (currentSeconds == desiredSeconds) {
                return true;
            }

            if (desiredSeconds == 0) {
                bucket.setRetentionRules(List.of());
            } else {
                bucket.setRetentionRules(List.of(new BucketRetentionRules().everySeconds(desiredSeconds)));
            }
            bucketsApi.updateBucket(bucket);
            LOGGER.info("Updated raw Influx retention to " + rawRetention + ".");
            return true;
        } catch (Exception error) {
            LOGGER.log(Level.SEVERE, "Could not update raw Influx retention", error);
            return false;
        }
    }

    private static String formatDuration(Duration duration) {
        if (duration.equals(Duration.ofDays(1))) return "1d";
        if (duration.equals(Duration.ofHours(1))) return "1h";
        throw new IllegalArgumentException("Unsupported coverage window: " + duration);
    }

    private enum BackfillKind {
        PV_DAILY,
        MINER_DAILY,
        PV_HOURLY
    }

    private record BackfillDefinition(
            String label,
            String sourceMeasurement,
            String targetMeasurement,
            List<String> fields,
            Duration window,
            BackfillKind kind) {
    }

    private record CoverageKey(Instant windowEnd, String entity, String field) {
    }
}
