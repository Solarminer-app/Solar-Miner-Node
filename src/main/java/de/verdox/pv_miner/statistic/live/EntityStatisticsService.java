package de.verdox.pv_miner.statistic.live;

import com.influxdb.query.FluxRecord;
import de.verdox.pv_miner.entity.QueryEntity;
import de.verdox.pv_miner.pvsite.PVSiteDataDTO;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.influx.InfluxService;
import de.verdox.pv_miner.entity.EntityMonitoringService;
import de.verdox.pv_miner.influx.QueryResult;
import de.verdox.pv_miner.influx.InfluxUtil;
import de.verdox.pv_miner.pvsite.PVSiteInfluxStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class EntityStatisticsService {
    private static final Logger LOGGER = Logger.getLogger(EntityStatisticsService.class.getSimpleName());

    @Autowired
    private EntityMonitoringService entityMonitoringService;

    @Autowired
    private InfluxService influxService;

    private final Map<UUID, Map<EntityStatisticType<?, ?, ?>, EntityStatistic<?, ?, ?>>> statisticMap = new HashMap<>();

    public final EntityStatisticType<PVSiteEntity, PVSiteDataDTO, Double> PV_POWER_DAY_STATISTIC = createStatisticType(TimeUnit.MINUTES, 60 * 24,
            PVSiteEntity.class,
            PVSiteInfluxStrategy.PV_POWER_IN_KW,
            PVSiteDataDTO::getPvPower,
            fluxRecord -> (Double) fluxRecord.getValue(),
            influxQueryBuilder -> influxQueryBuilder.setAggregation(InfluxUtil.AggregateOperation.MEAN, Duration.ofMinutes(1)));

    public final EntityStatisticType<PVSiteEntity, PVSiteDataDTO, Double> PV_GRID_EXPORT = createStatisticType(TimeUnit.MINUTES, 60 * 24,
            PVSiteEntity.class,
            PVSiteInfluxStrategy.GRID_CONSUMPTION_POWER,
            PVSiteDataDTO::getExportPowerKw,
            fluxRecord -> (Double) fluxRecord.getValue(),
            influxQueryBuilder -> influxQueryBuilder.setAggregation(InfluxUtil.AggregateOperation.MEAN, Duration.ofMinutes(1)));

    public final EntityStatisticType<PVSiteEntity, PVSiteDataDTO, Double> CONSUMPTION = createStatisticType(TimeUnit.MINUTES, 60 * 24,
            PVSiteEntity.class,
            PVSiteInfluxStrategy.LOADS_POWER_IN_KW,
            PVSiteDataDTO::getLoadPowerKw,
            fluxRecord -> (Double) fluxRecord.getValue(),
            influxQueryBuilder -> influxQueryBuilder.setAggregation(InfluxUtil.AggregateOperation.MEAN, Duration.ofMinutes(1)));

    public final EntityStatisticType<PVSiteEntity, PVSiteDataDTO, Double> MINER_CONSUMPTION = createStatisticType(TimeUnit.MINUTES, 60 * 24,
            PVSiteEntity.class,
            PVSiteInfluxStrategy.MINER_POWER_IN_KW,
            PVSiteDataDTO::getTotalMinerPowerKw,
            fluxRecord -> (Double) fluxRecord.getValue(),
            influxQueryBuilder -> influxQueryBuilder.setAggregation(InfluxUtil.AggregateOperation.MEAN, Duration.ofMinutes(1)));

    public final EntityStatisticType<PVSiteEntity, PVSiteDataDTO, Double> PV_IMPORT = createStatisticType(TimeUnit.MINUTES, 60 * 24,
            PVSiteEntity.class,
            PVSiteInfluxStrategy.FEED_IN_POWER_IN_KW,
            PVSiteDataDTO::getImportPowerKw,
            fluxRecord -> (Double) fluxRecord.getValue(),
            influxQueryBuilder -> influxQueryBuilder.setAggregation(InfluxUtil.AggregateOperation.MEAN, Duration.ofMinutes(1)));

    public final EntityStatisticType<PVSiteEntity, PVSiteDataDTO, Double> PV_POWER_PER_HOUR_STATISTIC = createStatisticType(TimeUnit.HOURS, -1,
            PVSiteEntity.class,
            PVSiteInfluxStrategy.PV_POWER_IN_KW,
            PVSiteDataDTO::getPvPower,
            fluxRecord -> (Double) fluxRecord.getValue(),
            influxQueryBuilder -> {
                influxQueryBuilder.setAggregation(InfluxUtil.AggregateOperation.MEDIAN, Duration.ofHours(1));
            });

    /**
     * Loads the statistic type for a specific entity in a specified time frame. If the time frame was not loaded yet the statistics are populated asynchronously.
     *
     * @param statisticType the statistics type
     * @param entity        the entity
     * @param from          the start of the time interval
     * @param to            the end of the time interval
     * @param forceLoad     whether the data should be loaded from the historical db regardless of it being already loaded
     * @param <E>           the entity type
     * @param <Q>           the query result type
     * @param <T>           the data type
     * @return the statistics object
     */
    public <E extends QueryEntity<Q>, Q extends QueryResult, T> EntityStatistic<E, Q, T> loadStatistic(EntityStatisticType<E, Q, T> statisticType, E entity, long from, long to, boolean forceLoad) {
        if (!statisticType.getEntityType().isAssignableFrom(entity.getClass())) {
            throw new IllegalArgumentException("Cannot monitor the statistic type " + statisticType.getStatisticsId() + " for the provided entity. The provided " + entity.getClass().getSimpleName() + " " + entity.getId() + "is not an assignable from" + statisticType.getEntityType());
        }

        EntityStatistic<E, Q, T> entityStatistic;
        if (!statisticMap.computeIfAbsent(entity.getId(), queryEntity -> new HashMap<>()).containsKey(statisticType)) {
            entityStatistic = (EntityStatistic<E, Q, T>) statisticMap.computeIfAbsent(entity.getId(), queryEntity -> new HashMap<>()).computeIfAbsent(statisticType, entityStatisticType -> new EntityStatistic<>(statisticType, entity));
            entityStatistic.setLiveDataSubscription(entityMonitoringService.hookIntoLiveData(entity)
                    .sampleFirst(Duration.of(1, statisticType.getAccuracy().toChronoUnit()))
                    .subscribe(q -> {
                        entityStatistic.getValues().addData(System.currentTimeMillis(), statisticType.getLiveData().apply(q));
                        entityStatistic.triggerStatisticsUpdate();
                    }));
            LOGGER.log(Level.FINE, "Creating statistic " + statisticType.getStatisticsId() + " for " + entity.getClass().getSimpleName() + " " + entity.getId());
        } else {
            entityStatistic = (EntityStatistic<E, Q, T>) statisticMap.get(entity.getId()).get(statisticType);
        }

        var hasDataForInterval = entityStatistic.hasDataForInterval(from, to);

        if (forceLoad || !hasDataForInterval) {

            var fromInstant = (entityStatistic.getValues().isEmpty() || !hasDataForInterval) ? Instant.ofEpochMilli(from) : Instant.ofEpochMilli(entityStatistic.getValues().getMostRecentTimeStamp());
            var toInstant = Instant.ofEpochMilli(to);

            AtomicLong atomicLong = new AtomicLong();

            influxService.queryDataFromApiAsync(entity,
                    fromInstant,
                    toInstant,
                    influxQueryBuilder -> statisticType.getInfluxQuery().accept(influxQueryBuilder.addFilter("_field", statisticType.getStatisticsId())),
                    (cancellable, fluxRecord) -> {
                        T value = statisticType.getInfluxToData().apply(fluxRecord);
                        atomicLong.addAndGet(1);
                        entityStatistic.getValues().addData(fluxRecord.getTime().toEpochMilli(), value);
                    },
                    throwable -> {
                        entityStatistic.notifyPopulationError(fromInstant.toEpochMilli(), toInstant.toEpochMilli());
                        LOGGER.log(Level.SEVERE, "Could not populate statistic", throwable);
                    },
                    () -> {
                        entityStatistic.notifyPopulationDone(fromInstant.toEpochMilli(), toInstant.toEpochMilli());
                        entityStatistic.triggerStatisticsUpdate();
                    });
        }
        return entityStatistic;
    }

    public void cleanUp(QueryEntity<?> queryEntity) {
        if (!statisticMap.containsKey(queryEntity.getId())) {
            return;
        }
        statisticMap.get(queryEntity.getId()).forEach((entityStatisticType, entityStatistic) -> entityStatistic.close());
        statisticMap.remove(queryEntity.getId());
    }

    private <E extends QueryEntity<Q>, Q extends QueryResult, T> EntityStatisticType<E, Q, T> createStatisticType(TimeUnit accuracy, int limit, Class<E> entityType, String name, Function<Q, T> liveData, Function<FluxRecord, T> fluxToValue, Consumer<InfluxUtil.InfluxQueryBuilder> influxQuery) {
        LOGGER.info("Creating new statistics for entities of type " + entityType.getSimpleName() + " with id " + name);
        return new EntityStatisticType<>(accuracy, limit, entityType, name, fluxToValue, liveData, influxQuery);
    }
}
