package de.verdox.pv_miner.pvconfig;

import de.verdox.pv_miner.configfetcher.ConfigFetcherService;
import de.verdox.pv_miner.dto.PVConfigDtos.ModbusFieldDto;
import de.verdox.pv_miner.dto.PVConfigDtos.ModbusProfileDto;
import de.verdox.pv_miner.dto.PVConfigDtos.RestFieldDto;
import de.verdox.pv_miner.dto.PVConfigDtos.RestProfileDto;
import de.verdox.pv_miner.dto.PVConfigDtos.RestTestRequest;
import de.verdox.pv_miner_extensions.device.modbus.ModbusConfigStorage;
import de.verdox.pv_miner_extensions.device.message.MessageConfigStorage;
import de.verdox.pv_miner_extensions.device.rest.RestConfigStorage;
import de.verdox.solarminer.modbustcp.ModbusConfigCreatorTemplate;
import de.verdox.solarminer.rest.RestConfigCreatorTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PVConfigServiceTest {
    private RestConfigStorage restStorage;
    private ModbusConfigStorage modbusStorage;
    private MessageConfigStorage messageStorage;
    private PVConfigService service;

    @BeforeEach
    void setUp() {
        restStorage = mock(RestConfigStorage.class);
        modbusStorage = mock(ModbusConfigStorage.class);
        messageStorage = mock(MessageConfigStorage.class);
        service = new PVConfigService(restStorage, modbusStorage, messageStorage, mock(ConfigFetcherService.class), false);
    }

    @Test
    void rejectsPathTraversalProfileNamesBeforeStorageAccess() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createRestProfile("../outside", RestConfigCreatorTemplate.HOME_ASSISTANT_PV.id()));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(restStorage);
    }

    @Test
    void rejectsUnexpectedOrMissingTemplateFields() {
        RestProfileDto profile = new RestProfileDto("Profile", RestConfigCreatorTemplate.HOME_ASSISTANT_PV.id(), List.of(
                new RestFieldDto("pv_power", "kw", "/api/value", "GET", "JSON", "state", 1, "x", "float")
        ));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.saveRestProfile("Profile", profile));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void rejectsOversizedImports() {
        String oversizedJson = "x".repeat(PVConfigService.MAX_IMPORT_CHARACTERS + 1);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.importRestProfile("Profile", RestConfigCreatorTemplate.HOME_ASSISTANT_PV.id(), oversizedJson));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getStatusCode());
    }

    @Test
    void rejectsPublicTargetsForDeviceConnectionTests() {
        RestTestRequest request = new RestTestRequest("http://8.8.8.8", "", RestConfigCreatorTemplate.HOME_ASSISTANT_PV.id(), validRestFields());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.testRestConnection(request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void rejectsModbusRegistersOutsideTheProtocolRange() {
        List<ModbusFieldDto> fields = validModbusFields();
        fields.set(0, new ModbusFieldDto("pv_power", "kw", -1, 2, 1, "x", "int32", "READ_HOLDING_REGISTER", "BIG_ENDIAN"));
        ModbusProfileDto profile = new ModbusProfileDto("Profile", ModbusConfigCreatorTemplate.PV_SITE.id(), 0, null, fields);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.saveModbusProfile("Profile", profile));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void createsMqttProfilesWithSharedMessageFields() {
        RestProfileDto profile = service.createMessageProfile(
                "mqtt", "Local meter", RestConfigCreatorTemplate.SMART_METER.id());

        assertEquals(RestConfigCreatorTemplate.SMART_METER.requiredFields().size(), profile.fields().size());
        assertEquals("solarminer/device/telemetry", profile.fields().getFirst().urlExtension());
        assertEquals("JSON", profile.fields().getFirst().responseType());
    }

    private List<RestFieldDto> validRestFields() {
        return RestConfigCreatorTemplate.HOME_ASSISTANT_PV.requiredFields().stream()
                .map(field -> new RestFieldDto(field.field(), field.unit(), "/api/" + field.field(), "GET", "JSON", "state", 1, "x", "float"))
                .toList();
    }

    private List<ModbusFieldDto> validModbusFields() {
        return new java.util.ArrayList<>(ModbusConfigCreatorTemplate.PV_SITE.requiredFields().stream()
                .map(field -> new ModbusFieldDto(field.field(), field.unit(), 40_000, 2, 1, "x", "int32", "READ_HOLDING_REGISTER", "BIG_ENDIAN"))
                .toList());
    }
}
