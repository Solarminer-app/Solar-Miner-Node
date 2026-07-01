package de.verdox.pv_miner.influx;

import com.influxdb.Cancellable;
import com.influxdb.client.*;
import com.influxdb.client.domain.TaskCreateRequest;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import de.verdox.pv_miner.dailystatistic.DailyStatisticAccumulator;
import de.verdox.pv_miner.entity.QueryEntity;
import de.verdox.pv_miner.miner.MinerInfluxStrategy;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.MinerStatisticsAccumulator;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteInfluxStrategy;
import de.verdox.pv_miner.pvsite.PVStatisticsAccumulator;
import de.verdox.pv_miner_extensions.pools.braiins.BraiinsPoolEntity;
import de.verdox.pv_miner_extensions.pools.braiins.BraiinsPoolInfluxStrategy;
import de.verdox.pv_miner_extensions.pools.nicehash.NiceHashPoolEntity;
import de.verdox.pv_miner_extensions.pools.nicehash.NiceHashPoolInfluxStrategy;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class InfluxService {
    private static final Logger LOGGER = Logger.getLogger(InfluxService.class.getSimpleName());

    @Value("${influxdb.url}")
    private String influxUrl;

    @Value("${influxdb.token}")
    private String influxToken;

    @Value("${influxdb.org}")
    private String influxOrg;

    @Value("${influxdb.bucket}")
    private String influxBucket;

    private InfluxDBClient influxDBClient;
    private final Map<Class<? extends QueryEntity>, InfluxEntityStrategy<?, ? extends QueryResult>> strategies = new HashMap<>();

    @PostConstruct
    private void createInfluxClient() {
        Objects.requireNonNull(influxUrl, "influxUrl cannot be null");
        Objects.requireNonNull(influxToken, "influxToken cannot be null");
        Objects.requireNonNull(influxOrg, "influxOrg cannot be null");
        Objects.requireNonNull(influxBucket, "influxBucket cannot be null");

        InfluxDBClientOptions options = InfluxDBClientOptions.builder()
                .url(influxUrl)
                .authenticateToken(influxToken.toCharArray())
                .org(influxOrg)
                .bucket(influxBucket)
                .okHttpClient(
                        new OkHttpClient.Builder()
                                .connectTimeout(30, TimeUnit.SECONDS)
                                .writeTimeout(30, TimeUnit.MINUTES)
                                .readTimeout(30, TimeUnit.MINUTES)
                )
                .build();
        this.influxDBClient = InfluxDBClientFactory.create(options);

        LOGGER.info("Pinging influx db...");

        if (this.influxDBClient.ping()) {
            LOGGER.info("Received answer from influx db with version: " + this.influxDBClient.version());
        } else {
            LOGGER.log(Level.SEVERE, "Received no answer from influx db (url = " + influxUrl + ", token = " + influxToken + ", influxOrg = " + influxOrg + ", influxBucket = " + influxBucket + ")");
            throw new IllegalStateException("Influx not reachable on system start.");
        }

        strategies.put(PVSiteEntity.class, new PVSiteInfluxStrategy());
        strategies.put(MinerEntity.class, new MinerInfluxStrategy());

        strategies.put(BraiinsPoolEntity.class, new BraiinsPoolInfluxStrategy());
        strategies.put(NiceHashPoolEntity.class, new NiceHashPoolInfluxStrategy());

        registerTasksForDailyStatisticsAccumulator("daily_pv_site_data_task",new PVStatisticsAccumulator(), "pv_site_data");
        registerTasksForDailyStatisticsAccumulator("daily_miner_data_task", new MinerStatisticsAccumulator(), "miner_data");
    }

    public InfluxDBClient getInfluxDBClient() {
        return influxDBClient;
    }

    public <B extends QueryEntity<Q>, Q extends QueryResult> void writeDataToApi(B entity, Q result, Instant time) {
        InfluxEntityStrategy<B, Q> strategy = findStrategy(entity);
        if (strategy == null) {
            throw new IllegalStateException("No influx strategy found for type " + entity.getClass().getName());
        }
        try (WriteApi writeApi = influxDBClient.makeWriteApi()) {
            strategy.writeToInflux(writeApi, influxBucket, influxOrg, entity, result, time);
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "Error while trying to write to influx", e);
        }
    }

    public <B extends QueryEntity<? extends Q>, Q extends QueryResult> List<FluxRecord> queryDataFromApi(B entity, Instant from, Instant to, Consumer<InfluxUtil.InfluxQueryBuilder> queryBuilder) {
        InfluxEntityStrategy<B, Q> strategy = findStrategy(entity);
        if (strategy == null) {
            throw new IllegalStateException("No influx strategy found for type " + entity.getClass().getName());
        }
        try {
            QueryApi queryApi = influxDBClient.getQueryApi();
            return strategy.queryDataSync(queryApi, influxBucket, entity, from.toEpochMilli(), to.toEpochMilli(), queryBuilder);
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "An error occurred while querying influx", e);
            return List.of();
        }
    }

    public <B extends QueryEntity<Q>, Q extends QueryResult> void queryDataFromApiAsync(B entity, Instant from, Instant to, Consumer<InfluxUtil.InfluxQueryBuilder> queryBuilder, BiConsumer<Cancellable, FluxRecord> consumer, Consumer<Throwable> onError, Runnable onComplete) {
        InfluxEntityStrategy<B, Q> strategy = findStrategy(entity);
        if (strategy == null) {
            throw new IllegalStateException("No influx strategy found for type " + entity.getClass().getName());
        }
        try {
            QueryApi queryApi = influxDBClient.getQueryApi();
            strategy.queryDataAsync(queryApi, influxBucket, entity, from.toEpochMilli(), to.toEpochMilli(), queryBuilder, consumer, onError, onComplete);
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "An error occurred while querying influx", e);
        }
    }

    public <B extends QueryEntity<Q>, Q extends QueryResult> boolean hasInfluxStrategy(B entity) {
        return findStrategy(entity) != null;
    }

    public <B extends QueryEntity<? extends Q>, Q extends QueryResult> InfluxEntityStrategy<B, Q> findStrategy(B entity) {
        InfluxEntityStrategy<B, Q> strategy = null;
        for (Map.Entry<Class<? extends QueryEntity>, InfluxEntityStrategy<?, ? extends QueryResult>> pair : strategies.entrySet()) {
            if (pair.getKey().isAssignableFrom(entity.getClass())) {
                strategy = (InfluxEntityStrategy<B, Q>) pair.getValue();
                break;
            }
        }
        return strategy;
    }

    public void registerTasksForDailyStatisticsAccumulator(String taskName, DailyStatisticAccumulator<?, ?> accumulator, String measurement) {
        if (!accumulator.supportsDownsampling()) return;

        String fluxScript = accumulator.generateDownsamplingTaskQuery(influxBucket, getDownSampledInfluxBucket(), measurement);
        TasksApi tasksApi = influxDBClient.getTasksApi();

        try {
            var existingTasks = tasksApi.findTasks();

            var existingTaskOpt = existingTasks.stream()
                    .filter(t -> taskName.equals(t.getName()))
                    .findFirst();

            if (existingTaskOpt.isPresent()) {
                var existingTask = existingTaskOpt.get();

                if (!fluxScript.equals(existingTask.getFlux())) {
                    LOGGER.info("Updating changed influx task: " + taskName);

                    existingTask.setFlux(fluxScript);
                    tasksApi.updateTask(existingTask);
                } else {
                    LOGGER.info("Influx task already exists: " + taskName);
                }
            } else {
                LOGGER.info("Registering new influx task: " + taskName);
                tasksApi.createTask(new TaskCreateRequest().flux(fluxScript).org(influxOrg));
            }

        } catch (Exception e) {
            LOGGER.severe("Could not register influx task '" + taskName + "': " + e.getMessage());
        }
    }

    public <E extends QueryEntity<? extends Q>, Q extends QueryResult> List<FluxRecord> queryDownsampledData(
            E entity,
            String downsampledMeasurementName,
            Instant queryFrom,
            Instant queryTo) {

        Objects.requireNonNull(downsampledMeasurementName, "downsampledMeasurementName cannot be null");

        try {
            QueryApi queryApi = influxDBClient.getQueryApi();

            String fluxQuery = String.format("from(bucket: \"%s\") |> range(start: %s, stop: %s)", getDownSampledInfluxBucket(), queryFrom.toString(), queryTo.toString()) +
                    String.format(" |> filter(fn: (r) => r[\"_measurement\"] == \"%s\")", downsampledMeasurementName) +
                    String.format(" |> filter(fn: (r) => r[\"entity\"] == \"%s\")", entity.getId().toString()) +
                    " |> pivot(rowKey:[\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\")";
            List<FluxTable> tables = queryApi.query(fluxQuery, influxOrg);
            List<FluxRecord> records = new ArrayList<>();
            for (com.influxdb.query.FluxTable table : tables) {
                records.addAll(table.getRecords());
            }
            return records;
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "An error occurred while querying downsampled influx data", e);
            return List.of();
        }
    }

    public void executeRawFluxQuery(String fluxQuery) {
        Objects.requireNonNull(fluxQuery, "fluxQuery cannot be null");
        try {

            QueryApi queryApi = getInfluxDBClient().getQueryApi();
            long start = System.currentTimeMillis();

            queryApi.query(fluxQuery, getInfluxOrg());
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "Could not issue raw flux query", e);
        }
    }

    public boolean isMeasurementEmpty(String bucketName, String measurementName) {
        try {
            QueryApi queryApi = getInfluxDBClient().getQueryApi();
            String checkQuery = String.format(
                    "from(bucket: \"%s\") |> range(start: -10y) |> filter(fn: (r) => r[\"_measurement\"] == \"%s\") |> limit(n: 1)",
                    bucketName, measurementName
            );
            return queryApi.query(checkQuery, getInfluxOrg()).isEmpty();
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, "Could not check empty status for measurement " + measurementName + ".", e);
            return true;
        }
    }

    public String getInfluxUrl() {
        return influxUrl;
    }

    public String getInfluxToken() {
        return influxToken;
    }

    public String getInfluxOrg() {
        return influxOrg;
    }

    public String getInfluxBucket() {
        return influxBucket;
    }

    public String getDownSampledInfluxBucket() {
        return influxBucket + "_downsampled";
    }
}