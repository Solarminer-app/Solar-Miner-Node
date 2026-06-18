package de.verdox.pv_miner.dailystatistic;

import com.influxdb.query.FluxRecord;
import de.verdox.pv_miner.influx.InfluxUtil;
import de.verdox.pv_miner.influx.QueryResult;

import java.time.Duration;
import java.time.Instant;

public interface DailyStatisticAccumulator<S extends DailyStatistic, Q extends QueryResult> {
    S createEmptyInstance();

    void accumulate(S currentCache, Q liveData, double timeWindowInHours);
    
    void mapFromFluxRecord(S statisticObject, FluxRecord record);

    default InfluxUtil.AggregateOperation getAggregationOperation() {
        return InfluxUtil.AggregateOperation.INTEGRAL;
    }

    default Duration getAggregationOffset() {
        return Duration.ZERO;
    }

    default Instant remapRecordTime(Instant recordTime) {
        return recordTime;
    }

    default boolean syncsWithDatabaseOnLiveData() {
        return true;
    }

    default boolean supportsDownsampling() {
        return false;
    }

    default String getDownsampledMeasurementName() {
        return null;
    }


    default String generateDownsamplingTaskQuery(String sourceBucket, String targetBucket, String measurement) {
        return "";
    }

    default void mapFromDownsampledRecord(S statisticObject, FluxRecord record) {
        mapFromFluxRecord(statisticObject, record);
    }
}