package de.verdox.pv_miner.influx;

import com.influxdb.client.WriteApi;
import com.influxdb.client.WriteOptions;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.TasksApi;
import com.influxdb.client.domain.Task;
import de.verdox.pv_miner.entity.QueryEntity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InfluxServiceTest {

    @Test
    void writeBufferIsBoundedAndRetriesAreTimeLimited() {
        WriteOptions options = InfluxService.createWriteOptions();

        assertEquals(500, options.getBatchSize());
        assertEquals(5_000, options.getFlushInterval());
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

    @Test
    void taskRegistrationUpdatesOneTaskAndRemovesDuplicates() {
        InfluxService service = new InfluxService();
        InfluxDBClient client = mock(InfluxDBClient.class);
        TasksApi tasksApi = mock(TasksApi.class);
        Task first = mock(Task.class);
        Task duplicate = mock(Task.class);
        String taskName = "Downsample_PV_Hourly";
        String newFlux = "option task = {name: \"Downsample_PV_Hourly\", every: 1h}";

        ReflectionTestUtils.setField(service, "influxDBClient", client);
        when(client.getTasksApi()).thenReturn(tasksApi);
        when(tasksApi.findTasks()).thenReturn(List.of(first, duplicate));
        when(first.getName()).thenReturn(taskName);
        when(duplicate.getName()).thenReturn(taskName);
        when(first.getFlux()).thenReturn("old flux");

        service.registerTask(taskName, newFlux);

        verify(first).setFlux(newFlux);
        verify(tasksApi).updateTask(first);
        verify(tasksApi).deleteTask(duplicate);
    }
}
