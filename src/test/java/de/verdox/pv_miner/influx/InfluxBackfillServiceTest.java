package de.verdox.pv_miner.influx;

import com.influxdb.client.BucketsApi;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.domain.Bucket;
import com.influxdb.query.FluxRecord;
import de.verdox.pv_miner.pvsite.PVSiteInfluxStrategy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InfluxBackfillServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-21T12:34:56Z");

    @Test
    void emptyRawBucketDoesNotTriggerHistoricalBackfillAndCanEnableRetention() {
        Fixture fixture = fixture();
        when(fixture.influxService.queryRawFluxOrThrow(anyString())).thenReturn(List.of());

        boolean healthy = fixture.service.auditDownsamplingAndRetention();

        assertTrue(healthy);
        verify(fixture.influxService, times(3)).queryRawFluxOrThrow(anyString());
        verify(fixture.influxService, never()).executeRawFluxQuery(anyString());
        verify(fixture.bucketsApi).updateBucket(fixture.rawBucket);
        assertEquals((int) Duration.ofDays(90).toSeconds(),
                fixture.rawBucket.getRetentionRules().getFirst().getEverySeconds());
    }

    @Test
    void failedBackfillPreventsRawRetentionChange() {
        Fixture fixture = fixture();
        FluxRecord uncoveredRawWindow = mock(FluxRecord.class);
        when(uncoveredRawWindow.getTime()).thenReturn(Instant.parse("2026-07-20T00:00:00Z"));
        when(uncoveredRawWindow.getField()).thenReturn(PVSiteInfluxStrategy.PV_POWER_IN_KW);
        when(uncoveredRawWindow.getValueByKey(InfluxEntityStrategy.ENTITY_TAG)).thenReturn("site-1");

        when(fixture.influxService.queryRawFluxOrThrow(anyString())).thenAnswer(invocation -> {
            String query = invocation.getArgument(0);
            if (query.contains("aggregateWindow(every: 1d")
                    && query.contains("_measurement\"] == \"" + PVSiteInfluxStrategy.MEASUREMENT_KEY + "\"")) {
                return List.of(uncoveredRawWindow);
            }
            return List.of();
        });
        when(fixture.influxService.executeRawFluxQuery(contains("pv_all ="))).thenReturn(false);

        boolean healthy = fixture.service.auditDownsamplingAndRetention();

        assertTrue(!healthy);
        verify(fixture.bucketsApi, never()).updateBucket(fixture.rawBucket);
        assertTrue(fixture.rawBucket.getRetentionRules().isEmpty());
    }

    private Fixture fixture() {
        InfluxService influxService = mock(InfluxService.class);
        InfluxDBClient client = mock(InfluxDBClient.class);
        BucketsApi bucketsApi = mock(BucketsApi.class);
        Bucket rawBucket = new Bucket().name("solarminer").retentionRules(List.of());
        Bucket downsampleBucket = new Bucket().name("solarminer_downsampled").retentionRules(List.of());

        when(influxService.getInfluxBucket()).thenReturn("solarminer");
        when(influxService.getDownSampledInfluxBucket()).thenReturn("solarminer_downsampled");
        when(influxService.getInfluxDBClient()).thenReturn(client);
        when(client.getBucketsApi()).thenReturn(bucketsApi);
        when(bucketsApi.findBucketByName(anyString())).thenAnswer(invocation ->
                "solarminer".equals(invocation.getArgument(0)) ? rawBucket : downsampleBucket
        );

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        InfluxBackfillService service = new InfluxBackfillService(
                influxService,
                Duration.ofDays(90),
                clock
        );
        return new Fixture(service, influxService, bucketsApi, rawBucket);
    }

    private record Fixture(
            InfluxBackfillService service,
            InfluxService influxService,
            BucketsApi bucketsApi,
            Bucket rawBucket) {
    }
}
