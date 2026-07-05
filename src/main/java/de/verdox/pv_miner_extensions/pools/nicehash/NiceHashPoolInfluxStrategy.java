package de.verdox.pv_miner_extensions.pools.nicehash;

import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import de.verdox.pv_miner.miningpool.MiningPoolInfluxStrategy;
import de.verdox.pv_miner.influx.InfluxUtil;

import java.time.Instant;

public class NiceHashPoolInfluxStrategy implements MiningPoolInfluxStrategy<NiceHashPoolEntity.NicehashWorkerStats, NiceHashPoolEntity> {
    @Override
    public void writeToInflux(WriteApi writeApi, String bucket, String org, NiceHashPoolEntity entity, NiceHashPoolEntity.NicehashWorkerStats dataToWrite, Instant timeOfData) {
        InfluxUtil.InfluxRecordBuilder influxRecordBuilder = new InfluxUtil.InfluxRecordBuilder("pool_data")
                .addTag("entity", entity.getId().toString())
                .addField("payoutThresholdSatoshis", dataToWrite.payoutThreshold())
                .setTimestamp(timeOfData.toEpochMilli());
        writeApi.writeRecord(bucket, org, WritePrecision.MS, influxRecordBuilder.build());

        for (NiceHashPoolEntity.UnpaidAmount worker : dataToWrite.workers()) {
            InfluxUtil.InfluxRecordBuilder workerWrite = new InfluxUtil.InfluxRecordBuilder("pool_worker")
                    .addTag("entity", entity.getId().toString())
                    .addTag("workerName", worker.workerName())
                    .addField("unpaidAmount", worker.unpaidSatoshis())
                    .setTimestamp(timeOfData.toEpochMilli());
            writeApi.writeRecord(bucket, org, WritePrecision.MS, workerWrite.build());
        }

        for (NiceHashPoolEntity.Payout paidAmount : dataToWrite.paidAmounts()) {
            if (paidAmount.paidSatoshis() <= 0) {
                continue;
            }
            InfluxUtil.InfluxRecordBuilder workerWrite = new InfluxUtil.InfluxRecordBuilder("total_rewards")
                    .addTag("entity", entity.getId().toString())
                    .addField("total_rewards_satoshis", paidAmount.paidSatoshis())
                    .setTimestamp(paidAmount.timestamp());
            writeApi.writeRecord(bucket, org, WritePrecision.MS, workerWrite.build());
        }
    }
}
