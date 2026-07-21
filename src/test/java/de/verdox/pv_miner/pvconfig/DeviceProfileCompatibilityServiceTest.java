package de.verdox.pv_miner.pvconfig;

import de.verdox.pv_miner.dto.SetupRequests;
import de.verdox.pv_miner.entity.EntityService;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.setup.SetupService;
import de.verdox.pv_miner_extensions.device.modbus.inverter.ModbusInverter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceProfileCompatibilityServiceTest {
    @Test
    void acceptsLegacyDisplayNameWhenCanonicalProfileAndSectionStillExist() {
        SetupService setupService = mock(SetupService.class);
        when(setupService.getPvDeviceProfiles(SetupService.MODBUS, "")).thenReturn(List.of(
                new SetupRequests.PvDeviceProfileDto(SetupService.MODBUS, "acrel-adw300", List.of(
                        new SetupRequests.PvDeviceSectionDto("inverter", "modbus_inverter", "Inverter", "INVERTER")
                ))
        ));
        DeviceProfileCompatibilityService service = new DeviceProfileCompatibilityService(setupService, mock(EntityService.class));
        PVSiteEntity site = siteWithModbusInverter("Acrel ADW300", "inverter");

        assertTrue(service.inspect(site).compatible());
    }

    @Test
    void reportsDeviceWhenItsSectionCannotBeMapped() {
        SetupService setupService = mock(SetupService.class);
        when(setupService.getPvDeviceProfiles(SetupService.MODBUS, "")).thenReturn(List.of(
                new SetupRequests.PvDeviceProfileDto(SetupService.MODBUS, "acrel-adw300", List.of(
                        new SetupRequests.PvDeviceSectionDto("inverter", "modbus_inverter", "Inverter", "INVERTER")
                ))
        ));
        DeviceProfileCompatibilityService service = new DeviceProfileCompatibilityService(setupService, mock(EntityService.class));
        PVSiteEntity site = siteWithModbusInverter("Removed profile", "legacy_section");

        var result = service.inspect(site);
        assertFalse(result.compatible());
        assertFalse(result.issues().getFirst().candidates().isEmpty());
    }

    private PVSiteEntity siteWithModbusInverter(String profileName, String sectionKey) {
        ModbusInverter inverter = new ModbusInverter();
        inverter.setId(UUID.randomUUID());
        inverter.setName("Test inverter");
        inverter.setModbusConfigName(profileName);
        inverter.setSectionKey(sectionKey);
        PVSiteEntity site = new PVSiteEntity();
        site.getInverters().add(inverter);
        return site;
    }
}
