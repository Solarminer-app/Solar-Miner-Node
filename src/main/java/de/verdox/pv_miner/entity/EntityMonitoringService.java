package de.verdox.pv_miner.entity;

import de.verdox.pv_miner.influx.InfluxService;
import de.verdox.pv_miner.influx.QueryResult;
import de.verdox.pv_miner.pvconfig.DeviceProfileConfigurationException;
import lombok.Getter;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class EntityMonitoringService {
    private static final Logger LOGGER = Logger.getLogger(EntityMonitoringService.class.getSimpleName());
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    private final EntityQueryService entityQueryService;
    private final InfluxService influxService;

    public EntityMonitoringService(EntityQueryService entityQueryService, InfluxService influxService) {
        this.entityQueryService = entityQueryService;
        this.influxService = influxService;
    }

    private final Map<UUID, MonitoringJob<?, ?>> monitoringJobs = new ConcurrentHashMap<>();

    public <E extends QueryEntity<? extends Q>, Q extends QueryResult> Flux<Q> hookIntoLiveData(E entity) {
        if (monitoringJobs.containsKey(entity.getId())
                && !monitoringJobs.get(entity.getId()).isRunning
                && !monitoringJobs.get(entity.getId()).profileRepairRequired) {
            monitoringJobs.get(entity.getId()).start();
        }
        if (!monitoringJobs.containsKey(entity.getId())) {
            throw new IllegalStateException(entity.getClass().getSimpleName() + " " + entity.getId() + " is not attached to monitoring service");
        }

        var monitoringJob = monitoringJobs.get(entity.getId());
        return (Flux<Q>) monitoringJob.getLiveDataFlux();
    }

    @SuppressWarnings("unchecked")
    public <E extends QueryEntity<Q>, Q extends QueryResult> void attach(E entity, Q defaultValue) {
        MonitoringJob<?, ?> existing = monitoringJobs.get(entity.getId());
        if (existing != null) {
            ((MonitoringJob<E, Q>) existing).updateEntity(entity);
            return;
        }
        LOGGER.info("Attaching " + entity + " to entity monitoring");

        MonitoringJob<E, Q> monitoringJob = new MonitoringJob<>(entity, defaultValue);
        MonitoringJob<?, ?> concurrentlyAttached = monitoringJobs.putIfAbsent(entity.getId(), monitoringJob);
        if (concurrentlyAttached == null) {
            monitoringJob.start();
        } else {
            ((MonitoringJob<E, Q>) concurrentlyAttached).updateEntity(entity);
        }
    }

    public <E extends QueryEntity<Q>, Q extends QueryResult> void detach(E entity) {
        MonitoringJob<?, ?> monitoringJob = monitoringJobs.remove(entity.getId());
        if (monitoringJob != null) {
            monitoringJob.stop();
        }
        LOGGER.info("Detaching " + entity + " from entity monitoring");
    }

    private class MonitoringJob<E extends QueryEntity<Q>, Q extends QueryResult> {
        @Getter
        private volatile E entity;
        private final Q defaultValue;
        @Getter
        private Flux<Q> liveDataFlux;
        private ScheduledFuture<?> schedulerJob;
        private Disposable monitoringJob;
        @Getter
        private volatile boolean isRunning;
        private volatile boolean profileRepairRequired;

        public MonitoringJob(E entity, Q defaultValue) {
            this.entity = entity;
            this.defaultValue = defaultValue;
        }

        private synchronized void updateEntity(E entity) {
            this.entity = entity;
            this.profileRepairRequired = false;
            if (!isRunning) {
                start();
            }
        }

        private synchronized void start() {
            if (isRunning) {
                return;
            }
            stop();
            LOGGER.info("Starting monitoring job for " + entity);
            Sinks.Many<Q> sink = Sinks.many().replay().latest();
            this.liveDataFlux = sink.asFlux().share();

            if (influxService.hasInfluxStrategy(entity)) {
                monitoringJob = this.liveDataFlux
                        .subscribe(q -> influxService.writeDataToApi(entity, q, Instant.now()));
            } else {
                LOGGER.info("No influx strategy found for monitoring the entity " + entity);
            }

            isRunning = true;
            schedulerJob = scheduler.scheduleAtFixedRate(() -> {
                try {
                    Q result = entityQueryService.query(entity);
                    if (result == null) {
                        return;
                    }
                    sink.tryEmitNext(result).isFailure();
                } catch (DeviceProfileConfigurationException exception) {
                    profileRepairRequired = true;
                    LOGGER.warning("Pausing monitoring for " + entity.getClass().getSimpleName() + " - "
                            + entity.getId() + " because its device profile must be repaired: "
                            + exception.getMessage());
                    stop();
                } catch (Throwable e) {
                    LOGGER.log(Level.SEVERE, "An error occurred while emitting a monitoring signal for " + entity.getClass().getSimpleName() + " - " + entity.getId() + ": " + e.getMessage(), e);
                }
            }, 0, 1, TimeUnit.SECONDS);
        }

        private synchronized void stop() {
            if (!isRunning) {
                return;
            }
            if (schedulerJob != null) {
                schedulerJob.cancel(true);
            }
            if (monitoringJob != null) {
                monitoringJob.dispose();
            }
            isRunning = false;
            LOGGER.info("Monitoring job stopped for " + entity.getClass().getSimpleName() + ": " + entity);
        }

    }
}
