package de.verdox.pv_miner.influx;

import com.influxdb.Cancellable;
import com.influxdb.client.*;
import com.influxdb.client.domain.TaskCreateRequest;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import de.verdox.pv_miner.statistic.daily.DailyStatisticAccumulator;
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
import lombok.Getter;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
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
    private static final int CONNECT_TIMEOUT_SECONDS = 5;
    private static final int WRITE_TIMEOUT_SECONDS = 15;
    private static final int READ_TIMEOUT_SECONDS = 30;
    private static final int WRITE_BUFFER_LIMIT = 10_000;
    private static final int DEFAULT_WRITE_BATCH_SIZE = 500;
    private static final int DEFAULT_WRITE_FLUSH_INTERVAL_MILLIS = 5_000;

    @Getter
    @Value("${influxdb.url}")
    private String influxUrl;

    @Getter
    @Value("${influxdb.token}")
    private String influxToken;

    @Getter
    @Value("${influxdb.org}")
    private String influxOrg;

    @Getter
    @Value("${influxdb.bucket}")
    private String influxBucket;

    @Value("${influxdb.write-batch-size:500}")
    private int writeBatchSize;

    @Value("${influxdb.write-flush-interval:5s}")
    private Duration writeFlushInterval;

    private InfluxDBClient influxDBClient;
    private WriteApi writeApi;
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
                                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                                .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                                .callTimeout(60, TimeUnit.SECONDS)
                )
                .build();
        this.influxDBClient = InfluxDBClientFactory.create(options);

        LOGGER.info("Pinging influx db...");

        if (this.influxDBClient.ping()) {
            LOGGER.info("Received answer from influx db with version: " + this.influxDBClient.version());
        } else {
            LOGGER.log(Level.SEVERE, "Received no answer from influx db (url = " + influxUrl + ", influxOrg = " + influxOrg + ", influxBucket = " + influxBucket + ")");
            this.influxDBClient.close();
            this.influxDBClient = null;
            throw new IllegalStateException("Influx not reachable on system start.");
        }

        // WriteApi owns a background scheduler and is explicitly designed to be a singleton.
        // Creating one per sample leaks a thread per write, especially while InfluxDB is offline.
        this.writeApi = influxDBClient.makeWriteApi(createWriteOptions(
                writeBatchSize,
                Math.toIntExact(writeFlushInterval.toMillis())
        ));

        strategies.put(PVSiteEntity.class, new PVSiteInfluxStrategy());
        strategies.put(MinerEntity.class, new MinerInfluxStrategy());

        strategies.put(BraiinsPoolEntity.class, new BraiinsPoolInfluxStrategy());
        strategies.put(NiceHashPoolEntity.class, new NiceHashPoolInfluxStrategy());

        registerTasksForDailyStatisticsAccumulator("Downsample_PV_pv_site_data",new PVStatisticsAccumulator(), "pv_site_data");
        registerTasksForDailyStatisticsAccumulator("Downsample_Miner_miner_data", new MinerStatisticsAccumulator(), "miner_data");
        registerTask(
                "Downsample_PV_Hourly",
                InfluxDownsamplingQueries.generatePvHourlyTask(influxBucket, getDownSampledInfluxBucket())
        );
    }

    public InfluxDBClient getInfluxDBClient() {
        return influxDBClient;
    }

    public <B extends QueryEntity<Q>, Q extends QueryResult> void writeDataToApi(B entity, Q result, Instant time) {
        InfluxEntityStrategy<B, Q> strategy = findStrategy(entity);
        if (strategy == null) {
            throw new IllegalStateException("No influx strategy found for type " + entity.getClass().getName());
        }
        try {
            strategy.writeToInflux(writeApi, influxBucket, influxOrg, entity, result, time);
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Error while trying to write to influx", e);
        }
    }

    static WriteOptions createWriteOptions() {
        return createWriteOptions(DEFAULT_WRITE_BATCH_SIZE, DEFAULT_WRITE_FLUSH_INTERVAL_MILLIS);
    }

    static WriteOptions createWriteOptions(int batchSize, int flushIntervalMillis) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Influx write batch size must be positive");
        }
        if (flushIntervalMillis <= 0) {
            throw new IllegalArgumentException("Influx write flush interval must be positive");
        }
        return WriteOptions.builder()
                .batchSize(batchSize)
                .flushInterval(flushIntervalMillis)
                .retryInterval(2_000)
                .maxRetries(5)
                .maxRetryDelay(15_000)
                .maxRetryTime(60_000)
                .bufferLimit(WRITE_BUFFER_LIMIT)
                .build();
    }

    @PreDestroy
    void closeInfluxClient() {
        if (writeApi != null) {
            try {
                writeApi.close();
            } catch (RuntimeException exception) {
                LOGGER.log(Level.WARNING, "Could not close the Influx write buffer cleanly", exception);
            }
        }
        if (influxDBClient != null) {
            influxDBClient.close();
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
        registerTask(taskName, fluxScript);
    }

    void registerTask(String taskName, String fluxScript) {
        TasksApi tasksApi = influxDBClient.getTasksApi();

        try {
            var existingTasks = tasksApi.findTasks();

            var matchingTasks = existingTasks.stream()
                    .filter(t -> taskName.equals(t.getName()))
                    .toList();

            if (!matchingTasks.isEmpty()) {
                var existingTask = matchingTasks.getFirst();

                if (!fluxScript.equals(existingTask.getFlux())) {
                    LOGGER.info("Updating changed influx task: " + taskName);

                    existingTask.setFlux(fluxScript);
                    tasksApi.updateTask(existingTask);
                } else {
                    LOGGER.info("Influx task already exists: " + taskName);
                }
                for (int index = 1; index < matchingTasks.size(); index++) {
                    tasksApi.deleteTask(matchingTasks.get(index));
                    LOGGER.warning("Removed duplicate influx task: " + taskName);
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

    public boolean executeRawFluxQuery(String fluxQuery) {
        Objects.requireNonNull(fluxQuery, "fluxQuery cannot be null");
        try {
            QueryApi queryApi = getInfluxDBClient().getQueryApi();
            queryApi.query(fluxQuery, getInfluxOrg());
            return true;
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "Could not issue raw flux query", e);
            return false;
        }
    }

    public List<FluxRecord> queryRawFlux(String fluxQuery) {
        Objects.requireNonNull(fluxQuery, "fluxQuery cannot be null");
        try {
            return queryRawFluxOrThrow(fluxQuery);
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "Could not issue raw Flux query", e);
            return List.of();
        }
    }

    List<FluxRecord> queryRawFluxOrThrow(String fluxQuery) {
        Objects.requireNonNull(fluxQuery, "fluxQuery cannot be null");
        List<FluxRecord> records = new ArrayList<>();
        for (FluxTable table : getInfluxDBClient().getQueryApi().query(fluxQuery, getInfluxOrg())) {
            records.addAll(table.getRecords());
        }
        return records;
    }

    public List<FluxRecord> queryMeasurementData(
            String bucket,
            String measurement,
            QueryEntity<?> entity,
            Instant from,
            Instant to,
            Collection<String> fields) {
        String fieldFilter = fields.stream()
                .map(field -> "r[\"_field\"] == \"" + field + "\"")
                .reduce((left, right) -> left + " or " + right)
                .orElse("true");
        String query = String.format(
                "from(bucket: \"%s\") |> range(start: %s, stop: %s)" +
                        " |> filter(fn: (r) => r[\"_measurement\"] == \"%s\")" +
                        " |> filter(fn: (r) => r[\"entity\"] == \"%s\")" +
                        " |> filter(fn: (r) => %s)",
                bucket, from, to, measurement, entity.getId(), fieldFilter
        );
        return queryRawFlux(query);
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

    public String getDownSampledInfluxBucket() {
        return influxBucket + "_downsampled";
    }
}
