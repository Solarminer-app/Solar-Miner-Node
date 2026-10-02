package de.verdox.pv_miner.setup;

import de.verdox.pv_miner.discovery.DiscoveryService;
import de.verdox.pv_miner.discovery.PvDiscoveryScanner;
import de.verdox.pv_miner.dto.SetupRequests;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SetupDiscoveryTest {
    private SetupService service(DiscoveryService discovery) {
        return new SetupService(null, null, null, null, null, null, null, null, null, discovery, new PvDiscoveryScanner());
    }

    @Test
    void honorsConfiguredModbusPortAndDeviceIdAndNeverScansLoopback() {
        var discovery = mock(DiscoveryService.class);
        var report = service(discovery).scanPvDevices(new SetupRequests.PvDiscoveryRequest("MODBUS_TCP", "192.168.1.", 1502, 7));
        assertTrue(report.complete());
        assertEquals(254, report.checkedHosts());
        verify(discovery, times(254)).inspectModbusDevice(startsWith("192.168.1."), eq(1502), eq(7), eq(200));
        verifyNoMoreInteractions(discovery);
    }

    @Test
    void honorsCustomRestPortInsteadOfProbingAllDefaultPorts() {
        var discovery = mock(DiscoveryService.class);
        var report = service(discovery).scanPvDevices(new SetupRequests.PvDiscoveryRequest("REST_API", "10.1.2.", 8888, null));
        assertTrue(report.complete());
        verify(discovery, times(254)).inspectRestDevice(startsWith("10.1.2."), eq(8888), eq(200));
        verifyNoMoreInteractions(discovery);
    }

    @Test
    void rejectsBadTargetsAndSettingsBeforeNetworkAccess() {
        var discovery = mock(DiscoveryService.class);
        var service = service(discovery);
        for (var request : java.util.List.of(
                new SetupRequests.PvDiscoveryRequest("MODBUS_TCP", "8.8.8.", 502, 1),
                new SetupRequests.PvDiscoveryRequest("MODBUS_TCP", "10.1.2.", 0, 1),
                new SetupRequests.PvDiscoveryRequest("MODBUS_TCP", "10.1.2.", 502, 256),
                new SetupRequests.PvDiscoveryRequest("MQTT", "10.1.2.", 502, 1)))
            assertThrows(ResponseStatusException.class, () -> service.scanPvDevices(request));
        verifyNoInteractions(discovery);
    }
}
