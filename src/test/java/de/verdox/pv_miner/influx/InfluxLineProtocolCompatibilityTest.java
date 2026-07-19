package de.verdox.pv_miner.influx;

import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.MinerInfluxStrategy;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.pvsite.PVSiteDataDTO;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteInfluxStrategy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InfluxLineProtocolCompatibilityTest {

    @Test
    void pvSiteMeasurementKeepsLegacyMeasurementTagAndFields() {
        WriteApi writeApi = mock(WriteApi.class);
        PVSiteEntity site = mock(PVSiteEntity.class);
        PVSiteDataDTO data = mock(PVSiteDataDTO.class);
        UUID entityId = UUID.fromString("f7a2ef43-781d-4ec4-a88e-29af04e9bad7");
        Instant timestamp = Instant.parse("2026-07-15T09:11:31.055Z");

        when(site.getId()).thenReturn(entityId);
        when(data.getPvPower()).thenReturn(8.1);
        when(data.getImportPowerKw()).thenReturn(1.2);
        when(data.getExportPowerKw()).thenReturn(0.3);
        when(data.getBatterySoC()).thenReturn(55.5f);
        when(data.getBatteryChargePower()).thenReturn(0.4);
        when(data.getBatteryDischargePower()).thenReturn(0.5);
        when(data.getLoadPowerKw()).thenReturn(6.7);
        when(data.getTotalMinerPowerKw()).thenReturn(3.2);

        new PVSiteInfluxStrategy().writeToInflux(writeApi, "solarminer", "SolarMiner", site, data, timestamp);

        String line = captureLine(writeApi, "solarminer", "SolarMiner");
        assertTrue(line.startsWith("pv_site_data,entity=" + entityId + " "));
        assertContainsField(line, "PVPowerInKw", 8.1);
        assertContainsField(line, "FeedInPowerInKw", 1.2);
        assertContainsField(line, "GridConsumptionPower", 0.3);
        assertContainsField(line, "BatteryStateOfCharge", 55.5);
        assertContainsField(line, "BatteryChargePower", 0.4);
        assertContainsField(line, "BatteryDischargePower", 0.5);
        assertContainsField(line, "LoadsPowerInKw", 6.7);
        assertContainsField(line, "MinerPowerInKw", 3.2);
        assertTrue(line.endsWith(" " + timestamp.toEpochMilli()));
    }

    @Test
    void minerMeasurementKeepsLegacyMeasurementTagAndFields() {
        WriteApi writeApi = mock(WriteApi.class);
        MinerEntity<?> miner = mock(MinerEntity.class);
        UUID entityId = UUID.fromString("c7c574de-d54a-419a-909f-9403a2b70a22");
        Instant timestamp = Instant.parse("2026-07-15T09:11:32.055Z");
        MinerStats data = new MinerStats(
                new MinerStats.MinerIdentity("uid", "mac", "model"),
                "Miner 1",
                MinerStats.MinerStatus.MINING,
                2_900,
                500,
                2_800,
                3_500,
                2_850,
                100.0,
                68.5,
                List.of(),
                List.of()
        );
        when(miner.getId()).thenReturn(entityId);

        new MinerInfluxStrategy().writeToInflux(writeApi, "solarminer", "SolarMiner", miner, data, timestamp);

        String line = captureLine(writeApi, "solarminer", "SolarMiner");
        assertTrue(line.startsWith("miner_data,entity=" + entityId + " "));
        assertContainsField(line, "powerUsageWatts", 2850.0);
        assertContainsField(line, "teraHashPerSecond", 100.0);
        assertContainsField(line, "powerTargetWatts", 2900.0);
        assertContainsField(line, "temperatureChipCelsius", 68.5);
        assertContainsField(line, "efficiencyJTh", 28.5);
        assertTrue(line.endsWith(" " + timestamp.toEpochMilli()));
    }

    private String captureLine(WriteApi writeApi, String bucket, String org) {
        ArgumentCaptor<String> lineCaptor = ArgumentCaptor.forClass(String.class);
        verify(writeApi).writeRecord(eq(bucket), eq(org), eq(WritePrecision.MS), lineCaptor.capture());
        return lineCaptor.getValue();
    }

    private void assertContainsField(String line, String field, double value) {
        assertTrue(line.contains(field + "=" + value), () -> "Missing legacy field " + field + " in: " + line);
    }
}
