package de.verdox.pv_miner.miner;

import com.influxdb.Cancellable;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.query.FluxRecord;
import de.verdox.pv_miner.influx.InfluxEntityStrategy;
import de.verdox.pv_miner.influx.InfluxUtil;
import de.verdox.pv_miner.miner.data.MinerStats;

import java.time.Instant;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class MinerInfluxStrategy implements InfluxEntityStrategy<MinerEntity<?>, MinerStats> {

    public static final String MEASUREMENT = "miner_data";
    public static final String POWER_USAGE_WATTS = "powerUsageWatts";
    public static final String TERA_HASH_PER_SECOND = "teraHashPerSecond";
    public static final String POWER_TARGET_WATTS = "powerTargetWatts";
    public static final String TEMPERATURE_CHIP_CELSIUS = "temperatureChipCelsius";

    @Override
    public void writeToInflux(WriteApi writeApi, String bucket, String org, MinerEntity<?> entity, MinerStats dataToWrite, Instant timeOfData) {
        InfluxUtil.InfluxRecordBuilder influxRecordBuilder = new InfluxUtil.InfluxRecordBuilder(MEASUREMENT)
                .addTag(InfluxEntityStrategy.ENTITY_TAG, entity.getId().toString())
                .addField(POWER_USAGE_WATTS, dataToWrite.approximatedPowerUsageWatts())
                .addField(TERA_HASH_PER_SECOND, dataToWrite.terahashPerSecond())
                .addField(POWER_TARGET_WATTS, dataToWrite.powerTargetWatts())
                .addField(TEMPERATURE_CHIP_CELSIUS, dataToWrite.temperatureCelsius())
                .setTimestamp(timeOfData.toEpochMilli());
        writeApi.writeRecord(bucket, org, WritePrecision.MS, influxRecordBuilder.build());
    }

    @Override
    public void queryDataAsync(QueryApi queryApi, String bucket, MinerEntity<?> entity, long timeStart, long timeEnd, Consumer<InfluxUtil.InfluxQueryBuilder> queryBuilder, BiConsumer<Cancellable, FluxRecord> consumer, Consumer<Throwable> onError, Runnable onComplete) {
        InfluxUtil.InfluxQueryBuilder builder = new InfluxUtil.InfluxQueryBuilder(bucket)
                .setMeasurement(MEASUREMENT)
                .setTimeRange(Instant.ofEpochMilli(timeStart), Instant.ofEpochMilli(timeEnd))
                .addFilter(InfluxEntityStrategy.ENTITY_TAG, entity.getId().toString());
        queryBuilder.accept(builder);
        queryApi.query(builder.build(), consumer, onError, onComplete);
    }
}
