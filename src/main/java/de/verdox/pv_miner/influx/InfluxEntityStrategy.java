package de.verdox.pv_miner.influx;

import com.influxdb.Cancellable;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApi;
import com.influxdb.query.FluxRecord;
import de.verdox.pv_miner.entity.QueryEntity;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public interface InfluxEntityStrategy<E extends QueryEntity<? extends Q>, Q extends QueryResult> {
    String ENTITY_TAG = "entity";

    Logger LOGGER = Logger.getLogger(InfluxEntityStrategy.class.getSimpleName());

    void writeToInflux(WriteApi writeApi, String bucket, String org, E entity, Q dataToWrite, Instant timeOfData);

    default List<FluxRecord> queryDataSync(
            QueryApi queryApi,
            String bucket,
            E entity,
            long timeStart,
            long timeEnd,
            Consumer<InfluxUtil.InfluxQueryBuilder> queryBuilder
    ) {
        List<FluxRecord> records = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        queryDataAsync(
                queryApi,
                bucket,
                entity,
                timeStart,
                timeEnd,
                queryBuilder,
                (cancellable, record) -> records.add(record),
                error -> {
                    errorRef.set(error);
                    LOGGER.log(Level.SEVERE, "An error occured while querying influx", error);
                    latch.countDown();
                },
                latch::countDown
        );

        try {
            latch.await(1, TimeUnit.MINUTES); // wait until query is complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Query was interrupted", e);
        }

        if (errorRef.get() != null) {
            throw new RuntimeException("Error occurred during query", errorRef.get());
        }
        return records;
    }

    default void queryDataAsync(QueryApi queryApi, String bucket, E entity, long timeStart, long timeEnd, Consumer<InfluxUtil.InfluxQueryBuilder> queryBuilder, BiConsumer<Cancellable, FluxRecord> consumer, Consumer<Throwable> onError, Runnable onComplete) {
        InfluxUtil.InfluxQueryBuilder builder = new InfluxUtil.InfluxQueryBuilder(bucket)
                .setTimeRange(Instant.ofEpochMilli(timeStart), Instant.ofEpochMilli(timeEnd))
                .addFilter("entity", entity.getId().toString());
        queryBuilder.accept(builder);
        queryApi.query(builder.build(), consumer, onError, onComplete);
    }
}
