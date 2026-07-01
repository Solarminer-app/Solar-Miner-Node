package de.verdox.pv_miner.miningpool;

import com.influxdb.query.FluxRecord;
import de.verdox.pv_miner.statistic.daily.DailyStatisticAccumulator;
import de.verdox.pv_miner.influx.InfluxUtil;
import de.verdox.pv_miner.util.CryptoCurrency;

import java.time.Duration;
import java.time.Instant;

public class MiningPoolStatisticsAccumulator implements DailyStatisticAccumulator<MiningPoolStatisticsPerDay, MiningPoolData> {

    @Override
    public MiningPoolStatisticsPerDay createEmptyInstance() {
        MiningPoolStatisticsPerDay stats = new MiningPoolStatisticsPerDay();
        stats.setCryptoCurrency(CryptoCurrency.BITCOIN);
        stats.setAmountCryptoCurrency(0L);
        return stats;
    }

    @Override
    public void accumulate(MiningPoolStatisticsPerDay currentCache, MiningPoolData liveData, double timeWindowInHours) {
        if (liveData != null) {
            currentCache.setAmountCryptoCurrency((long) liveData.calculateSatoshiRewardToday());
        }
        else {
            System.out.println("no live data!");
        }
    }

    @Override
    public void mapFromFluxRecord(MiningPoolStatisticsPerDay statisticObject, FluxRecord record) {
        String fieldName = record.getField();
        Object valueObj = record.getValue();
        if (valueObj == null) return;

        long value = (valueObj instanceof Double) ? ((Double) valueObj).longValue() : ((Number) valueObj).longValue();



        switch (fieldName) {
            case "total_rewards_satoshis":
                statisticObject.setAmountCryptoCurrency(statisticObject.getAmountCryptoCurrency() + value);
                break;
        }
    }

    @Override
    public InfluxUtil.AggregateOperation getAggregationOperation() {
        return InfluxUtil.AggregateOperation.LAST;
    }

    @Override
    public Instant remapRecordTime(Instant recordTime) {
        return recordTime.minus(Duration.ofDays(1));
    }

    @Override
    public Duration getAggregationOffset() {
        return Duration.ofHours(3);
    }

    @Override
    public boolean syncsWithDatabaseOnLiveData() {
        return false;
    }
}
