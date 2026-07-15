package de.verdox.pv_miner.influx;

import com.influxdb.client.WriteApi;
import com.influxdb.client.WriteOptions;
import de.verdox.pv_miner.entity.QueryEntity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class InfluxServiceTest {

    @Test
    void writeBufferIsBoundedAndRetriesAreTimeLimited() {
        WriteOptions options = InfluxService.createWriteOptions();

        assertEquals(500, options.getBatchSize());
        assertEquals(1_000, options.getFlushInterval());
        assertEquals(10_000, options.getBufferLimit());
        assertEquals(5, options.getMaxRetries());
        assertEquals(60_000, options.getMaxRetryTime());
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyMeasurementUsesTheSameLongLivedWriteApi() {
        InfluxService service = spy(new InfluxService());
        WriteApi writeApi = mock(WriteApi.class);
        QueryEntity<QueryResult> entity = mock(QueryEntity.class);
        QueryResult result = mock(QueryResult.class);
        InfluxEntityStrategy<QueryEntity<QueryResult>, QueryResult> strategy = mock(InfluxEntityStrategy.class);
        Instant timestamp = Instant.parse("2026-07-15T09:11:31Z");
        Instant nextTimestamp = timestamp.plusSeconds(1);

        ReflectionTestUtils.setField(service, "writeApi", writeApi);
        ReflectionTestUtils.setField(service, "influxBucket", "solarminer");
        ReflectionTestUtils.setField(service, "influxOrg", "SolarMiner");
        doReturn(strategy).when(service).findStrategy(entity);

        service.writeDataToApi(entity, result, timestamp);
        service.writeDataToApi(entity, result, nextTimestamp);

        verify(strategy, times(1)).writeToInflux(
                same(writeApi),
                eq("solarminer"),
                eq("SolarMiner"),
                same(entity),
                same(result),
                same(timestamp)
        );
        verify(strategy, times(1)).writeToInflux(
                same(writeApi),
                eq("solarminer"),
                eq("SolarMiner"),
                same(entity),
                same(result),
                same(nextTimestamp)
        );
    }
}
