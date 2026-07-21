package de.verdox.pv_miner.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import de.verdox.pv_miner.dto.PVConfigDtos.ConnectionTestDto;
import de.verdox.pv_miner.dto.PVConfigDtos.CreateProfileRequest;
import de.verdox.pv_miner.dto.PVConfigDtos.ModbusCatalogDto;
import de.verdox.pv_miner.dto.PVConfigDtos.ModbusProfileDto;
import de.verdox.pv_miner.dto.PVConfigDtos.ModbusTestRequest;
import de.verdox.pv_miner.dto.PVConfigDtos.RestCatalogDto;
import de.verdox.pv_miner.dto.PVConfigDtos.RestProfileDto;
import de.verdox.pv_miner.dto.PVConfigDtos.RestTestRequest;
import de.verdox.pv_miner.pvconfig.PVConfigService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/config/pv")
@CrossOrigin(origins = "http://localhost:3000", exposedHeaders = HttpHeaders.CONTENT_DISPOSITION)
@Tag(name = "PV profiles")
public class PVConfigController {
    private static final String DEFAULT_REST_TEMPLATE = "ha_pvsite";
    private static final String DEFAULT_MODBUS_TEMPLATE = "pvsite";

    private final PVConfigService configService;

    public PVConfigController(PVConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/rest/catalog")
    public RestCatalogDto getRestCatalog() {
        return configService.getRestCatalog();
    }

    @PostMapping("/rest/profiles")
    public RestProfileDto createRestProfile(@RequestBody CreateProfileRequest request) {
        return configService.createRestProfile(
                request == null ? null : request.name(),
                request == null ? null : request.templateId()
        );
    }

    @GetMapping("/rest/profiles/{name}")
    public RestProfileDto getRestProfile(
            @PathVariable String name,
            @RequestParam(defaultValue = DEFAULT_REST_TEMPLATE) String templateId
    ) {
        return configService.loadRestProfile(name, templateId);
    }

    @PutMapping("/rest/profiles/{name}")
    public RestProfileDto saveRestProfile(@PathVariable String name, @RequestBody RestProfileDto profile) {
        return configService.saveRestProfile(name, profile);
    }

    @DeleteMapping("/rest/profiles/{name}")
    public ResponseEntity<Void> deleteRestProfile(
            @PathVariable String name,
            @RequestParam(defaultValue = DEFAULT_REST_TEMPLATE) String templateId
    ) {
        configService.deleteRestProfile(name, templateId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/rest/profiles/{name}/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> exportRestProfile(
            @PathVariable String name,
            @RequestParam(defaultValue = DEFAULT_REST_TEMPLATE) String templateId
    ) {
        return jsonDownload(name, configService.exportRestProfile(name, templateId));
    }

    @PostMapping(value = "/rest/profiles/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public RestProfileDto importRestProfile(
            @RequestParam String name,
            @RequestParam(defaultValue = DEFAULT_REST_TEMPLATE) String templateId,
            @RequestBody String json
    ) {
        return configService.importRestProfile(name, templateId, json);
    }

    @PostMapping("/rest/community/{name}")
    public RestProfileDto downloadRestCommunityProfile(
            @PathVariable String name,
            @RequestParam(defaultValue = DEFAULT_REST_TEMPLATE) String templateId
    ) {
        return configService.downloadRestCommunityProfile(name, templateId);
    }

    @PostMapping("/rest/test")
    public ConnectionTestDto testRestConnection(@RequestBody RestTestRequest request) {
        return configService.testRestConnection(request);
    }

    @GetMapping({"/modbus/tcp/catalog", "/modbus/rtu/catalog"})
    public ModbusCatalogDto getModbusCatalog() {
        return configService.getModbusCatalog();
    }

    @PostMapping({"/modbus/tcp/profiles", "/modbus/rtu/profiles"})
    public ModbusProfileDto createModbusProfile(@RequestBody CreateProfileRequest request) {
        return configService.createModbusProfile(
                request == null ? null : request.name(),
                request == null ? null : request.templateId()
        );
    }

    @GetMapping({"/modbus/tcp/profiles/{name}", "/modbus/rtu/profiles/{name}"})
    public ModbusProfileDto getModbusProfile(
            @PathVariable String name,
            @RequestParam(defaultValue = DEFAULT_MODBUS_TEMPLATE) String templateId
    ) {
        return configService.loadModbusProfile(name, templateId);
    }

    @PutMapping({"/modbus/tcp/profiles/{name}", "/modbus/rtu/profiles/{name}"})
    public ModbusProfileDto saveModbusProfile(@PathVariable String name, @RequestBody ModbusProfileDto profile) {
        return configService.saveModbusProfile(name, profile);
    }

    @DeleteMapping({"/modbus/tcp/profiles/{name}", "/modbus/rtu/profiles/{name}"})
    public ResponseEntity<Void> deleteModbusProfile(
            @PathVariable String name,
            @RequestParam(defaultValue = DEFAULT_MODBUS_TEMPLATE) String templateId
    ) {
        configService.deleteModbusProfile(name, templateId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = {"/modbus/tcp/profiles/{name}/export", "/modbus/rtu/profiles/{name}/export"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> exportModbusProfile(
            @PathVariable String name,
            @RequestParam(defaultValue = DEFAULT_MODBUS_TEMPLATE) String templateId
    ) {
        return jsonDownload(name, configService.exportModbusProfile(name, templateId));
    }

    @PostMapping(value = {"/modbus/tcp/profiles/import", "/modbus/rtu/profiles/import"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ModbusProfileDto importModbusProfile(
            @RequestParam String name,
            @RequestParam(defaultValue = DEFAULT_MODBUS_TEMPLATE) String templateId,
            @RequestBody String json
    ) {
        return configService.importModbusProfile(name, templateId, json);
    }

    @PostMapping({"/modbus/tcp/community/{name}", "/modbus/rtu/community/{name}"})
    public ModbusProfileDto downloadModbusCommunityProfile(
            @PathVariable String name,
            @RequestParam(defaultValue = DEFAULT_MODBUS_TEMPLATE) String templateId
    ) {
        return configService.downloadModbusCommunityProfile(name, templateId);
    }

    @PostMapping("/modbus/tcp/test")
    public ConnectionTestDto testModbusConnection(@RequestBody ModbusTestRequest request) {
        return configService.testModbusConnection(request);
    }

    @GetMapping("/{protocol:mqtt|websocket}/catalog")
    public RestCatalogDto getMessageCatalog(@PathVariable String protocol) {
        return configService.getMessageCatalog(protocol);
    }

    @PostMapping("/{protocol:mqtt|websocket}/profiles")
    public RestProfileDto createMessageProfile(@PathVariable String protocol, @RequestBody CreateProfileRequest request) {
        return configService.createMessageProfile(protocol, request == null ? null : request.name(), request == null ? null : request.templateId());
    }

    @GetMapping("/{protocol:mqtt|websocket}/profiles/{name}")
    public RestProfileDto getMessageProfile(@PathVariable String protocol, @PathVariable String name,
                                            @RequestParam(defaultValue = DEFAULT_REST_TEMPLATE) String templateId) {
        return configService.loadMessageProfile(protocol, name, templateId);
    }

    @PutMapping("/{protocol:mqtt|websocket}/profiles/{name}")
    public RestProfileDto saveMessageProfile(@PathVariable String protocol, @PathVariable String name,
                                             @RequestBody RestProfileDto profile) {
        return configService.saveMessageProfile(protocol, name, profile);
    }

    @DeleteMapping("/{protocol:mqtt|websocket}/profiles/{name}")
    public ResponseEntity<Void> deleteMessageProfile(@PathVariable String protocol, @PathVariable String name,
                                                     @RequestParam(defaultValue = DEFAULT_REST_TEMPLATE) String templateId) {
        configService.deleteMessageProfile(protocol, name, templateId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/{protocol:mqtt|websocket}/profiles/{name}/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> exportMessageProfile(@PathVariable String protocol, @PathVariable String name) {
        return jsonDownload(name, configService.exportMessageProfile(protocol, name));
    }

    @PostMapping(value = "/{protocol:mqtt|websocket}/profiles/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public RestProfileDto importMessageProfile(@PathVariable String protocol, @RequestParam String name,
                                               @RequestParam(defaultValue = DEFAULT_REST_TEMPLATE) String templateId,
                                               @RequestBody String json) {
        return configService.importMessageProfile(protocol, name, templateId, json);
    }

    @PostMapping("/{protocol:mqtt|websocket}/community/{name}")
    public RestProfileDto downloadMessageCommunityProfile(@PathVariable String protocol, @PathVariable String name,
                                                          @RequestParam(defaultValue = DEFAULT_REST_TEMPLATE) String templateId) {
        return configService.downloadMessageCommunityProfile(protocol, name, templateId);
    }

    private ResponseEntity<String> jsonDownload(String profileName, String json) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(profileName + ".json", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(json);
    }
}
