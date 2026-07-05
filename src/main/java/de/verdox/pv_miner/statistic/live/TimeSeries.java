package de.verdox.pv_miner.statistic.live;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;

/**
 * A thread safe series that saves discrete values over a period of time.
 * Extremely useful when a large data set is retrieved from an influx db and the values need to be cached.
 * A tree structure is used to have efficient searching and inserting of values for specific time stamps.
 * The tree is sorted such that the most recent timestamps are most right in the tree.
 *
 * @param <T> the data to be saved per timestamp
 */
public class TimeSeries<T> implements Collection<Map.Entry<Long, T>> {
    private final ConcurrentNavigableMap<Long, T> timeSeriesMap;
    private final TimeUnit accuracy;
    private final int limit;

    private TimeSeries(TimeUnit accuracy, ConcurrentNavigableMap<Long, T> subMap, int limit) {
        this.accuracy = accuracy;
        this.timeSeriesMap = subMap;
        this.limit = limit;
    }

    public TimeSeries(TimeUnit accuracy, int limit) {
        this.accuracy = accuracy;
        this.limit = limit;
        this.timeSeriesMap = new ConcurrentSkipListMap<>();
    }

    /**
     * Inserts new data into this series.
     *
     * @param timeMillis the time of the new data
     * @param data       the data
     */
    public void addData(long timeMillis, T data) {
        timeSeriesMap.put(withAccuracy(timeMillis), data);

        if (limit > -1 && timeSeriesMap.size() >= limit) {
            timeSeriesMap.pollFirstEntry();
        }
    }

    public long getMostEarliestTimeStamp() {
        return timeSeriesMap.firstKey();
    }

    public long getMostRecentTimeStamp() {
        return timeSeriesMap.lastKey();
    }

    /**
     * Inserts values from another series into this series. Only values with timestamps that are not saved into this series are copied into this series.
     *
     * @param other the other series.
     */
    public void addValues(TimeSeries<T> other) {
        for (Map.Entry<Long, T> entry : other.timeSeriesMap.entrySet()) {
            timeSeriesMap.putIfAbsent(withAccuracy(entry.getKey()), entry.getValue());
        }
    }

    /**
     * Returns a new time series with values only between from and two. This operation has O(1) complexity
     *
     * @param fromMillis the inclusive from value
     * @param toMillis   the inclusive to value
     * @return the new time series
     */
    public TimeSeries<T> getValuesFromTo(long fromMillis, long toMillis) {
        return new TimeSeries<>(this.accuracy, timeSeriesMap.subMap(withAccuracy(fromMillis), true, withAccuracy(toMillis), true), this.limit);
    }

    public boolean hasDataForInterval(long fromMillis, long toMillis) {
        NavigableMap<Long, T> subMap = timeSeriesMap.subMap(withAccuracy(fromMillis), true, withAccuracy(toMillis), true);
        if (subMap.isEmpty()) {
            return false;
        }

        long firstKey = subMap.firstKey();
        long lastKey = subMap.lastKey();
        return firstKey == fromMillis && lastKey == toMillis;
    }

    /**
     * Merges two time series objects by creating a new time series with values from both inputs merged into one.
     * The new time series is also sorted from back to front with the latest values in the series being the newest ones.
     *
     * @param accuracy the merge accuracy
     * @param first    the first series
     * @param second   the second series
     * @param <T>      the data value
     * @return the merged series
     */
    public static <T> TimeSeries<T> merge(TimeUnit accuracy, int limit, TimeSeries<T> first, TimeSeries<T> second) {
        TimeSeries<T> merged = new TimeSeries<>(accuracy, limit);
        first.timeSeriesMap.forEach(merged::addData);
        second.timeSeriesMap.forEach(merged::addData);
        return merged;
    }

    private long withAccuracy(long timeMillis) {
        long unitInMillis = accuracy.toMillis(1);
        return ((timeMillis + unitInMillis - 1) / unitInMillis) * unitInMillis;
    }

    @Override
    public int size() {
        return timeSeriesMap.size();
    }

    @Override
    public boolean isEmpty() {
        return timeSeriesMap.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return timeSeriesMap.containsValue(o);
    }

    @Override
    public Iterator<Map.Entry<Long, T>> iterator() {
        return timeSeriesMap.entrySet().iterator();
    }

    @Override
    public Object[] toArray() {
        return timeSeriesMap.values().toArray();
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        return timeSeriesMap.values().toArray(a);
    }

    @Override
    public boolean add(Map.Entry<Long, T> t) {
        throw new UnsupportedOperationException("Use addData(time, data) instead.");
    }

    @Override
    public boolean remove(Object o) {
        return timeSeriesMap.values().remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return timeSeriesMap.values().containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends Map.Entry<Long, T>> c) {
        throw new UnsupportedOperationException("Use addValues(other) instead.");
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return timeSeriesMap.values().removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return timeSeriesMap.values().retainAll(c);
    }

    @Override
    public void clear() {
        timeSeriesMap.clear();
    }

    @Override
    public String toString() {
        return timeSeriesMap.toString();
    }
}
