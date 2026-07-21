package de.verdox.pv_miner.configfetcher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigFetcherServiceTest {

    @Test
    void bundledProfilesAreAvailableWithoutDownloadingTheCommunityRepository() {
        ConfigFetcherService service = new ConfigFetcherService();

        service.loadBundledConfigs();

        assertFalse(service.getCachedProfiles().isEmpty());
        assertTrue(service.getCachedProfiles().stream()
                .anyMatch(profile -> profile.supportedProtocols().contains("Modbus-RTU")));
        assertTrue(service.getCachedProfiles().stream()
                .anyMatch(profile -> profile.supportedProtocols().contains("MQTT")));
        assertTrue(service.getMessagePVConfig("mqtt", "solaranzeige").isPresent());
    }

    @Test
    void resolvesLegacyDisplayNamesToCanonicalProfileNames() {
        ConfigFetcherService service = new ConfigFetcherService();
        service.loadBundledConfigs();

        assertTrue(service.getModbusConfig("Acrel ADW300").isPresent());
        assertEquals("acrel-adw300", service.resolveProfileName("Modbus-TCP", "Acrel ADW300").orElseThrow());
    }
}
