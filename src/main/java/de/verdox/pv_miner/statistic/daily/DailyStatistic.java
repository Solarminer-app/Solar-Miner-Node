package de.verdox.pv_miner.statistic.daily;

public abstract class DailyStatistic {
    private long startOfDayUnixTimestampUTC;
    private long fromMillis;
    private long toMillis;

    public long getStartOfDayUnixTimestampUTC() {
        return startOfDayUnixTimestampUTC;
    }

    public void setStartOfDayUnixTimestampUTC(long startOfDayUnixTimestampUTC) {
        this.startOfDayUnixTimestampUTC = startOfDayUnixTimestampUTC;
    }

    public long getFromMillis() {
        return fromMillis;
    }

    public void setFromMillis(long fromMillis) {
        this.fromMillis = fromMillis;
    }

    public long getToMillis() {
        return toMillis;
    }

    public void setToMillis(long toMillis) {
        this.toMillis = toMillis;
    }
}
