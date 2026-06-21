package de.verdox.pv_miner.miner;

import com.influxdb.query.FluxRecord;
import de.verdox.pv_miner.dailystatistic.DailyStatisticAccumulator;
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
            case AsicMinerInfluxStrategy.POWER_USAGE_WATTS:
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
                "option task = {name: \"Downsample_Miner_%s\", every: 1d}\n\n" +
                        "from(bucket: \"%s\")\n" +
                        "  |> range(start: -2d)\n" +
                        "  |> filter(fn: (r) => r[\"_measurement\"] == \"%s\")\n" +
                        "  |> filter(fn: (r) => r[\"_field\"] == \"" + AsicMinerInfluxStrategy.POWER_USAGE_WATTS + "\")\n" +
                        "  |> map(fn: (r) => ({ r with _value: r._value / 1000.0 }))\n" +
                        "  |> aggregateWindow(every: 1d, fn: integral, column: \"_value\", timeSrc: \"_stop\", timeDst: \"_time\", createEmpty: false)\n" +
                        "  |> set(key: \"_measurement\", value: \"%s\")\n" +
                        "  |> to(bucket: \"%s\")",
                measurement, sourceBucket, measurement, getDownsampledMeasurementName(), targetBucket
        );
    }

    @Override
    public void mapFromDownsampledRecord(MinerStatisticsPerDay statisticObject, FluxRecord record) {
        Object value = record.getValueByKey(AsicMinerInfluxStrategy.POWER_USAGE_WATTS);
        if (value instanceof Number) {
            statisticObject.setTotalEnergyUsedInKwh(((Number) value).doubleValue());
        }
    }
}
