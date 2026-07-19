package de.verdox.pv_miner.statistic.daily;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class DailyStatistic {
    private long startOfDayUnixTimestampUTC;
    private long fromMillis;
    private long toMillis;
}
