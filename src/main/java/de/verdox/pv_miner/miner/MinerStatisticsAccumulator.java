package de.verdox.pv_miner.miner;

import com.influxdb.query.FluxRecord;
import de.verdox.pv_miner.statistic.daily.DailyStatisticAccumulator;
import de.verdox.pv_miner.miner.data.MinerStats;

public class MinerStatisticsAccumulator implements DailyStatisticAccumulator<MinerStatisticsPerDay, MinerStats> {
    @Override
    public MinerStatisticsPerDay createEmptyInstance() {
        MinerStatisticsPerDay stats = new MinerStatisticsPerDay();
        stats.setTotalEnergyUsedInKwh(0.0);
        return stats;
    }

    @Override
    public void accumulate(MinerStatisticsPerDay currentCache, MinerStats liveData, double timeWindowInHours) {

    }

    @Override
    public void mapFromFluxRecord(MinerStatisticsPerDay statisticObject, FluxRecord record) {
        String fieldName = record.getField();
        Object value = record.getValue();
        if (value == null) return;

        switch (fieldName) {
            case MinerInfluxStrategy.POWER_USAGE_WATTS:
                statisticObject.setTotalEnergyUsedInKwh(((Number) value).doubleValue() / 1000d / 3600d);
                break;
        }
    }

    @Override
    public boolean supportsDownsampling() {
        return true;
    }

    @Override
    public String getDownsampledMeasurementName() {
        return "miner_daily_summary";
    }

    @Override
    public String generateDownsamplingTaskQuery(String sourceBucket, String targetBucket, String measurement) {
        return String.format(
                "import \"date\"\n\n" +
                        "option task = {name: \"Downsample_Miner_%s\", every: 1d, offset: 15m}\n\n" +
                        "stop = date.truncate(t: now(), unit: 1d)\n" +
                        "start = date.sub(d: 2d, from: stop)\n\n" +
                        "from(bucket: \"%s\")\n" +
                        "  |> range(start: start, stop: stop)\n" +
                        "  |> filter(fn: (r) => r[\"_measurement\"] == \"%s\")\n" +
                        "  |> filter(fn: (r) => r[\"_field\"] == \"" + MinerInfluxStrategy.POWER_USAGE_WATTS + "\")\n" +
                        "  |> group(columns: [\"_measurement\", \"_field\", \"entity\"])\n" + // <-- FIX: Gruppierung wie im Backfill!
                        "  |> map(fn: (r) => ({ r with _value: r._value / 1000.0 }))\n" +
                        "  |> aggregateWindow(every: 1d, fn: (column, tables=<-) => tables |> integral(unit: 1h), timeSrc: \"_stop\", timeDst: \"_time\", createEmpty: false)\n" +
                        "  |> set(key: \"_measurement\", value: \"%s\")\n" +
                        "  |> to(bucket: \"%s\")",
                measurement, sourceBucket, measurement, getDownsampledMeasurementName(), targetBucket
        );
    }

    @Override
    public void mapFromDownsampledRecord(MinerStatisticsPerDay statisticObject, FluxRecord record) {
        Object value = record.getValueByKey(MinerInfluxStrategy.POWER_USAGE_WATTS);
        if (value instanceof Number) {
            statisticObject.setTotalEnergyUsedInKwh(((Number) value).doubleValue());
        }
    }
}
