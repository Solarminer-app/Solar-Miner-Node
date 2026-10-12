package de.verdox.pv_miner.finance;

import com.influxdb.query.FluxRecord;
import de.verdox.pv_miner.influx.InfluxService;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteInfluxStrategy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MiningEnergyAllocationServiceTest {

    @Test
    void allocatesGridBatteryAndLocalGenerationAtTheSameInstant() {
        MiningEnergyAllocationService.PointAllocation allocation =
                MiningEnergyAllocationService.allocatePoint(10, 2, 5, 2);

        assertEquals(2, allocation.miningKw(), 0.000001);
        assertEquals(1, allocation.gridKw(), 0.000001);
        assertEquals(0.4, allocation.batteryKw(), 0.000001);
        assertEquals(0.6, allocation.localGenerationKw(), 0.000001);
    }

    @Test
    void ignoresGridMeterNoiseWhenAllocatingMiningEnergy() {
        MiningEnergyAllocationService.PointAllocation allocation =
                MiningEnergyAllocationService.allocatePoint(2, 1, 0.03, 0);

        assertEquals(0, allocation.gridKw(), 0.000001);
        assertEquals(1, allocation.localGenerationKw(), 0.000001);
    }

    @Test
    void usesCoveredMinuteIntervalsForTheDailyAllocation() {
        Instant now = Instant.parse("2026-10-12T00:02:00Z");
        InfluxService influx = mock(InfluxService.class);
        when(influx.getInfluxBucket()).thenReturn("raw");
        when(influx.getDownSampledInfluxBucket()).thenReturn("raw_downsampled");
        when(influx.queryRawFlux(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> {
            String query = invocation.getArgument(0);
            if (!query.contains("from(bucket: \"raw\")")) return List.of();
            assertTrue(query.contains("aggregateWindow(every: 1m"));
            return List.of(point(now.minusSeconds(60)), point(now));
        });
        MiningEnergyAllocationService service = new MiningEnergyAllocationService(
                influx, Duration.ofDays(90), Clock.fixed(now, ZoneOffset.UTC));
        PVSiteEntity site = mock(PVSiteEntity.class);
        when(site.getId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000123"));

        MiningEnergyAllocationService.Allocation allocation = service.calculate(
                site, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12), ZoneId.of("UTC"))
                .get(LocalDate.of(2026, 10, 12));

        assertEquals(MiningEnergyAllocationService.QUALITY_MINUTE, allocation.quality());
        assertEquals(2.0 / 30.0, allocation.miningKwh(), 0.000001);
        assertEquals(1.0 / 30.0, allocation.gridKwh(), 0.000001);
        assertEquals(0.4 / 30.0, allocation.batteryKwh(), 0.000001);
        assertEquals(0.6 / 30.0, allocation.localGenerationKwh(), 0.000001);
        assertEquals(1, allocation.coverage(), 0.000001);
    }

    @Test
    void usesBatteryAwareHourlyHistoryWithoutAggregatingItAgain() {
        Instant now = Instant.parse("2026-10-12T12:00:00Z");
        Instant firstWindowEnd = Instant.parse("2026-01-01T01:00:00Z");
        InfluxService influx = mock(InfluxService.class);
        when(influx.getInfluxBucket()).thenReturn("raw");
        when(influx.getDownSampledInfluxBucket()).thenReturn("raw_downsampled");
        when(influx.queryRawFlux(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> {
            String query = invocation.getArgument(0);
            if (!query.contains("from(bucket: \"raw_downsampled\")")) return List.of();
            assertTrue(!query.contains("aggregateWindow"));
            return IntStream.range(0, 24)
                    .mapToObj(hour -> point(firstWindowEnd.plus(hour, ChronoUnit.HOURS)))
                    .toList();
        });
        MiningEnergyAllocationService service = new MiningEnergyAllocationService(
                influx, Duration.ofDays(90), Clock.fixed(now, ZoneOffset.UTC));
        PVSiteEntity site = mock(PVSiteEntity.class);
        when(site.getId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000123"));

        LocalDate date = LocalDate.of(2026, 1, 1);
        MiningEnergyAllocationService.Allocation allocation = service.calculate(site, date, date, ZoneId.of("UTC")).get(date);

        assertEquals(MiningEnergyAllocationService.QUALITY_HOURLY, allocation.quality());
        assertEquals(48, allocation.miningKwh(), 0.000001);
        assertEquals(24, allocation.gridKwh(), 0.000001);
        assertEquals(9.6, allocation.batteryKwh(), 0.000001);
        assertEquals(14.4, allocation.localGenerationKwh(), 0.000001);
    }

    private static FluxRecord point(Instant timestamp) {
        FluxRecord record = mock(FluxRecord.class);
        when(record.getTime()).thenReturn(timestamp);
        when(record.getValueByKey(PVSiteInfluxStrategy.LOADS_POWER_IN_KW)).thenReturn(10.0);
        when(record.getValueByKey(PVSiteInfluxStrategy.MINER_POWER_IN_KW)).thenReturn(2.0);
        when(record.getValueByKey(PVSiteInfluxStrategy.FEED_IN_POWER_IN_KW)).thenReturn(5.0);
        when(record.getValueByKey(PVSiteInfluxStrategy.BATTERY_DISCHARGE_POWER)).thenReturn(2.0);
        return record;
    }
}
