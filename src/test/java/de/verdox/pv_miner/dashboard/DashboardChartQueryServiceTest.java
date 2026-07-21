package de.verdox.pv_miner.dashboard;

import com.influxdb.query.FluxRecord;
import de.verdox.pv_miner.influx.InfluxService;
import de.verdox.pv_miner.influx.InfluxUtil;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteInfluxStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardChartQueryServiceTest {
    private final InfluxService influxService = mock(InfluxService.class);
    private final DashboardChartQueryService service = new DashboardChartQueryService(
            influxService,
            Duration.ofMinutes(5),
            Duration.ofDays(7)
    );

    @AfterEach
    void closeService() {
        service.close();
    }

    @Test
    void combinesFieldsIntoOneLiveQueryAndCachesTheResult() {
        PVSiteEntity site = mock(PVSiteEntity.class);
        when(site.getId()).thenReturn(UUID.randomUUID());

        Instant liveTime = Instant.parse("2026-07-15T10:00:00Z");
        Instant historyTime = Instant.parse("2026-07-15T09:00:00Z");
        List<FluxRecord> liveRecords = List.of(
                record(liveTime, PVSiteInfluxStrategy.PV_POWER_IN_KW, 4.2),
                record(liveTime, PVSiteInfluxStrategy.FEED_IN_POWER_IN_KW, 1.1),
                record(liveTime, PVSiteInfluxStrategy.GRID_CONSUMPTION_POWER, 0.4),
                record(liveTime, PVSiteInfluxStrategy.LOADS_POWER_IN_KW, 2.3),
                record(liveTime, PVSiteInfluxStrategy.MINER_POWER_IN_KW, 1.8)
        );
        List<FluxRecord> historyRecords = List.of(
                record(historyTime, PVSiteInfluxStrategy.PV_POWER_IN_KW, 3.7)
        );
        AtomicInteger queryNumber = new AtomicInteger();
        List<String> builtQueries = new ArrayList<>();

        when(influxService.getDownSampledInfluxBucket()).thenReturn("solarminer_downsampled");
        when(influxService.queryMeasurementData(
                eq("solarminer_downsampled"),
                eq(PVSiteInfluxStrategy.HOURLY_MEASUREMENT_KEY),
                eq(site),
                any(Instant.class),
                any(Instant.class),
                eq(List.of(PVSiteInfluxStrategy.PV_POWER_IN_KW))
        )).thenReturn(historyRecords);

        doAnswer(invocation -> {
                    Consumer<InfluxUtil.InfluxQueryBuilder> queryCustomizer = invocation.getArgument(3);
                    InfluxUtil.InfluxQueryBuilder builder = new InfluxUtil.InfluxQueryBuilder("solarminer")
                            .setMeasurement(PVSiteInfluxStrategy.MEASUREMENT_KEY)
                            .setTimeRange(invocation.getArgument(1), invocation.getArgument(2));
                    queryCustomizer.accept(builder);
                    builtQueries.add(builder.build());
                    queryNumber.incrementAndGet();
                    return liveRecords;
                })
                .when(influxService)
                .queryDataFromApi(
                        eq(site),
                        any(Instant.class),
                        any(Instant.class),
                        org.mockito.ArgumentMatchers.<Consumer<InfluxUtil.InfluxQueryBuilder>>any()
                );

        long todayStart = Instant.parse("2026-07-15T00:00:00Z").toEpochMilli();
        long todayEnd = Instant.parse("2026-07-15T23:59:59Z").toEpochMilli();
        long siteStart = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();

        var first = service.load(site, todayStart, todayEnd, siteStart).join();
        var cached = service.load(site, todayStart, todayEnd, siteStart).join();

        assertEquals(first, cached);
        assertEquals(1, first.live().pvPower().size());
        assertEquals(4.2, first.live().pvPower().getFirst().value());
        assertEquals(1, first.live().consumption().size());
        assertEquals(2.3, first.live().consumption().getFirst().value());
        assertEquals(1.1, first.live().gridImport().getFirst().value());
        assertEquals(0.4, first.live().gridExport().getFirst().value());
        assertEquals(1.8, first.live().minerConsumption().getFirst().value());
        assertEquals(1, first.pvHistory().size());
        assertEquals(3.7, first.pvHistory().getFirst().value());
        assertEquals(1, builtQueries.size());
        assertTrue(builtQueries.getFirst().contains(PVSiteInfluxStrategy.PV_POWER_IN_KW));
        assertTrue(builtQueries.getFirst().contains(PVSiteInfluxStrategy.FEED_IN_POWER_IN_KW));
        assertTrue(builtQueries.getFirst().contains(PVSiteInfluxStrategy.GRID_CONSUMPTION_POWER));
        assertTrue(builtQueries.getFirst().contains(PVSiteInfluxStrategy.LOADS_POWER_IN_KW));
        assertTrue(builtQueries.getFirst().contains(PVSiteInfluxStrategy.MINER_POWER_IN_KW));
        assertFalse(builtQueries.getFirst().contains("group(columns: [\"_time\"])"));
        verify(influxService, times(1)).queryDataFromApi(
                eq(site),
                any(Instant.class),
                any(Instant.class),
                org.mockito.ArgumentMatchers.<Consumer<InfluxUtil.InfluxQueryBuilder>>any()
        );
        verify(influxService).queryMeasurementData(
                eq("solarminer_downsampled"),
                eq(PVSiteInfluxStrategy.HOURLY_MEASUREMENT_KEY),
                eq(site),
                eq(Instant.ofEpochMilli(todayEnd).minus(Duration.ofDays(7))),
                eq(Instant.ofEpochMilli(todayEnd)),
                eq(List.of(PVSiteInfluxStrategy.PV_POWER_IN_KW))
        );
    }

    private FluxRecord record(Instant time, String field, double value) {
        FluxRecord record = mock(FluxRecord.class);
        when(record.getTime()).thenReturn(time);
        when(record.getField()).thenReturn(field);
        when(record.getValue()).thenReturn(value);
        return record;
    }
}
