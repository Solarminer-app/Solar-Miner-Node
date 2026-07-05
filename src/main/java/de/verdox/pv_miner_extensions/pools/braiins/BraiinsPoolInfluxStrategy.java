package de.verdox.pv_miner_extensions.pools.braiins;

import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import de.verdox.pv_miner.influx.InfluxEntityStrategy;
import de.verdox.pv_miner.miningpool.MiningPoolInfluxStrategy;
import de.verdox.pv_miner.influx.InfluxUtil;
import de.verdox.pv_miner.util.TimeUtil;

import java.time.Instant;

public class BraiinsPoolInfluxStrategy implements MiningPoolInfluxStrategy<BraiinsPoolEntity.BraiinsPoolData, BraiinsPoolEntity> {
    @Override
    public void writeToInflux(WriteApi writeApi, String bucket, String org, BraiinsPoolEntity entity, BraiinsPoolEntity.BraiinsPoolData dataToWrite, Instant timeOfData) {
        long unixDay = TimeUtil.getStartOfDayInUtc(timeOfData.toEpochMilli());

        if(dataToWrite.payPerShare() > 0) {
            InfluxUtil.InfluxRecordBuilder influxRecordBuilder = new InfluxUtil.InfluxRecordBuilder("pool_data")
                    .addTag(InfluxEntityStrategy.ENTITY_TAG, entity.getId().toString())
                    .addField("payPerShare", dataToWrite.payPerShare())
                    .setTimestamp(unixDay);
            writeApi.writeRecord(bucket, org, WritePrecision.MS, influxRecordBuilder.build());
        }

        for (BraiinsPoolEntity.BraiinsPoolData.WorkerData workerDatum : dataToWrite.workerData()) {
            if(workerDatum.generatedSharesToday() == 0) {
                continue;
            }
            InfluxUtil.InfluxRecordBuilder workerWrite = new InfluxUtil.InfluxRecordBuilder("pool_worker")
                    .addTag(InfluxEntityStrategy.ENTITY_TAG, entity.getId().toString())
                    .addTag("workerName", workerDatum.workerName())
                    .addField("sharesGeneratedToday", workerDatum.generatedSharesToday())
                    .setTimestamp(unixDay);
            writeApi.writeRecord(bucket, org, WritePrecision.MS, workerWrite.build());
        }

        dataToWrite.paidRewardsSatoshis().forEach((aLong, aDouble) -> {
            if (aDouble == null) {
                return;
            }
            
            InfluxUtil.InfluxRecordBuilder paidRewardWrite = new InfluxUtil.InfluxRecordBuilder("total_rewards")
                    .addTag(InfluxEntityStrategy.ENTITY_TAG, entity.getId().toString())
                    .addField("total_rewards_satoshis", aDouble)
                    .setTimestamp(aLong);
            writeApi.writeRecord(bucket, org, WritePrecision.MS, paidRewardWrite.build());
        });
    }
}
