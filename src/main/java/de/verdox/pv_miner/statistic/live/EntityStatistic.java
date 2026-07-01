package de.verdox.pv_miner.statistic.live;

import de.verdox.pv_miner.entity.QueryEntity;
import de.verdox.pv_miner.influx.QueryResult;
import lombok.Getter;
import org.reactivestreams.Subscription;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class EntityStatistic<E extends QueryEntity<Q>, Q extends QueryResult, T> {
    private final EntityStatisticType<E, Q, T> statisticType;
    private final E entity;
    private Disposable liveDataSubscription;
    @Getter
    private final TimeSeries<T> values;
    private final Sinks.Many<Long> updateSink;

    private final Set<Long> collectedErrorTimestamps = new HashSet<>();

    public EntityStatistic(EntityStatisticType<E, Q, T> statisticType, E entity) {
        this.values = new TimeSeries<>(statisticType.getAccuracy(), statisticType.getLimit());
        this.statisticType = statisticType;
        this.entity = entity;
        updateSink = Sinks.many().replay().latest();
    }

    /**
     * Used to subscribe to changes in the statistics
     *
     * @param consumer the consumer to invoke on each value (onNext signal)
     * @return a new {@link Disposable} that can be used to cancel the underlying {@link Subscription}
     */
    public Disposable subscribe(Consumer<Long> consumer) {
        return updateSink.asFlux().subscribe(consumer);
    }

    void setLiveDataSubscription(Disposable liveDataSubscription) {
        this.liveDataSubscription = liveDataSubscription;
    }

    public void triggerStatisticsUpdate() {
        updateSink.tryEmitNext(System.currentTimeMillis());
    }

    public void notifyPopulationError(long from, long to){
        collectedErrorTimestamps.add(from);
        collectedErrorTimestamps.add(to);
    }

    public void notifyPopulationDone(long from, long to) {
        collectedErrorTimestamps.remove(from);
        collectedErrorTimestamps.remove(to);
    }

    public boolean hasDataForInterval(long from, long to) {
        if(collectedErrorTimestamps.contains(from) || collectedErrorTimestamps.contains(to)) {
            return false;
        }
        return getValues().hasDataForInterval(from, to);
    }

    public void close() {
        if(liveDataSubscription != null) {
            liveDataSubscription.dispose();
        }
    }
}
