package de.verdox.pv_miner.influx;

import de.verdox.pv_miner.miner.MinerStatisticsAccumulator;
import de.verdox.pv_miner.pvsite.PVStatisticsAccumulator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfluxDownsamplingQueriesTest {

    @Test
    void dailyTasksOnlyProcessCompletedUtcDays() {
        String pvTask = new PVStatisticsAccumulator()
                .generateDownsamplingTaskQuery("raw", "down", "pv_site_data");
        String minerTask = new MinerStatisticsAccumulator()
                .generateDownsamplingTaskQuery("raw", "down", "miner_data");

        assertCompletedDayRange(pvTask);
        assertCompletedDayRange(minerTask);
    }

    @Test
    void hourlyTaskOnlyProcessesCompletedHours() {
        String task = InfluxDownsamplingQueries.generatePvHourlyTask("raw", "down");

        assertTrue(task.contains("date.truncate(t: now(), unit: 1h)"));
        assertTrue(task.contains("range(start: start, stop: stop)"));
        assertTrue(task.contains("aggregateWindow(every: 1h, fn: mean"));
        assertFalse(task.contains("range(start: -2h)"));
    }

    private void assertCompletedDayRange(String task) {
        assertTrue(task.contains("date.truncate(t: now(), unit: 1d)"));
        assertTrue(task.contains("range(start: start, stop: stop)"));
        assertFalse(task.contains("range(start: -2d)"));
    }
}
