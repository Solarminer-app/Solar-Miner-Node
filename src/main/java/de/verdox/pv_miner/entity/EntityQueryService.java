package de.verdox.pv_miner.entity;

import de.verdox.pv_miner.influx.QueryResult;
import de.verdox.pv_miner.miner.MinerQueryStrategy;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.SiteQueryStrategy;
import de.verdox.pv_miner_extensions.device.modbus.battery.ModbusBattery;
import de.verdox.pv_miner_extensions.device.modbus.battery.ModbusBatteryQueryStrategy;
import de.verdox.pv_miner_extensions.device.modbus.inverter.ModbusInverter;
import de.verdox.pv_miner_extensions.device.modbus.inverter.ModbusInverterQueryStrategy;
import de.verdox.pv_miner_extensions.device.modbus.smartmeter.ModbusSmartMeter;
import de.verdox.pv_miner_extensions.device.modbus.smartmeter.ModbusSmartMeterQueryStrategy;
import de.verdox.pv_miner_extensions.device.rest.battery.RestBattery;
import de.verdox.pv_miner_extensions.device.rest.battery.RestBatteryQueryStrategy;
import de.verdox.pv_miner_extensions.device.rest.inverter.RestInverter;
import de.verdox.pv_miner_extensions.device.rest.inverter.RestInverterQueryStrategy;
import de.verdox.pv_miner_extensions.device.rest.smartmeter.RestSmartMeter;
import de.verdox.pv_miner_extensions.device.rest.smartmeter.RestSmartMeterQueryStrategy;
import de.verdox.pv_miner_extensions.device.modbusrtu.battery.ModbusRtuBattery;
import de.verdox.pv_miner_extensions.device.modbusrtu.battery.ModbusRtuBatteryQueryStrategy;
import de.verdox.pv_miner_extensions.device.modbusrtu.inverter.ModbusRtuInverter;
import de.verdox.pv_miner_extensions.device.modbusrtu.inverter.ModbusRtuInverterQueryStrategy;
import de.verdox.pv_miner_extensions.device.modbusrtu.smartmeter.ModbusRtuSmartMeter;
import de.verdox.pv_miner_extensions.device.modbusrtu.smartmeter.ModbusRtuSmartMeterQueryStrategy;
import de.verdox.pv_miner_extensions.device.message.MessageBatteryQueryStrategy;
import de.verdox.pv_miner_extensions.device.message.MessageInverterQueryStrategy;
import de.verdox.pv_miner_extensions.device.message.MessageSmartMeterQueryStrategy;
import de.verdox.pv_miner_extensions.device.mqtt.battery.MqttBattery;
import de.verdox.pv_miner_extensions.device.mqtt.inverter.MqttInverter;
import de.verdox.pv_miner_extensions.device.mqtt.smartmeter.MqttSmartMeter;
import de.verdox.pv_miner_extensions.device.websocket.battery.WebSocketBattery;
import de.verdox.pv_miner_extensions.device.websocket.inverter.WebSocketInverter;
import de.verdox.pv_miner_extensions.device.websocket.smartmeter.WebSocketSmartMeter;
import de.verdox.pv_miner_extensions.miner.AgentMinerEntity;
import de.verdox.pv_miner_extensions.miner.AntminerEntity;
import de.verdox.pv_miner_extensions.miner.BraiinsOSAsicMinerEntity;
import de.verdox.pv_miner_extensions.pools.braiins.BraiinsPoolEntity;
import de.verdox.pv_miner_extensions.pools.braiins.BraiinsPoolQueryStrategy;
import de.verdox.pv_miner_extensions.pools.nicehash.NiceHashPoolEntity;
import de.verdox.pv_miner_extensions.pools.nicehash.NicehashPoolQueryStrategy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class EntityQueryService {
    private static final Logger LOGGER = Logger.getLogger(EntityQueryService.class.getSimpleName());
    private final Map<Class<? extends QueryEntity<?>>, Strategy<?, ? extends QueryResult>> strategies = new HashMap<>();
    private final Map<UUID, QueryResult> cachedLastQueries = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> lastSuccessfulQueries = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> lastFailedQueries = new ConcurrentHashMap<>();

    public EntityQueryService() {
        MinerQueryStrategy minerQueryStrategy = new MinerQueryStrategy();
        this.strategies.put(AgentMinerEntity.class, minerQueryStrategy);
        this.strategies.put(BraiinsOSAsicMinerEntity.class, minerQueryStrategy);
        this.strategies.put(AntminerEntity.class, minerQueryStrategy);

        this.strategies.put(PVSiteEntity.class, new SiteQueryStrategy());

        this.strategies.put(ModbusBattery.class, new ModbusBatteryQueryStrategy());
        this.strategies.put(ModbusInverter.class, new ModbusInverterQueryStrategy());
        this.strategies.put(ModbusSmartMeter.class, new ModbusSmartMeterQueryStrategy());
        this.strategies.put(ModbusRtuBattery.class, new ModbusRtuBatteryQueryStrategy());
        this.strategies.put(ModbusRtuInverter.class, new ModbusRtuInverterQueryStrategy());
        this.strategies.put(ModbusRtuSmartMeter.class, new ModbusRtuSmartMeterQueryStrategy());

        this.strategies.put(RestBattery.class, new RestBatteryQueryStrategy());
        this.strategies.put(RestInverter.class, new RestInverterQueryStrategy());
        this.strategies.put(RestSmartMeter.class, new RestSmartMeterQueryStrategy());

        var messageBattery = new MessageBatteryQueryStrategy<>();
        var messageInverter = new MessageInverterQueryStrategy<>();
        var messageSmartMeter = new MessageSmartMeterQueryStrategy<>();
        this.strategies.put(MqttBattery.class, messageBattery);
        this.strategies.put(MqttInverter.class, messageInverter);
        this.strategies.put(MqttSmartMeter.class, messageSmartMeter);
        this.strategies.put(WebSocketBattery.class, messageBattery);
        this.strategies.put(WebSocketInverter.class, messageInverter);
        this.strategies.put(WebSocketSmartMeter.class, messageSmartMeter);

        this.strategies.put(BraiinsPoolEntity.class, new BraiinsPoolQueryStrategy());
        this.strategies.put(NiceHashPoolEntity.class, new NicehashPoolQueryStrategy());
    }

    public <B extends QueryEntity<Q>, Q extends QueryResult> Q query(B entity) throws Throwable {
        if (!strategies.containsKey(entity.getClass())) {
            throw new IllegalStateException("No query strategy found for type " + entity.getClass().getName());
        }
        Strategy<B, Q> strategy = (Strategy<B, Q>) strategies.get(entity.getClass());
        Objects.requireNonNull(strategy);
        try {
            Q result = strategy.query(this, entity);
            if (result != null) {
                cachedLastQueries.put(entity.getId(), result);
                lastSuccessfulQueries.put(entity.getId(), Instant.now());
                lastFailedQueries.remove(entity.getId());
            }
            return result;
        } catch (Throwable throwable) {
            lastFailedQueries.put(entity.getId(), Instant.now());
            throw throwable;
        }
    }

    public <B extends QueryEntity<Q>, Q extends QueryResult> CompletableFuture<Boolean> ping(B entity, long timeout, TimeUnit timeoutUnit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Strategy<B, Q> strategy = (Strategy<B, Q>) strategies.get(entity.getClass());
                Objects.requireNonNull(strategy);
                strategy.ping(entity);
                return true;
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Ping not successful for entity " + entity, e);
                return false;
            }
        }).completeOnTimeout(false, timeout, timeoutUnit);
    }

    public <B extends QueryEntity<Q>, Q extends QueryResult> Q getLastResult(B entity, Q defaultValue) {
        return (Q) cachedLastQueries.getOrDefault(entity.getId(), defaultValue);
    }

    public boolean hasLastResult(QueryEntity<?> entity) {
        return entity != null && entity.getId() != null && cachedLastQueries.containsKey(entity.getId());
    }

    public Optional<Instant> getLastSuccessfulQueryAt(QueryEntity<?> entity) {
        return entity == null || entity.getId() == null
                ? Optional.empty()
                : Optional.ofNullable(lastSuccessfulQueries.get(entity.getId()));
    }

    public Optional<Instant> getLastFailedQueryAt(QueryEntity<?> entity) {
        return entity == null || entity.getId() == null
                ? Optional.empty()
                : Optional.ofNullable(lastFailedQueries.get(entity.getId()));
    }

    /**
     * @param <E> The entity type
     * @param <R> The query result type
     */
    public interface Strategy<E extends QueryEntity<R>, R extends QueryResult> {
        Logger LOGGER = Logger.getLogger(Strategy.class.getSimpleName());

        R query(EntityQueryService entityQueryService, E entity) throws Throwable;

        @Deprecated
        void ping(E entity) throws Throwable;
    }
}
