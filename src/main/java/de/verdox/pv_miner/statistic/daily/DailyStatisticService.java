package de.verdox.pv_miner.statistic.daily;

import com.influxdb.query.FluxRecord;
import de.verdox.pv_miner.entity.EntityMonitoringService;
import de.verdox.pv_miner.entity.QueryEntity;
import de.verdox.pv_miner.influx.InfluxService;
import de.verdox.pv_miner.influx.QueryResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Service
public class DailyStatisticService {
    private static final Logger LOGGER = Logger.getLogger(DailyStatisticService.class.getSimpleName());

    @Autowired
    private EntityMonitoringService entityMonitoringService;

    @Autowired
    private InfluxService influxService;

    private final Map<UUID, Map<String, DailyStatistic>> cacheMatrix = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Disposable>> subscriptionMatrix = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastStreamTimestamp = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <S extends DailyStatistic, E extends QueryEntity<? extends Q>, Q extends QueryResult> S getLiveDailyStatistic(
            E entity,
            String statisticKey,
            DailyStatisticAccumulator<S, Q> accumulator) {

        long startOfTodayUTC = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        Map<String, DailyStatistic> entityCache = cacheMatrix.computeIfAbsent(entity.getId(), k -> new ConcurrentHashMap<>());
        DailyStatistic cachedStat = entityCache.get(statisticKey);

        if (cachedStat == null || cachedStat.getStartOfDayUnixTimestampUTC() != startOfTodayUTC) {
            synchronized (entityCache) {
                cachedStat = entityCache.get(statisticKey);
                if (cachedStat == null || cachedStat.getStartOfDayUnixTimestampUTC() != startOfTodayUTC) {

                    killSubscription(entity.getId(), statisticKey);

                    S newCacheInstance = accumulator.createEmptyInstance();
                    newCacheInstance.setStartOfDayUnixTimestampUTC(startOfTodayUTC);
                    newCacheInstance.setFromMillis(startOfTodayUTC);

                    if(accumulator.syncsWithDatabaseOnLiveData()) {
                        populateInitialCacheSynchronous(entity, newCacheInstance, accumulator, startOfTodayUTC);
                    }

                    long now = System.currentTimeMillis();
                    newCacheInstance.setToMillis(now);
                    lastStreamTimestamp.put(entity.getId(), now);

                    entityCache.put(statisticKey, newCacheInstance);
                    cachedStat = newCacheInstance;

                    subscribeToLiveUpdates(entity, statisticKey, newCacheInstance, accumulator);
                }
            }
        }

        return (S) cachedStat;
    }

    private <E extends QueryEntity<? extends Q>, Q extends QueryResult, S extends DailyStatistic> void populateInitialCacheSynchronous(
            E entity, S cacheInstance, DailyStatisticAccumulator<S, Q> accumulator, long startOfTodayUTC) {

        Instant from = Instant.ofEpochMilli(startOfTodayUTC);
        Instant to = Instant.now();

        try {
            List<FluxRecord> records = influxService.queryDataFromApi(entity, from, to, queryBuilder -> {
                queryBuilder.setAggregation(accumulator.getAggregationOperation(), Duration.ofDays(1), accumulator.getAggregationOffset());
            });
            for (FluxRecord record : records) {
                accumulator.mapFromFluxRecord(cacheInstance, record);
            }
        } catch (Exception e) {
            LOGGER.severe("Failed to populate live cache asynchronously: " + e.getMessage());
        }
    }

    public <S extends DailyStatistic, E extends QueryEntity<? extends Q>, Q extends QueryResult> List<S> getHistoricalStatistics(
            E entity,
            String statisticKey,
            Instant from,
            Instant to,
            DailyStatisticAccumulator<S, Q> accumulator) {

        long oneDayMillis = TimeUnit.DAYS.toMillis(1);
        List<S> resultList = new ArrayList<>();

        Map<Long, S> fetchedData = new TreeMap<>();

        try {
            if (accumulator.supportsDownsampling()) {
                List<FluxRecord> records = influxService.queryDownsampledData(
                        entity,
                        accumulator.getDownsampledMeasurementName(),
                        from,
                        to
                );

                for (FluxRecord record : records) {
                    Instant recordTime = accumulator.remapRecordTime(record.getTime());
                    if (recordTime == null) continue;

                    long startOfDayTag = recordTime.truncatedTo(ChronoUnit.DAYS).toEpochMilli();

                    S statObject = fetchedData.computeIfAbsent(startOfDayTag, timestamp -> {
                        S newStat = accumulator.createEmptyInstance();
                        newStat.setStartOfDayUnixTimestampUTC(timestamp);
                        newStat.setFromMillis(timestamp);
                        newStat.setToMillis(timestamp + oneDayMillis - 1);
                        return newStat;
                    });
                    accumulator.mapFromDownsampledRecord(statObject, record);
                }
            } else {
                List<FluxRecord> records = influxService.queryDataFromApi(entity, from, to, queryBuilder -> {
                    queryBuilder.setAggregation(accumulator.getAggregationOperation(), Duration.ofDays(1), accumulator.getAggregationOffset());
                });

                for (FluxRecord record : records) {
                    Instant recordTime = accumulator.remapRecordTime(record.getTime());
                    if (recordTime == null) continue;

                    long startOfDayTag = recordTime.truncatedTo(ChronoUnit.DAYS).toEpochMilli();

                    S statObject = fetchedData.computeIfAbsent(startOfDayTag, timestamp -> {
                        S newStat = accumulator.createEmptyInstance();
                        newStat.setStartOfDayUnixTimestampUTC(timestamp);
                        newStat.setFromMillis(timestamp);
                        newStat.setToMillis(timestamp + oneDayMillis - 1);
                        return newStat;
                    });
                    accumulator.mapFromFluxRecord(statObject, record);
                }
            }

            resultList.addAll(fetchedData.values());

        } catch (Exception e) {
            LOGGER.severe("Failed to fetch historical statistics for entity " + entity.getId() + ": " + e.getMessage());
        }

        return resultList;
    }

    private <E extends QueryEntity<? extends Q>, Q extends QueryResult, S extends DailyStatistic> void subscribeToLiveUpdates(
            E entity, String statisticKey, S cacheInstance, DailyStatisticAccumulator<S, Q> accumulator) {

        lastStreamTimestamp.put(entity.getId(), System.currentTimeMillis());

        Disposable subscription = entityMonitoringService.hookIntoLiveData(entity)
                .subscribe(liveData -> {
                    long now = System.currentTimeMillis();

                    synchronized (cacheInstance) {
                        long lastTime = lastStreamTimestamp.getOrDefault(entity.getId(), now);
                        lastStreamTimestamp.put(entity.getId(), now);

                        double deltaHours = (now - lastTime) / 1000.0 / 3600.0;
                        if (deltaHours <= 0) {
                            deltaHours = 1.0 / 3600.0;
                        }

                        cacheInstance.setToMillis(now);
                        accumulator.accumulate(cacheInstance, (Q) liveData, deltaHours);
                    }
                }, error -> LOGGER.severe("Error in Live-Statistic-Stream for key [" + statisticKey + "]: " + error.getMessage()));

        subscriptionMatrix.computeIfAbsent(entity.getId(), k -> new ConcurrentHashMap<>()).put(statisticKey, subscription);
    }

    private void killSubscription(UUID entityId, String statisticKey) {
        Map<String, Disposable> entitySubs = subscriptionMatrix.get(entityId);
        if (entitySubs != null) {
            Disposable sub = entitySubs.remove(statisticKey);
            if (sub != null && !sub.isDisposed()) {
                sub.dispose();
            }
        }
    }

    public void cleanUpEntity(UUID entityId) {
        cacheMatrix.remove(entityId);
        Map<String, Disposable> subs = subscriptionMatrix.remove(entityId);
        if (subs != null) {
            subs.values().forEach(Disposable::dispose);
        }
        lastStreamTimestamp.remove(entityId);
    }
}