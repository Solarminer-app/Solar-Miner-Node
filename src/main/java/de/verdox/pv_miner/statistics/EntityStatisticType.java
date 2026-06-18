package de.verdox.pv_miner.statistics;

import com.influxdb.query.FluxRecord;
import de.verdox.pv_miner.entity.QueryEntity;
import de.verdox.pv_miner.influx.QueryResult;
import de.verdox.pv_miner.influx.InfluxUtil;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Combines historical data and live data into a single thread safe wrapper object.
 *
 * @param <E> The entity type
 * @param <Q> The query result type
 * @param <T> The data type to parse the specific data into
 */
public class EntityStatisticType<E extends QueryEntity<Q>, Q extends QueryResult, T> {
    private final TimeUnit accuracy;
    private final int limit;
    private final Class<E> entityType;
    private final String statisticsId;
    private final Function<FluxRecord, T> influxToData;
    private final Function<Q, T> liveData;
    private final Consumer<InfluxUtil.InfluxQueryBuilder> influxQuery;

    EntityStatisticType(TimeUnit accuracy, int limit, Class<E> entityType, String statisticsId, Function<FluxRecord, T> influxToData, Function<Q, T> liveData, Consumer<InfluxUtil.InfluxQueryBuilder> influxQuery) {
        this.accuracy = accuracy;
        this.limit = limit;
        this.entityType = entityType;
        this.statisticsId = statisticsId;
        this.influxToData = influxToData;
        this.liveData = liveData;
        this.influxQuery = influxQuery;
    }

    public TimeUnit getAccuracy() {
        return accuracy;
    }

    public int getLimit() {
        return limit;
    }

    public Class<E> getEntityType() {
        return entityType;
    }

    public String getStatisticsId() {
        return statisticsId;
    }

    public Function<FluxRecord, T> getInfluxToData() {
        return influxToData;
    }

    public Function<Q, T> getLiveData() {
        return liveData;
    }

    public Consumer<InfluxUtil.InfluxQueryBuilder> getInfluxQuery() {
        return influxQuery;
    }
}
