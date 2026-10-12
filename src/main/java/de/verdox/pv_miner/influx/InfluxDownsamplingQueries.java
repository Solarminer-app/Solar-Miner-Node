package de.verdox.pv_miner.influx;

import de.verdox.pv_miner.pvsite.PVSiteInfluxStrategy;
import de.verdox.pv_miner.pvsite.GridPowerNoiseFilter;

import java.util.List;
import java.util.stream.Collectors;

final class InfluxDownsamplingQueries {
    static final List<String> PV_POWER_FIELDS = List.of(
            PVSiteInfluxStrategy.PV_POWER_IN_KW,
            PVSiteInfluxStrategy.FEED_IN_POWER_IN_KW,
            PVSiteInfluxStrategy.GRID_CONSUMPTION_POWER,
            PVSiteInfluxStrategy.LOADS_POWER_IN_KW,
            PVSiteInfluxStrategy.MINER_POWER_IN_KW,
            PVSiteInfluxStrategy.BATTERY_DISCHARGE_POWER
    );

    private InfluxDownsamplingQueries() {
    }

    static String generatePvHourlyTask(String sourceBucket, String targetBucket) {
        return String.format(
                """
                        import "date"

                        option task = {name: "Downsample_PV_Hourly", every: 1h, offset: 5m}

                        stop = date.truncate(t: now(), unit: 1h)
                        start = date.sub(d: 2h, from: stop)

                        from(bucket: "%s")
                          |> range(start: start, stop: stop)
                          |> filter(fn: (r) => r["_measurement"] == "%s")
                          |> filter(fn: (r) => %s)
                          %s
                          |> group(columns: ["_measurement", "_field", "entity"])
                          |> aggregateWindow(every: 1h, fn: mean, timeSrc: "_stop", timeDst: "_time", createEmpty: false)
                          |> set(key: "_measurement", value: "%s")
                          |> to(bucket: "%s")""",
                sourceBucket,
                PVSiteInfluxStrategy.MEASUREMENT_KEY,
                fieldFilter(PV_POWER_FIELDS),
                gridNoiseFilter(),
                PVSiteInfluxStrategy.HOURLY_MEASUREMENT_KEY,
                targetBucket
        );
    }

    static String fieldFilter(List<String> fields) {
        return fields.stream()
                .map(field -> "r[\"_field\"] == \"" + field + "\"")
                .collect(Collectors.joining(" or "));
    }

    static String gridNoiseFilter() {
        return "|> map(fn: (r) => ({r with _value: if (r[\"_field\"] == \""
                + PVSiteInfluxStrategy.FEED_IN_POWER_IN_KW + "\" or r[\"_field\"] == \""
                + PVSiteInfluxStrategy.GRID_CONSUMPTION_POWER + "\") and r[\"_value\"] >= -"
                + GridPowerNoiseFilter.DEADBAND_KW + " and r[\"_value\"] <= "
                + GridPowerNoiseFilter.DEADBAND_KW + " then 0.0 else r[\"_value\"]}))";
    }
}
