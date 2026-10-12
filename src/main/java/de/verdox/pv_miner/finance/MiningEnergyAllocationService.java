package de.verdox.pv_miner.finance;

import com.influxdb.query.FluxRecord;
import de.verdox.pv_miner.influx.InfluxService;
import de.verdox.pv_miner.pvsite.GridPowerNoiseFilter;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteInfluxStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Allocates grid, battery and local generation to mining before values are
 * collapsed into daily totals. This preserves the correlation between miner
 * load and the site's energy sources.
 */
@Service
public class MiningEnergyAllocationService {
    static final String QUALITY_MINUTE = "INTERVAL_1_MINUTE";
    static final String QUALITY_HOURLY = "INTERVAL_1_HOUR";
    static final String QUALITY_DAILY_ESTIMATE = "DAILY_ESTIMATE";
    private static final double MINIMUM_COVERAGE = 0.90;

    private final InfluxService influxService;
    private final Duration rawRetention;
    private final Clock clock;

    @Autowired
    public MiningEnergyAllocationService(InfluxService influxService,
                                         @Value("${influxdb.raw-retention:90d}") Duration rawRetention) {
        this(influxService, rawRetention, Clock.systemUTC());
    }

    MiningEnergyAllocationService(InfluxService influxService, Duration rawRetention, Clock clock) {
        this.influxService = Objects.requireNonNull(influxService);
        this.rawRetention = Objects.requireNonNull(rawRetention);
        this.clock = Objects.requireNonNull(clock);
    }

    public Map<LocalDate, Allocation> calculate(PVSiteEntity site, LocalDate from, LocalDate to, ZoneId zoneId) {
        Instant rangeStart = from.atStartOfDay(zoneId).toInstant();
        Instant requestedStop = to.plusDays(1).atStartOfDay(zoneId).toInstant();
        Instant now = clock.instant();
        Instant rangeStop = requestedStop.isAfter(now) ? now : requestedStop;
        if (!rangeStart.isBefore(rangeStop)) return Map.of();

        Map<LocalDate, Accumulator> hourly = read(
                influxService.getDownSampledInfluxBucket(),
                PVSiteInfluxStrategy.HOURLY_MEASUREMENT_KEY,
                site, rangeStart, rangeStop, Duration.ofHours(1), zoneId);

        Instant rawStart = rangeStart;
        if (!rawRetention.isZero()) {
            Instant retainedFrom = now.minus(rawRetention);
            if (retainedFrom.isAfter(rawStart)) rawStart = retainedFrom;
        }
        Map<LocalDate, Accumulator> minute = rawStart.isBefore(rangeStop)
                ? read(influxService.getInfluxBucket(), PVSiteInfluxStrategy.MEASUREMENT_KEY,
                site, rawStart, rangeStop, Duration.ofMinutes(1), zoneId)
                : Map.of();

        Map<LocalDate, Allocation> result = new LinkedHashMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            Instant dayStart = date.atStartOfDay(zoneId).toInstant();
            Instant dayStop = date.plusDays(1).atStartOfDay(zoneId).toInstant();
            Instant effectiveStart = dayStart.isBefore(rangeStart) ? rangeStart : dayStart;
            Instant effectiveStop = dayStop.isAfter(rangeStop) ? rangeStop : dayStop;
            if (!effectiveStart.isBefore(effectiveStop)) continue;

            Accumulator minuteDay = minute.get(date);
            double expectedMinutes = Math.max(1, Duration.between(effectiveStart, effectiveStop).toSeconds() / 60.0);
            if (minuteDay != null && minuteDay.samples / expectedMinutes >= MINIMUM_COVERAGE) {
                result.put(date, minuteDay.toAllocation(QUALITY_MINUTE, minuteDay.samples / expectedMinutes));
                continue;
            }

            Accumulator hourlyDay = hourly.get(date);
            double expectedHours = Math.max(1, Math.ceil(Duration.between(effectiveStart, effectiveStop).toMinutes() / 60.0));
            if (hourlyDay != null && hourlyDay.samples / expectedHours >= MINIMUM_COVERAGE) {
                result.put(date, hourlyDay.toAllocation(QUALITY_HOURLY, hourlyDay.samples / expectedHours));
            }
        }
        return result;
    }

    private Map<LocalDate, Accumulator> read(String bucket, String measurement, PVSiteEntity site,
                                             Instant from, Instant to, Duration interval, ZoneId zoneId) {
        String query = buildQuery(bucket, measurement, site.getId().toString(), from, to, interval);
        List<FluxRecord> records = influxService.queryRawFlux(query);
        Map<LocalDate, Accumulator> byDay = new HashMap<>();
        for (FluxRecord record : records) {
            Instant timestamp = record.getTime();
            if (timestamp == null) continue;
            Double loadKw = number(record, PVSiteInfluxStrategy.LOADS_POWER_IN_KW);
            Double minerKw = number(record, PVSiteInfluxStrategy.MINER_POWER_IN_KW);
            // Historical field names are misleading but wire-compatible:
            // FeedInPowerInKw stores import, GridConsumptionPower stores export.
            Double importKw = number(record, PVSiteInfluxStrategy.FEED_IN_POWER_IN_KW);
            if (loadKw == null || minerKw == null || importKw == null) continue;
            Double batteryDischargeKw = number(record, PVSiteInfluxStrategy.BATTERY_DISCHARGE_POWER);
            if (batteryDischargeKw == null) continue;
            PointAllocation point = allocatePoint(loadKw, minerKw, importKw, batteryDischargeKw);
            LocalDate date = timestamp.minusNanos(1).atZone(zoneId).toLocalDate();
            byDay.computeIfAbsent(date, ignored -> new Accumulator()).add(point, interval);
        }
        return byDay;
    }

    static PointAllocation allocatePoint(double loadKw, double minerKw, double importKw,
                                         double batteryDischargeKw) {
        double load = positive(loadKw);
        double miner = Math.min(positive(minerKw), load);
        if (miner <= 0 || load <= 0) return new PointAllocation(0, 0, 0, 0);

        double minerShare = clamp(miner / load, 0, 1);
        double gridAvailableToLoads = Math.min(GridPowerNoiseFilter.directionalKw(importKw), load);
        double grid = Math.min(miner, gridAvailableToLoads * minerShare);
        double remaining = Math.max(0, miner - grid);
        double batteryAvailableToLoads = Math.min(positive(batteryDischargeKw), Math.max(0, load - gridAvailableToLoads));
        double battery = Math.min(remaining, batteryAvailableToLoads * minerShare);
        double localGeneration = Math.max(0, miner - grid - battery);
        return new PointAllocation(miner, grid, battery, localGeneration);
    }

    private static String buildQuery(String bucket, String measurement, String entityId,
                                     Instant from, Instant to, Duration interval) {
        boolean rawSamples = interval.equals(Duration.ofMinutes(1));
        return "from(bucket: \"" + escapeFlux(bucket) + "\")"
                + " |> range(start: " + from + ", stop: " + to + ")"
                + " |> filter(fn: (r) => r[\"_measurement\"] == \"" + escapeFlux(measurement) + "\")"
                + " |> filter(fn: (r) => r[\"entity\"] == \"" + escapeFlux(entityId) + "\")"
                + " |> filter(fn: (r) => r[\"_field\"] == \"" + PVSiteInfluxStrategy.LOADS_POWER_IN_KW
                + "\" or r[\"_field\"] == \"" + PVSiteInfluxStrategy.MINER_POWER_IN_KW
                + "\" or r[\"_field\"] == \"" + PVSiteInfluxStrategy.FEED_IN_POWER_IN_KW
                + "\" or r[\"_field\"] == \"" + PVSiteInfluxStrategy.BATTERY_DISCHARGE_POWER + "\")"
                + (rawSamples ? " |> aggregateWindow(every: 1m, fn: mean, createEmpty: false)" : "")
                + " |> group(columns: [\"entity\"])"
                + " |> pivot(rowKey: [\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\")";
    }

    private static Double number(FluxRecord record, String key) {
        Object value = record.getValueByKey(key);
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private static String escapeFlux(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static double positive(double value) {
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record Allocation(double miningKwh, double gridKwh, double batteryKwh, double localGenerationKwh,
                             String quality, double coverage) {
    }

    record PointAllocation(double miningKw, double gridKw, double batteryKw, double localGenerationKw) {
    }

    private static final class Accumulator {
        private double miningKwh;
        private double gridKwh;
        private double batteryKwh;
        private double localGenerationKwh;
        private int samples;

        void add(PointAllocation point, Duration interval) {
            double hours = interval.toSeconds() / 3600.0;
            miningKwh += point.miningKw() * hours;
            gridKwh += point.gridKw() * hours;
            batteryKwh += point.batteryKw() * hours;
            localGenerationKwh += point.localGenerationKw() * hours;
            samples++;
        }

        Allocation toAllocation(String quality, double coverage) {
            return new Allocation(miningKwh, gridKwh, batteryKwh, localGenerationKwh,
                    quality, clamp(coverage, 0, 1));
        }
    }
}
