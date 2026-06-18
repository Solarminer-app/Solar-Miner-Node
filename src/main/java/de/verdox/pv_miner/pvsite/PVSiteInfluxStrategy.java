package de.verdox.pv_miner.pvsite;

import com.influxdb.Cancellable;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.query.FluxRecord;
import de.verdox.pv_miner.influx.InfluxEntityStrategy;
import de.verdox.pv_miner.influx.InfluxUtil;

import java.time.Instant;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class PVSiteInfluxStrategy implements InfluxEntityStrategy<PVSiteEntity, PVSiteDataDTO> {
    public static final String MEASUREMENT_KEY = "pv_site_data";

    public static final String PV_POWER_IN_KW = "PVPowerInKw";
    public static final String FEED_IN_POWER_IN_KW = "FeedInPowerInKw";
    public static final String GRID_CONSUMPTION_POWER = "GridConsumptionPower";
    public static final String BATTERY_STATE_OF_CHARGE = "BatteryStateOfCharge";
    public static final String BATTERY_CHARGE_POWER = "BatteryChargePower";
    public static final String BATTERY_DISCHARGE_POWER = "BatteryDischargePower";
    public static final String LOADS_POWER_IN_KW = "LoadsPowerInKw";
    public static final String MINER_POWER_IN_KW = "MinerPowerInKw";


    @Override
    public void writeToInflux(WriteApi writeApi, String bucket, String org, PVSiteEntity entity, PVSiteDataDTO dataToWrite, Instant timeOfData) {
        InfluxUtil.InfluxRecordBuilder influxRecordBuilder = new InfluxUtil.InfluxRecordBuilder(MEASUREMENT_KEY)
                .addTag(InfluxEntityStrategy.ENTITY_TAG, entity.getId().toString())
                .addField(PV_POWER_IN_KW, dataToWrite.getPVPowerInKw())
                .addField(FEED_IN_POWER_IN_KW, dataToWrite.getImportInKw())
                .addField(GRID_CONSUMPTION_POWER, dataToWrite.getExportInKw())
                .addField(BATTERY_STATE_OF_CHARGE, dataToWrite.getBatteryStateOfCharge())
                .addField(BATTERY_CHARGE_POWER, dataToWrite.getBatteryChargePower())
                .addField(BATTERY_DISCHARGE_POWER, dataToWrite.getBatteryDischargePower())
                .addField(LOADS_POWER_IN_KW, dataToWrite.getLoadsPowerInKw())
                .addField(MINER_POWER_IN_KW, dataToWrite.getTotalMinerPowerKw())
                .setTimestamp(timeOfData.toEpochMilli());
        writeApi.writeRecord(bucket, org, WritePrecision.MS, influxRecordBuilder.build());
    }

    @Override
    public void queryDataAsync(QueryApi queryApi, String bucket, PVSiteEntity entity, long timeStart, long timeEnd, Consumer<InfluxUtil.InfluxQueryBuilder> queryBuilder, BiConsumer<Cancellable, FluxRecord> consumer, Consumer<Throwable> onError, Runnable onComplete) {
        InfluxUtil.InfluxQueryBuilder builder = new InfluxUtil.InfluxQueryBuilder(bucket)
                .setMeasurement(MEASUREMENT_KEY)
                .setTimeRange(Instant.ofEpochMilli(timeStart), Instant.ofEpochMilli(timeEnd))
                .addFilter(InfluxEntityStrategy.ENTITY_TAG, entity.getId().toString());
        queryBuilder.accept(builder);
        queryApi.query(builder.build(), consumer, onError, onComplete);
    }
}
