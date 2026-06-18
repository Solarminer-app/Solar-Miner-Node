package de.verdox.pv_miner.util;

import java.time.*;
import java.time.temporal.ChronoUnit;

public class TimeUtil {
    public static Instant getTime(LocalTime localTime) {
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate today = LocalDate.now();
        LocalDateTime dateTime = LocalDateTime.of(today, localTime);
        ZonedDateTime zonedDateTime = dateTime.atZone(zoneId);
        return zonedDateTime.toInstant();
    }

    public static Instant getTime(LocalDateTime dateTime) {
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate today = LocalDate.now();
        ZonedDateTime zonedDateTime = dateTime.atZone(zoneId);
        return zonedDateTime.toInstant();
    }

    public static long getStartOfDayInUtc(long timestampUnixMillis) {
        Instant instant = Instant.ofEpochMilli(timestampUnixMillis);
        ZonedDateTime utcDateTime = instant.atZone(ZoneOffset.UTC);
        ZonedDateTime startOfDay = utcDateTime.truncatedTo(ChronoUnit.DAYS);
        return startOfDay.toEpochSecond() * 1000;
    }

    public static long getEndOfDayInUtc(long timestampUnixMillis) {
        Instant instant = Instant.ofEpochMilli(timestampUnixMillis);
        ZonedDateTime utcDateTime = instant.atZone(ZoneOffset.UTC);
        ZonedDateTime endOfDay = utcDateTime.truncatedTo(ChronoUnit.DAYS).plusDays(1).minusSeconds(1);
        return endOfDay.toEpochSecond() * 1000;
    }
}
