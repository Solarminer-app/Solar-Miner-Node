package de.verdox.pv_miner.entity;

import de.verdox.pv_miner.influx.QueryResult;
import de.verdox.pv_miner.miner.MinerQueryStrategy;
import de.verdox.pv_miner_extensions.agent.AgentMinerEntity;
import de.verdox.pv_miner_extensions.braiins.miner.BraiinsOSAsicMinerEntity;
import de.verdox.pv_miner_extensions.braiins.pool.BraiinsPoolEntity;
import de.verdox.pv_miner_extensions.braiins.pool.BraiinsPoolQueryStrategy;
import de.verdox.pv_miner_extensions.modbus.ModbusPVSite;
import de.verdox.pv_miner_extensions.modbus.ModbusPVSiteQueryStrategy;
import de.verdox.pv_miner_extensions.nicehash.NiceHashPoolEntity;
import de.verdox.pv_miner_extensions.nicehash.NicehashPoolQueryStrategy;
import de.verdox.pv_miner_extensions.restpv.RestPVSite;
import de.verdox.pv_miner_extensions.restpv.RestPVSiteQueryStrategy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class EntityQueryService {
    private static final Logger LOGGER = Logger.getLogger(EntityQueryService.class.getSimpleName());
    private final Map<Class<? extends QueryEntity<?>>, Strategy<?, ? extends QueryResult>> strategies = new HashMap<>();
    private final Map<UUID, QueryResult> cachedLastQueries = new WeakHashMap<>();

    public EntityQueryService() {
        MinerQueryStrategy minerQueryStrategy = new MinerQueryStrategy();

        this.strategies.put(AgentMinerEntity.class, minerQueryStrategy);
        this.strategies.put(BraiinsOSAsicMinerEntity.class, minerQueryStrategy);

        this.strategies.put(ModbusPVSite.class, new ModbusPVSiteQueryStrategy());
        this.strategies.put(RestPVSite.class, new RestPVSiteQueryStrategy());

        this.strategies.put(BraiinsPoolEntity.class, new BraiinsPoolQueryStrategy());
        this.strategies.put(NiceHashPoolEntity.class, new NicehashPoolQueryStrategy());
    }

    public <B extends QueryEntity<Q>, Q extends QueryResult> Q query(B entity) throws Throwable {
        if (!strategies.containsKey(entity.getClass())) {
            throw new IllegalStateException("No query strategy found for type " + entity.getClass().getName());
        }
        Strategy<B, Q> strategy = (Strategy<B, Q>) strategies.get(entity.getClass());
        Objects.requireNonNull(strategy);
        Q result = strategy.query(this, entity);
        if (result != null) {
            cachedLastQueries.put(entity.getId(), result);
        }
        return result;
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
