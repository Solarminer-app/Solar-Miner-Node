package de.verdox.pv_miner.influx;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InfluxUtil {
    public static class InfluxRecordBuilder {
        private final String measurement;
        private final Map<String, String> tags = new HashMap<>();
        private final Map<String, Object> fields = new HashMap<>();
        private long timestamp;

        public InfluxRecordBuilder(String measurement) {
            this.measurement = measurement;
        }

        public InfluxRecordBuilder addTag(String key, String value) {
            tags.put(key, value);
            return this;
        }

        public InfluxRecordBuilder addField(String key, String value) {
            fields.put(key, value);
            return this;
        }

        public InfluxRecordBuilder addField(String key, double value) {
            fields.put(key, value);
            return this;
        }

        public InfluxRecordBuilder setTimestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public String build() {
            StringBuilder sb = new StringBuilder(measurement);
            if (!tags.isEmpty()) {
                sb.append(",");
                tags.forEach((k, v) -> sb.append(k).append("=").append(v).append(","));
                sb.setLength(sb.length() - 1); // Remove last comma
            }
            sb.append(" ");
            fields.forEach((k, v) -> sb.append(k).append("=").append(v).append(","));
            sb.setLength(sb.length() - 1); // Remove last comma
            sb.append(" ").append(timestamp);
            return sb.toString();
        }
    }

    public static class InfluxQueryBuilder {
        private final String bucket;
        private String measurement;
        private Instant startTime;
        private Instant endTime;
        private final Map<String, String> filters = new HashMap<>();
        private final List<String> fields = new ArrayList<>();
        private AggregateOperation aggregateOperation;
        private Duration aggregationInterval;
        private Duration aggregationOffset;

        public InfluxQueryBuilder(String bucket) {
            this.bucket = bucket;
        }

        public InfluxQueryBuilder setMeasurement(String measurement) {
            this.measurement = measurement;
            return this;
        }

        public InfluxQueryBuilder setTimeRange(Instant startTime, Instant endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
            return this;
        }

        public InfluxQueryBuilder addFilter(String key, String value) {
            filters.put(key, value);
            return this;
        }

        public InfluxQueryBuilder addField(String field) {
            fields.add(field);
            return this;
        }

        public InfluxQueryBuilder setAggregation(AggregateOperation aggregateOperation, Duration interval) {
            this.aggregateOperation = aggregateOperation;
            this.aggregationInterval = interval;
            return this;
        }

        public InfluxQueryBuilder setAggregation(AggregateOperation aggregateOperation, Duration interval, Duration aggregationOffset) {
            this.aggregateOperation = aggregateOperation;
            this.aggregationInterval = interval;
            this.aggregationOffset = aggregationOffset;
            return this;
        }

        public String build() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("from(bucket: \"%s\") |> range(start: %s, stop: %s)",
                    bucket, startTime.toString(), endTime.toString()));

            if (measurement != null) {
                sb.append(String.format(" |> filter(fn: (r) => r[\"_measurement\"] == \"%s\")", measurement));
            }

            filters.forEach((k, v) -> sb.append(String.format(" |> filter(fn: (r) => r[\"%s\"] == \"%s\")", k, v)));

            if (!fields.isEmpty()) {
                sb.append(" |> filter(fn: (r) => " +
                        String.join(" or ", fields.stream().map(f -> "r[\"_field\"] == \"" + f + "\"").toList()) + ")");
            }

            if (aggregateOperation != null && aggregationInterval != null) {
                if (aggregationOffset != null) {
                    sb.append(String.format(" |> aggregateWindow(every: %s, offset: %s, fn: %s, createEmpty: false)",
                            formatDuration(aggregationInterval), formatDuration(aggregationOffset), aggregateOperation.getFunction()));
                }
                else {
                    sb.append(String.format(" |> aggregateWindow(every: %s, fn: %s, createEmpty: false)", formatDuration(aggregationInterval), aggregateOperation.getFunction()));
                }
            }

            sb.append(" |> group(columns: [\"_time\"])");
            return sb.toString();
        }
    }

    public enum AggregateOperation {
        // Average function
        MEAN("mean"),
        // Median function
        MEDIAN("median"),
        // Mode function (most appearing value)
        MODE("median"),
        // Sum function
        SUM("sum"),
        // Count function
        COUNT("count"),
        // Min function
        MIN("min"),
        // Max function
        MAX("max"),
        // First function
        FIRST("first"),
        // Last function
        LAST("last"),
        INTEGRAL("integral"),
        // Standard deviation function
        STANDARD_DEVIATION("stddev"),
        // Variance function
        VARIANCE("variance"),
        ;
        private final String function;

        AggregateOperation(String function) {
            this.function = function;
        }

        public String getFunction() {
            return function;
        }
    }

    private static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds % 604800 == 0) return (seconds / 604800) + "w"; // Wochen
        if (seconds % 86400 == 0) return (seconds / 86400) + "d";   // Tage
        if (seconds % 3600 == 0) return (seconds / 3600) + "h";    // Stunden
        if (seconds % 60 == 0) return (seconds / 60) + "m";        // Minuten
        return seconds + "s"; // Sekunden
    }
}
