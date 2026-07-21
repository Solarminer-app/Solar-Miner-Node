package de.verdox.pv_miner.pvconfig;

import de.verdox.pv_miner.configfetcher.ConfigFetcherService;
import de.verdox.pv_miner.dto.PVConfigDtos;
import de.verdox.pv_miner.dto.PVConfigDtos.ConnectionTestDto;
import de.verdox.pv_miner.dto.PVConfigDtos.FieldDefinitionDto;
import de.verdox.pv_miner.dto.PVConfigDtos.FieldTestDto;
import de.verdox.pv_miner.dto.PVConfigDtos.ModbusCatalogDto;
import de.verdox.pv_miner.dto.PVConfigDtos.ModbusFieldDto;
import de.verdox.pv_miner.dto.PVConfigDtos.ModbusFingerprintDto;
import de.verdox.pv_miner.dto.PVConfigDtos.ModbusProfileDto;
import de.verdox.pv_miner.dto.PVConfigDtos.ModbusTestRequest;
import de.verdox.pv_miner.dto.PVConfigDtos.OperationTypeDto;
import de.verdox.pv_miner.dto.PVConfigDtos.RestCatalogDto;
import de.verdox.pv_miner.dto.PVConfigDtos.RestFieldDto;
import de.verdox.pv_miner.dto.PVConfigDtos.RestProfileDto;
import de.verdox.pv_miner.dto.PVConfigDtos.RestTestRequest;
import de.verdox.pv_miner.dto.PVConfigDtos.TemplateDto;
import de.verdox.pv_miner_extensions.device.modbus.ModbusConfigStorage;
import de.verdox.pv_miner_extensions.device.rest.RestConfigStorage;
import de.verdox.pv_miner_extensions.device.message.MessageConfigStorage;
import de.verdox.solarminer.RequiredField;
import de.verdox.solarminer.formula.FormulaEngine;
import de.verdox.solarminer.modbustcp.ModbusConfig;
import de.verdox.solarminer.modbustcp.ModbusConfigCreatorTemplate;
import de.verdox.solarminer.modbustcp.ModbusParameterType;
import de.verdox.solarminer.modbustcp.ModbusReadOperationType;
import de.verdox.solarminer.modbustcp.TCPModbusClient;
import de.verdox.solarminer.rest.RestConfigCreatorTemplate;
import de.verdox.solarminer.rest.RestHttpMethod;
import de.verdox.solarminer.rest.RestPVClient;
import de.verdox.solarminer.rest.RestPVConfig;
import de.verdox.solarminer.rest.RestParameterType;
import de.verdox.solarminer.rest.RestResponseType;
import de.verdox.vserializer.json.JsonSerializerContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PVConfigService {
    static final int MAX_IMPORT_CHARACTERS = 1_000_000;
    private static final int MAX_PROFILE_NAME_LENGTH = 80;
    private static final int MAX_FIELDS = 256;
    private static final Pattern PROFILE_NAME = Pattern.compile("[\\p{L}\\p{N} ._()\\[\\]-]{1," + MAX_PROFILE_NAME_LENGTH + "}");
    private static final Pattern FORMULA_CHARACTERS = Pattern.compile("[xX0-9a-zA-Z_$+\\-*/%(). ]*");
    private static final Pattern FORMULA_REFERENCE = Pattern.compile("\\$([a-zA-Z0-9_]+)");
    private static final Logger LOGGER = Logger.getLogger(PVConfigService.class.getName());

    private final RestConfigStorage restStorage;
    private final ModbusConfigStorage modbusStorage;
    private final MessageConfigStorage messageStorage;
    private final ConfigFetcherService configFetcherService;
    private final boolean allowPublicDeviceHosts;
    private final Semaphore connectionTestSlots = new Semaphore(4, true);

    @Autowired
    public PVConfigService(RestConfigStorage restStorage,
                           ModbusConfigStorage modbusStorage,
                           MessageConfigStorage messageStorage,
                           ConfigFetcherService configFetcherService,
                           @Value("${solarminer.config.allow-public-device-hosts:false}") boolean allowPublicDeviceHosts) {
        this.restStorage = restStorage;
        this.modbusStorage = modbusStorage;
        this.messageStorage = messageStorage;
        this.configFetcherService = configFetcherService;
        this.allowPublicDeviceHosts = allowPublicDeviceHosts;
    }

    /** Keeps embedders and older tests source-compatible while message profiles remain optional. */
    public PVConfigService(RestConfigStorage restStorage, ModbusConfigStorage modbusStorage,
                           ConfigFetcherService configFetcherService, boolean allowPublicDeviceHosts) {
        this(restStorage, modbusStorage, new MessageConfigStorage(configFetcherService), configFetcherService, allowPublicDeviceHosts);
    }

    public RestCatalogDto getRestCatalog() {
        return new RestCatalogDto(
                RestConfigCreatorTemplate.getAll().stream()
                        .map(template -> toTemplate(template.id(), template.name(), template.requiredFields()))
                        .toList(),
                loadNames(restStorage::getSavedConfigs),
                communityNames("Rest-API"),
                Arrays.stream(RestHttpMethod.values()).map(Enum::name).toList(),
                Arrays.stream(RestResponseType.values()).map(Enum::name).toList(),
                List.of("int", "long", "float", "double")
        );
    }

    public ModbusCatalogDto getModbusCatalog() {
        return new ModbusCatalogDto(
                ModbusConfigCreatorTemplate.getAll().stream()
                        .map(entry -> toTemplate(entry.id(), entry.name(), entry.requiredFields()))
                        .toList(),
                loadNames(modbusStorage::getSavedConfigs),
                communityNames("Modbus-TCP"),
                Arrays.stream(ModbusParameterType.values()).map(ModbusParameterType::identifier).sorted().toList(),
                Arrays.stream(ModbusReadOperationType.values())
                        .map(value -> new OperationTypeDto(value.name(), value.getId()))
                        .toList(),
                List.of("BIG_ENDIAN", "LITTLE_ENDIAN")
        );
    }

    public synchronized RestProfileDto createRestProfile(String rawName, String templateId) {
        String name = profileName(rawName);
        RestConfigCreatorTemplate template = restTemplate(templateId);
        try {
            RestPVConfig existing = restStorage.doesConfigExistOnDisk(name)
                    ? restStorage.loadConfig(name)
                    : new RestPVConfig(Map.of());
            if (findRestSection(existing, template.id()) != null) {
                throw conflict("This profile already contains the selected device type");
            }
            RestPVConfig config = mergeRestSection(existing, buildRestConfig(template, emptyRestFields(template)), template);
            restStorage.save(name, config);
            return toRestProfile(name, template, config);
        } catch (IOException exception) {
            throw storageFailure("Could not create REST profile", exception);
        }
    }

    public RestProfileDto loadRestProfile(String rawName, String templateId) {
        String name = profileName(rawName);
        RestConfigCreatorTemplate template = restTemplate(templateId);
        try {
            return toRestProfile(name, template, restStorage.loadConfig(name));
        } catch (NoSuchElementException exception) {
            throw notFound("REST profile not found");
        } catch (IOException exception) {
            throw storageFailure("Could not load REST profile", exception);
        }
    }

    public synchronized RestProfileDto saveRestProfile(String rawName, RestProfileDto profile) {
        String name = profileName(rawName);
        requireMatchingName(name, profile == null ? null : profile.name());
        RestConfigCreatorTemplate template = restTemplate(profile == null ? null : profile.templateId());
        RestPVConfig replacement = buildRestConfig(template, profile.fields());
        try {
            requireExistingRestProfile(template, name);
            RestPVConfig config = mergeRestSection(restStorage.loadConfig(name), replacement, template);
            restStorage.save(name, config);
            return toRestProfile(name, template, config);
        } catch (IOException exception) {
            throw storageFailure("Could not save REST profile", exception);
        }
    }

    public synchronized void deleteRestProfile(String rawName, String templateId) {
        String name = profileName(rawName);
        RestConfigCreatorTemplate template = restTemplate(templateId);
        try {
            RestPVConfig existing = restStorage.loadConfig(name);
            Map<String, RestPVConfig.ConfigSection> sections = new LinkedHashMap<>(existing.getSections());
            boolean removed = sections.entrySet().removeIf(entry -> template.id().equals(entry.getValue().getTemplateId()));
            if (!removed) throw notFound("REST profile section not found");
            if (sections.isEmpty()) restStorage.delete(name); else restStorage.save(name, new RestPVConfig(sections));
        } catch (IOException exception) {
            throw storageFailure("Could not delete REST profile", exception);
        }
    }

    public String exportRestProfile(String rawName, String templateId) {
        RestConfigCreatorTemplate template = restTemplate(templateId);
        String name = profileName(rawName);
        try {
            RestPVConfig config = restStorage.loadConfig(name);
            JsonSerializerContext context = new JsonSerializerContext();
            return context.toJsonString(RestPVConfig.SERIALIZER.serialize(context, config));
        } catch (NoSuchElementException exception) {
            throw notFound("REST profile not found");
        } catch (IOException exception) {
            throw storageFailure("Could not export REST profile", exception);
        }
    }

    public synchronized RestProfileDto importRestProfile(String rawName, String templateId, String json) {
        String name = profileName(rawName);
        RestConfigCreatorTemplate template = restTemplate(templateId);
        RestPVConfig imported = deserializeRest(json);
        RestPVConfig validated = buildRestConfig(template, toRestProfile(name, template, imported).fields());
        try {
            restStorage.save(name, imported);
            return toRestProfile(name, template, imported);
        } catch (IOException exception) {
            throw storageFailure("Could not import REST profile", exception);
        }
    }

    public synchronized RestProfileDto downloadRestCommunityProfile(String rawName, String templateId) {
        String name = profileName(rawName);
        RestConfigCreatorTemplate template = restTemplate(templateId);
        RestPVConfig community = configFetcherService.getRestPVConfig(name)
                .orElseThrow(() -> notFound("Community REST profile not found"));
        RestPVConfig validated = buildRestConfig(template, toRestProfile(name, template, community).fields());
        try {
            restStorage.save(name, community);
            return toRestProfile(name, template, community);
        } catch (IOException exception) {
            throw storageFailure("Could not save community REST profile", exception);
        }
    }

    public synchronized ModbusProfileDto createModbusProfile(String rawName, String templateId) {
        String name = profileName(rawName);
        ModbusConfigCreatorTemplate template = modbusTemplate(templateId);
        try {
            ModbusConfig existing = modbusStorage.doesConfigExistOnDisk(name)
                    ? modbusStorage.loadConfig(name)
                    : new ModbusConfig(null, Map.of(), 0);
            if (findModbusSection(existing, template.id()) != null) {
                throw conflict("This profile already contains the selected device type");
            }
            ModbusConfig config = mergeModbusSection(existing, buildModbusConfig(template, emptyModbusFields(template), null, existing.getAddressOffset()), template);
            modbusStorage.save(name, config);
            return toModbusProfile(name, template, config);
        } catch (IOException exception) {
            throw storageFailure("Could not create Modbus profile", exception);
        }
    }

    public ModbusProfileDto loadModbusProfile(String rawName, String templateId) {
        String name = profileName(rawName);
        ModbusConfigCreatorTemplate template = modbusTemplate(templateId);
        try {
            return toModbusProfile(name, template, modbusStorage.loadConfig(name));
        } catch (NoSuchElementException exception) {
            throw notFound("Modbus profile not found");
        } catch (IOException exception) {
            throw storageFailure("Could not load Modbus profile", exception);
        }
    }

    public synchronized ModbusProfileDto saveModbusProfile(String rawName, ModbusProfileDto profile) {
        String name = profileName(rawName);
        requireMatchingName(name, profile == null ? null : profile.name());
        ModbusConfigCreatorTemplate template = modbusTemplate(profile == null ? null : profile.templateId());
        ModbusConfig replacement = buildModbusConfig(template, profile.fields(), profile.fingerprint(), profile.addressOffset());
        try {
            requireExistingModbusProfile(template, name);
            ModbusConfig config = mergeModbusSection(modbusStorage.loadConfig(name), replacement, template);
            modbusStorage.save(name, config);
            return toModbusProfile(name, template, config);
        } catch (IOException exception) {
            throw storageFailure("Could not save Modbus profile", exception);
        }
    }

    public synchronized void deleteModbusProfile(String rawName, String templateId) {
        String name = profileName(rawName);
        ModbusConfigCreatorTemplate template = modbusTemplate(templateId);
        try {
            ModbusConfig existing = modbusStorage.loadConfig(name);
            Map<String, ModbusConfig.ConfigSection> sections = new LinkedHashMap<>(existing.getSections());
            boolean removed = sections.entrySet().removeIf(entry -> template.id().equals(entry.getValue().getTemplateId()));
            if (!removed) throw notFound("Modbus profile section not found");
            if (sections.isEmpty()) modbusStorage.delete(name);
            else modbusStorage.save(name, new ModbusConfig(existing.getFingerprint(), sections, existing.getAddressOffset()));
        } catch (IOException exception) {
            throw storageFailure("Could not delete Modbus profile", exception);
        }
    }

    public String exportModbusProfile(String rawName, String templateId) {
        ModbusConfigCreatorTemplate template = modbusTemplate(templateId);
        String name = profileName(rawName);
        try {
            ModbusConfig config = modbusStorage.loadConfig(name);
            JsonSerializerContext context = new JsonSerializerContext();
            return context.toJsonString(ModbusConfig.SERIALIZER.serialize(context, config));
        } catch (NoSuchElementException exception) {
            throw notFound("Modbus profile not found");
        } catch (IOException exception) {
            throw storageFailure("Could not export Modbus profile", exception);
        }
    }

    public synchronized ModbusProfileDto importModbusProfile(String rawName, String templateId, String json) {
        String name = profileName(rawName);
        ModbusConfigCreatorTemplate template = modbusTemplate(templateId);
        ModbusConfig imported = deserializeModbus(json);
        ModbusProfileDto dto = toModbusProfile(name, template, imported);
        ModbusConfig validated = buildModbusConfig(template, dto.fields(), dto.fingerprint(), dto.addressOffset());
        try {
            modbusStorage.save(name, imported);
            return toModbusProfile(name, template, imported);
        } catch (IOException exception) {
            throw storageFailure("Could not import Modbus profile", exception);
        }
    }

    public synchronized ModbusProfileDto downloadModbusCommunityProfile(String rawName, String templateId) {
        String name = profileName(rawName);
        ModbusConfigCreatorTemplate template = modbusTemplate(templateId);
        ModbusConfig community = configFetcherService.getModbusConfig(name)
                .orElseThrow(() -> notFound("Community Modbus profile not found"));
        ModbusProfileDto dto = toModbusProfile(name, template, community);
        ModbusConfig validated = buildModbusConfig(template, dto.fields(), dto.fingerprint(), dto.addressOffset());
        try {
            modbusStorage.save(name, community);
            return toModbusProfile(name, template, community);
        } catch (IOException exception) {
            throw storageFailure("Could not save community Modbus profile", exception);
        }
    }

    public ConnectionTestDto testRestConnection(RestTestRequest request) {
        if (request == null) {
            throw badRequest("Test request is required");
        }
        URI baseUri = restBaseUri(request.baseUrl());
        RestConfigCreatorTemplate template = request.templateId() == null || request.templateId().isBlank()
                ? RestConfigCreatorTemplate.HOME_ASSISTANT_PV
                : restTemplate(request.templateId());
        RestPVConfig config = buildRestConfig(template, request.fields());
        acquireTestSlot();
        try (RestPVClient client = new RestPVClient(baseUri.toString(), safeToken(request.apiToken()))) {
            client.ping();
            Map<String, FieldTestDto> values = testRestFields(client, config);
            return new ConnectionTestDto(true, false, "connected", values);
        } catch (Exception exception) {
            LOGGER.log(Level.FINE, "REST device test failed for " + baseUri.getHost(), exception);
            return new ConnectionTestDto(false, false, "connection_failed", Map.of());
        } finally {
            connectionTestSlots.release();
        }
    }

    public ConnectionTestDto testModbusConnection(ModbusTestRequest request) {
        if (request == null) {
            throw badRequest("Test request is required");
        }
        String host = deviceHost(request.host());
        int port = integerRange(request.port(), 1, 65_535, "Port");
        int slaveId = integerRange(request.slaveId(), 1, 247, "Slave ID");
        ModbusConfigCreatorTemplate template = request.templateId() == null || request.templateId().isBlank()
                ? ModbusConfigCreatorTemplate.PV_SITE
                : modbusTemplate(request.templateId());
        ModbusConfig config = buildModbusConfig(template, request.fields(), request.fingerprint(), request.addressOffset());
        acquireTestSlot();
        try (TCPModbusClient client = new TCPModbusClient(host, port, slaveId)) {
            Map<String, FieldTestDto> values = testModbusFields(client, config);
            boolean fingerprintMatches = config.getFingerprint() != null
                    && client.verifyFingerprint(config.getAddressOffset(), config.getFingerprint());
            return new ConnectionTestDto(true, fingerprintMatches, "connected", values);
        } catch (Exception exception) {
            LOGGER.log(Level.FINE, "Modbus device test failed for " + host, exception);
            return new ConnectionTestDto(false, false, "connection_failed", Map.of());
        } finally {
            connectionTestSlots.release();
        }
    }

    private Map<String, FieldTestDto> testRestFields(RestPVClient client, RestPVConfig config) {
        Map<String, FieldTestDto> results = new LinkedHashMap<>();
        Map<String, Double> cache = new HashMap<>();
        RestPVConfig.ConfigSection section = config.getSections().values().stream().findFirst()
                .orElseThrow(() -> badRequest("REST profile has no section"));
        for (String field : section.getEntries().keySet()) {
            try {
                results.put(field, FieldTestDto.number(readRestValue(field, client, section, cache, new HashSet<>())));
            } catch (Exception exception) {
                results.put(field, FieldTestDto.error("read_failed"));
            }
        }
        return results;
    }

    private double readRestValue(String field, RestPVClient client, RestPVConfig.ConfigSection section,
                                 Map<String, Double> cache, Set<String> visiting) throws Exception {
        if (cache.containsKey(field)) {
            return cache.get(field);
        }
        if (!visiting.add(field)) {
            throw new IllegalArgumentException("Circular formula reference");
        }
        RestPVConfig.Entry<?> entry = section.getEntryForId(field);
        RestPVConfig.Entry<?> rawEntry = new RestPVConfig.Entry<>(
                entry.urlExtension(), entry.httpMethod(), entry.responseType(), entry.dataPath(),
                1.0f, "x", entry.restParameterType()
        );
        double raw = client.read(rawEntry);
        double evaluated = FormulaEngine.evaluate(raw, entry.formula(), referenced -> {
            try {
                return readRestValue(referenced, client, section, cache, new HashSet<>(visiting));
            } catch (Exception exception) {
                throw new IllegalArgumentException(exception);
            }
        });
        double result = evaluated * entry.scaleFactor();
        cache.put(field, result);
        return result;
    }

    private Map<String, FieldTestDto> testModbusFields(TCPModbusClient client, ModbusConfig config) {
        Map<String, FieldTestDto> results = new LinkedHashMap<>();
        Map<String, Double> cache = new HashMap<>();
        ModbusConfig.ConfigSection section = config.getSections().values().stream().findFirst()
                .orElseThrow(() -> badRequest("Modbus profile has no section"));
        for (String field : section.getEntries().keySet()) {
            try {
                results.put(field, FieldTestDto.number(readModbusValue(field, client, section, config.getAddressOffset(), cache, new HashSet<>())));
            } catch (Exception exception) {
                results.put(field, FieldTestDto.error("read_failed"));
            }
        }
        return results;
    }

    private double readModbusValue(String field, TCPModbusClient client, ModbusConfig.ConfigSection section,
                                   int addressOffset,
                                   Map<String, Double> cache, Set<String> visiting) throws Exception {
        if (cache.containsKey(field)) {
            return cache.get(field);
        }
        if (!visiting.add(field)) {
            throw new IllegalArgumentException("Circular formula reference");
        }
        ModbusConfig.Entry<?> entry = section.getEntryForId(field);
        ModbusConfig.Entry<?> rawEntry = new ModbusConfig.Entry<>(
                entry.startAddress(), entry.size(), 1.0f, "x", entry.modbusParameterType(),
                entry.readOperationType(), entry.byteOrder()
        );
        Object rawObject = client.read(addressOffset, rawEntry);
        double raw = rawObject instanceof Number number
                ? number.doubleValue()
                : Double.parseDouble(String.valueOf(rawObject).trim());
        double evaluated = FormulaEngine.evaluate(raw, entry.formula(), referenced -> {
            try {
                return readModbusValue(referenced, client, section, addressOffset, cache, new HashSet<>(visiting));
            } catch (Exception exception) {
                throw new IllegalArgumentException(exception);
            }
        });
        double result = evaluated * entry.scaleFactor();
        cache.put(field, result);
        return result;
    }

    private RestPVConfig buildRestConfig(RestConfigCreatorTemplate template, List<RestFieldDto> fields) {
        Map<String, RestFieldDto> byName = indexFields(fields, RestFieldDto::field);
        requireExactFields(template.requiredFields(), byName.keySet());
        Set<String> allowedReferences = requiredFieldNames(template.requiredFields());
        Map<String, RestPVConfig.Entry<?>> entries = new LinkedHashMap<>();
        for (RequiredField required : template.requiredFields()) {
            RestFieldDto field = byName.get(required.field());
            String extension = requiredText(field.urlExtension(), "URL extension", 512);
            if (!extension.startsWith("/") || extension.startsWith("//") || extension.contains("://") || hasControlCharacters(extension)) {
                throw badRequest("URL extension must be a relative path");
            }
            String dataPath = optionalText(field.dataPath(), 256);
            RestResponseType responseType = enumValue(RestResponseType.class, field.responseType(), "response type");
            if (responseType != RestResponseType.PLAIN_TEXT && dataPath.isBlank()) {
                throw badRequest("A data path is required for JSON and XML responses");
            }
            entries.put(required.field(), new RestPVConfig.Entry<>(
                    extension,
                    enumValue(RestHttpMethod.class, field.httpMethod(), "HTTP method"),
                    responseType,
                    dataPath,
                    scaleFactor(field.scaleFactor()),
                    formula(field.formula(), allowedReferences),
                    restParameterType(field.parameterType())
            ));
        }
        RestPVConfig.ConfigSection section = new RestPVConfig.ConfigSection(template.id(), template.name(), entries);
        return new RestPVConfig(Map.of(template.id(), section));
    }

    private RestPVConfig buildMessageConfig(RestConfigCreatorTemplate template, List<RestFieldDto> fields) {
        Map<String, RestFieldDto> byName = indexFields(fields, RestFieldDto::field);
        requireExactFields(template.requiredFields(), byName.keySet());
        Set<String> allowedReferences = requiredFieldNames(template.requiredFields());
        Map<String, RestPVConfig.Entry<?>> entries = new LinkedHashMap<>();
        for (RequiredField required : template.requiredFields()) {
            RestFieldDto field = byName.get(required.field());
            String source = requiredText(field.urlExtension(), "Topic/message source", 512);
            if (hasControlCharacters(source)) throw badRequest("Topic/message source contains control characters");
            String dataPath = optionalText(field.dataPath(), 256);
            RestResponseType responseType = enumValue(RestResponseType.class, field.responseType(), "response type");
            if (responseType != RestResponseType.PLAIN_TEXT && dataPath.isBlank()) throw badRequest("A data path is required for JSON and XML messages");
            entries.put(required.field(), new RestPVConfig.Entry<>(source, RestHttpMethod.GET, responseType, dataPath,
                    scaleFactor(field.scaleFactor()), formula(field.formula(), allowedReferences), restParameterType(field.parameterType())));
        }
        return new RestPVConfig(Map.of(template.id(), new RestPVConfig.ConfigSection(template.id(), template.name(), entries)));
    }

    public RestCatalogDto getMessageCatalog(String protocol) {
        return new RestCatalogDto(
                RestConfigCreatorTemplate.getAll().stream()
                        .map(template -> toTemplate(template.id(), template.name(), template.requiredFields())).toList(),
                loadNames(() -> messageStorage.getSavedConfigs(messageProtocol(protocol))),
                communityNames(profileProtocol(protocol)),
                List.of(RestHttpMethod.GET.name()),
                Arrays.stream(RestResponseType.values()).map(Enum::name).toList(),
                List.of("int", "long", "float", "double")
        );
    }

    public synchronized RestProfileDto createMessageProfile(String protocol, String rawName, String templateId) {
        String transport = messageProtocol(protocol);
        String name = profileName(rawName);
        RestConfigCreatorTemplate template = restTemplate(templateId);
        try {
            RestPVConfig existing = messageStorage.exists(transport, name) ? messageStorage.loadConfig(transport, name) : new RestPVConfig(Map.of());
            if (findRestSection(existing, template.id()) != null) throw conflict("This profile already contains the selected device type");
            RestPVConfig config = mergeRestSection(existing, buildMessageConfig(template, emptyMessageFields(template, transport)), template);
            messageStorage.save(transport, name, config);
            return toRestProfile(name, template, config);
        } catch (IOException exception) { throw storageFailure("Could not create message profile", exception); }
    }

    public RestProfileDto loadMessageProfile(String protocol, String rawName, String templateId) {
        String name = profileName(rawName);
        RestConfigCreatorTemplate template = restTemplate(templateId);
        try { return toRestProfile(name, template, messageStorage.loadConfig(messageProtocol(protocol), name)); }
        catch (NoSuchElementException exception) { throw notFound("Message profile not found"); }
        catch (IOException exception) { throw storageFailure("Could not load message profile", exception); }
    }

    public synchronized RestProfileDto saveMessageProfile(String protocol, String rawName, RestProfileDto profile) {
        String transport = messageProtocol(protocol);
        String name = profileName(rawName);
        requireMatchingName(name, profile == null ? null : profile.name());
        RestConfigCreatorTemplate template = restTemplate(profile == null ? null : profile.templateId());
        RestPVConfig replacement = buildMessageConfig(template, profile.fields());
        try {
            if (!messageStorage.exists(transport, name)) throw notFound("Message profile not found");
            RestPVConfig existing = messageStorage.loadConfig(transport, name);
            if (findRestSection(existing, template.id()) == null) throw notFound("Message profile section not found");
            RestPVConfig config = mergeRestSection(existing, replacement, template);
            messageStorage.save(transport, name, config);
            return toRestProfile(name, template, config);
        } catch (IOException exception) { throw storageFailure("Could not save message profile", exception); }
    }

    public synchronized void deleteMessageProfile(String protocol, String rawName, String templateId) {
        String transport = messageProtocol(protocol);
        String name = profileName(rawName);
        RestConfigCreatorTemplate template = restTemplate(templateId);
        try {
            RestPVConfig existing = messageStorage.loadConfig(transport, name);
            Map<String, RestPVConfig.ConfigSection> sections = new LinkedHashMap<>(existing.getSections());
            if (!sections.entrySet().removeIf(entry -> template.id().equals(entry.getValue().getTemplateId()))) throw notFound("Message profile section not found");
            if (sections.isEmpty()) messageStorage.delete(transport, name); else messageStorage.save(transport, name, new RestPVConfig(sections));
        } catch (IOException exception) { throw storageFailure("Could not delete message profile", exception); }
    }

    public String exportMessageProfile(String protocol, String rawName) {
        try {
            RestPVConfig config = messageStorage.loadConfig(messageProtocol(protocol), profileName(rawName));
            JsonSerializerContext context = new JsonSerializerContext();
            return context.toJsonString(RestPVConfig.SERIALIZER.serialize(context, config));
        } catch (IOException exception) { throw storageFailure("Could not export message profile", exception); }
    }

    public synchronized RestProfileDto importMessageProfile(String protocol, String rawName, String templateId, String json) {
        String name = profileName(rawName);
        RestConfigCreatorTemplate template = restTemplate(templateId);
        RestPVConfig imported = deserializeRest(json);
        buildMessageConfig(template, toRestProfile(name, template, imported).fields());
        try { messageStorage.save(messageProtocol(protocol), name, imported); return toRestProfile(name, template, imported); }
        catch (IOException exception) { throw storageFailure("Could not import message profile", exception); }
    }

    public synchronized RestProfileDto downloadMessageCommunityProfile(String protocol, String rawName, String templateId) {
        String name = profileName(rawName);
        RestConfigCreatorTemplate template = restTemplate(templateId);
        RestPVConfig community = configFetcherService.getRestPVConfig(name)
                .orElseThrow(() -> notFound("Community message profile not found"));
        buildMessageConfig(template, toRestProfile(name, template, community).fields());
        try {
            messageStorage.save(messageProtocol(protocol), name, community);
            return toRestProfile(name, template, community);
        } catch (IOException exception) { throw storageFailure("Could not save community message profile", exception); }
    }

    private ModbusConfig buildModbusConfig(ModbusConfigCreatorTemplate template, List<ModbusFieldDto> fields,
                                            ModbusFingerprintDto fingerprintDto, int addressOffset) {
        Map<String, ModbusFieldDto> byName = indexFields(fields, ModbusFieldDto::field);
        requireExactFields(template.requiredFields(), byName.keySet());
        Set<String> allowedReferences = requiredFieldNames(template.requiredFields());
        Map<String, ModbusConfig.Entry<?>> entries = new LinkedHashMap<>();
        for (RequiredField required : template.requiredFields()) {
            ModbusFieldDto field = byName.get(required.field());
            entries.put(required.field(), modbusEntry(field.startAddress(), field.size(), field.scaleFactor(),
                    field.formula(), field.parameterType(), field.operationType(), field.byteOrder(), allowedReferences));
        }
        ModbusConfig.ConfigSection section = new ModbusConfig.ConfigSection(template.id(), template.name(), entries);
        return new ModbusConfig(fingerprint(fingerprintDto), Map.of(template.id(), section), addressOffset);
    }

    private ModbusConfig.Entry<?> modbusEntry(int address, int size, double scale, String formula,
                                               String parameterType, String operationType, String byteOrder,
                                               Set<String> allowedReferences) {
        int validAddress = integerRange(address, 0, 65_535, "Register address");
        int validSize = integerRange(size, 1, 125, "Register size");
        if ((long) validAddress + validSize > 65_536L) {
            throw badRequest("Register range exceeds the Modbus address space");
        }
        ModbusParameterType<?> type = modbusParameterType(parameterType);
        ensureRegisterSize(type, validSize);
        return new ModbusConfig.Entry<>(
                validAddress,
                validSize,
                scaleFactor(scale),
                formula(formula, allowedReferences),
                type,
                enumValue(ModbusReadOperationType.class, operationType, "operation type"),
                byteOrder(byteOrder)
        );
    }

    private ModbusConfig.Fingerprint fingerprint(ModbusFingerprintDto dto) {
        if (dto == null || dto.address() == null || dto.expectedValue() == null || dto.expectedValue().isBlank()) {
            return null;
        }
        String expected = requiredText(dto.expectedValue(), "Fingerprint value", 128);
        ModbusConfig.Entry<?> entry = modbusEntry(
                dto.address(), dto.size() == null ? 1 : dto.size(), 1.0, "x",
                dto.parameterType(), dto.operationType(), dto.byteOrder(), Set.of()
        );
        return new ModbusConfig.Fingerprint(
                entry.startAddress(), entry.size(), entry.modbusParameterType(), entry.readOperationType(),
                entry.byteOrder(), expected
        );
    }

    private RestProfileDto toRestProfile(String name, RestConfigCreatorTemplate template, RestPVConfig config) {
        RestPVConfig.ConfigSection section = findRestSection(config, template.id());
        if (section == null) throw notFound("REST profile does not contain the selected device type");
        List<RestFieldDto> fields = new ArrayList<>();
        for (RequiredField required : template.requiredFields()) {
            RestPVConfig.Entry<?> entry;
            try {
                entry = section.getEntryForId(required.field());
            } catch (NoSuchElementException exception) {
                throw badRequest("Profile is missing required field " + required.field());
            }
            fields.add(new RestFieldDto(
                    required.field(), required.unit(), entry.urlExtension(), entry.httpMethod().name(),
                    entry.responseType().name(), entry.dataPath(), entry.scaleFactor(), entry.formula(),
                    entry.restParameterType().identifier()
            ));
        }
        return new RestProfileDto(name, template.id(), fields);
    }

    private ModbusProfileDto toModbusProfile(String name, ModbusConfigCreatorTemplate template, ModbusConfig config) {
        ModbusConfig.ConfigSection section = findModbusSection(config, template.id());
        if (section == null) throw notFound("Modbus profile does not contain the selected device type");
        List<ModbusFieldDto> fields = new ArrayList<>();
        for (RequiredField required : template.requiredFields()) {
            ModbusConfig.Entry<?> entry;
            try {
                entry = section.getEntryForId(required.field());
            } catch (NoSuchElementException exception) {
                throw badRequest("Profile is missing required field " + required.field());
            }
            fields.add(new ModbusFieldDto(
                    required.field(), required.unit(), entry.startAddress(), entry.size(), entry.scaleFactor(),
                    entry.formula(), entry.modbusParameterType().identifier(), entry.readOperationType().name(),
                    entry.byteOrder().toString()
            ));
        }
        ModbusConfig.Fingerprint fingerprint = config.getFingerprint();
        ModbusFingerprintDto fingerprintDto = fingerprint == null ? null : new ModbusFingerprintDto(
                fingerprint.address(), fingerprint.size(), fingerprint.parameterType().identifier(),
                fingerprint.operationType().name(), fingerprint.byteOrder().toString(), fingerprint.expectedValue()
        );
        return new ModbusProfileDto(name, template.id(), config.getAddressOffset(), fingerprintDto, fields);
    }

    private RestPVConfig deserializeRest(String json) {
        validateImport(json);
        try {
            JsonSerializerContext context = new JsonSerializerContext();
            return RestPVConfig.SERIALIZER.deserialize(context.fromJsonString(json));
        } catch (Exception exception) {
            throw badRequest("Invalid REST profile JSON");
        }
    }

    private ModbusConfig deserializeModbus(String json) {
        validateImport(json);
        try {
            JsonSerializerContext context = new JsonSerializerContext();
            return ModbusConfig.SERIALIZER.deserialize(context.fromJsonString(json));
        } catch (Exception exception) {
            throw badRequest("Invalid Modbus profile JSON");
        }
    }

    private void validateImport(String json) {
        if (json == null || json.isBlank()) {
            throw badRequest("Profile JSON is required");
        }
        if (json.length() > MAX_IMPORT_CHARACTERS) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Profile JSON is too large");
        }
    }

    private URI restBaseUri(String rawUrl) {
        String value = requiredText(rawUrl, "Base URL", 2_048);
        try {
            URI uri = URI.create(value);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw badRequest("Base URL must be an HTTP(S) origin without credentials, query, or fragment");
            }
            if (uri.getPort() < -1 || uri.getPort() > 65_535) {
                throw badRequest("Invalid URL port");
            }
            validateDeviceHost(uri.getHost());
            String normalized = uri.toString();
            return URI.create(normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw badRequest("Invalid base URL");
        }
    }

    private String deviceHost(String rawHost) {
        String host = requiredText(rawHost, "Host", 253);
        if (host.contains("/") || host.contains("\\") || host.contains("@") || hasControlCharacters(host)) {
            throw badRequest("Invalid device host");
        }
        validateDeviceHost(host);
        return host;
    }

    private void validateDeviceHost(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0 || (!allowPublicDeviceHosts && Arrays.stream(addresses).anyMatch(address -> !isPrivateAddress(address)))) {
                throw badRequest("Only private network device addresses are allowed");
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw badRequest("Device host could not be resolved");
        }
    }

    private boolean isPrivateAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress()) {
            return true;
        }
        if (address instanceof Inet6Address) {
            byte first = address.getAddress()[0];
            return (first & 0xFE) == 0xFC;
        }
        return false;
    }

    private String formula(String rawFormula, Set<String> allowedReferences) {
        String formula = rawFormula == null || rawFormula.isBlank() ? "x" : rawFormula.trim();
        if (formula.length() > 128 || !FORMULA_CHARACTERS.matcher(formula).matches()) {
            throw badRequest("Formula contains unsupported characters");
        }
        Matcher matcher = FORMULA_REFERENCE.matcher(formula);
        while (matcher.find()) {
            if (!allowedReferences.contains(matcher.group(1))) {
                throw badRequest("Formula references an unknown field");
            }
        }
        try {
            double result = FormulaEngine.evaluate(1.0, formula, ignored -> 1.0);
            if (!Double.isFinite(result)) {
                throw new IllegalArgumentException("Formula result is not finite");
            }
        } catch (Exception exception) {
            throw badRequest("Formula is invalid");
        }
        return formula;
    }

    private float scaleFactor(double value) {
        if (!Double.isFinite(value) || Math.abs(value) > 1_000_000_000D) {
            throw badRequest("Scale factor is invalid");
        }
        return (float) value;
    }

    private void ensureRegisterSize(ModbusParameterType<?> type, int registers) {
        int requiredBytes = switch (type.identifier()) {
            case "uint8" -> 1;
            case "uint16", "int16" -> 2;
            case "uint32", "int32", "float32" -> 4;
            case "ulong64", "double64" -> 8;
            default -> 0;
        };
        if (registers * 2 < requiredBytes) {
            throw badRequest("Register size is too small for parameter type " + type.identifier());
        }
    }

    private RestParameterType<?> restParameterType(String identifier) {
        try {
            return RestParameterType.findById(requiredText(identifier, "REST parameter type", 32));
        } catch (NoSuchElementException exception) {
            throw badRequest("Unknown REST parameter type");
        }
    }

    private ModbusParameterType<?> modbusParameterType(String identifier) {
        try {
            return ModbusParameterType.findById(requiredText(identifier, "Modbus parameter type", 32));
        } catch (NoSuchElementException exception) {
            throw badRequest("Unknown Modbus parameter type");
        }
    }

    private ByteOrder byteOrder(String value) {
        return switch (requiredText(value, "Byte order", 32).toUpperCase(Locale.ROOT)) {
            case "BIG_ENDIAN" -> ByteOrder.BIG_ENDIAN;
            case "LITTLE_ENDIAN" -> ByteOrder.LITTLE_ENDIAN;
            default -> throw badRequest("Unknown byte order");
        };
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String rawValue, String label) {
        try {
            return Enum.valueOf(type, requiredText(rawValue, label, 64).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw badRequest("Unknown " + label);
        }
    }

    private <T> Map<String, T> indexFields(List<T> fields, Function<T, String> nameFunction) {
        if (fields == null || fields.isEmpty() || fields.size() > MAX_FIELDS) {
            throw badRequest("Profile fields are missing or exceed the limit");
        }
        Map<String, T> result = new LinkedHashMap<>();
        for (T field : fields) {
            if (field == null) {
                throw badRequest("Profile field must not be null");
            }
            String name = requiredText(nameFunction.apply(field), "Field name", 80);
            if (result.putIfAbsent(name, field) != null) {
                throw badRequest("Duplicate profile field " + name);
            }
        }
        return result;
    }

    private void requireExactFields(List<RequiredField> requiredFields, Set<String> actualFields) {
        Set<String> required = requiredFieldNames(requiredFields);
        if (!required.equals(actualFields)) {
            throw badRequest("Profile fields do not match the selected template");
        }
    }

    private Set<String> requiredFieldNames(List<RequiredField> fields) {
        Set<String> names = new HashSet<>();
        fields.forEach(field -> names.add(field.field()));
        return names;
    }

    private TemplateDto toTemplate(String id, String name, List<RequiredField> fields) {
        return new TemplateDto(id, name, fields.stream()
                .map(field -> new FieldDefinitionDto(field.field(), field.unit()))
                .toList());
    }

    private List<String> communityNames(String protocol) {
        return configFetcherService.getCachedProfiles().stream()
                .filter(profile -> profile.supportedProtocols().contains(protocol))
                .map(profile -> profile.name().trim())
                .filter(name -> PROFILE_NAME.matcher(name).matches())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private List<String> loadNames(IoNames supplier) {
        try {
            return supplier.get().stream()
                    .filter(name -> name != null && !name.isBlank() && PROFILE_NAME.matcher(name.trim()).matches())
                    .map(String::trim)
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (IOException exception) {
            throw storageFailure("Could not list profiles", exception);
        }
    }

    private List<RestFieldDto> emptyRestFields(RestConfigCreatorTemplate template) {
        return template.requiredFields().stream()
                .map(field -> new RestFieldDto(
                        field.field(), field.unit(), "/", RestHttpMethod.GET.name(),
                        RestResponseType.PLAIN_TEXT.name(), "", 1.0, "x", RestParameterType.DOUBLE.identifier()
                ))
                .toList();
    }

    private List<RestFieldDto> emptyMessageFields(RestConfigCreatorTemplate template, String protocol) {
        String source = MessageConfigStorage.MQTT.equals(protocol) ? "solarminer/device/telemetry" : "message";
        return template.requiredFields().stream().map(field -> new RestFieldDto(
                field.field(), field.unit(), source, RestHttpMethod.GET.name(), RestResponseType.JSON.name(),
                "$." + field.field(), 1.0, "x", RestParameterType.DOUBLE.identifier())).toList();
    }

    private String messageProtocol(String protocol) {
        return switch (protocol == null ? "" : protocol.toLowerCase(Locale.ROOT)) {
            case "mqtt" -> MessageConfigStorage.MQTT;
            case "websocket" -> MessageConfigStorage.WEBSOCKET;
            default -> throw badRequest("Unsupported message protocol");
        };
    }

    private String profileProtocol(String protocol) {
        return MessageConfigStorage.MQTT.equals(messageProtocol(protocol)) ? "MQTT" : "WebSocket";
    }

    private List<ModbusFieldDto> emptyModbusFields(ModbusConfigCreatorTemplate template) {
        return template.requiredFields().stream()
                .map(field -> new ModbusFieldDto(
                        field.field(), field.unit(), 0, 1, 1.0, "x",
                        ModbusParameterType.UINT16.identifier(), ModbusReadOperationType.READ_HOLDING_REGISTER.name(),
                        ByteOrder.BIG_ENDIAN.toString()
                ))
                .toList();
    }

    private RestPVConfig.ConfigSection findRestSection(RestPVConfig config, String templateId) {
        if (config == null || config.getSections() == null) return null;
        return config.getSections().values().stream()
                .filter(section -> templateId.equals(section.getTemplateId()))
                .findFirst()
                .orElse(null);
    }

    private ModbusConfig.ConfigSection findModbusSection(ModbusConfig config, String templateId) {
        if (config == null || config.getSections() == null) return null;
        return config.getSections().values().stream()
                .filter(section -> templateId.equals(section.getTemplateId()))
                .findFirst()
                .orElse(null);
    }

    private RestPVConfig mergeRestSection(RestPVConfig existing, RestPVConfig replacement,
                                          RestConfigCreatorTemplate template) {
        Map<String, RestPVConfig.ConfigSection> sections = new LinkedHashMap<>(existing.getSections());
        String sectionKey = sections.entrySet().stream()
                .filter(entry -> template.id().equals(entry.getValue().getTemplateId()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(template.id());
        RestPVConfig.ConfigSection newSection = findRestSection(replacement, template.id());
        RestPVConfig.ConfigSection oldSection = sections.get(sectionKey);
        String sectionName = oldSection == null || oldSection.getName() == null || oldSection.getName().isBlank()
                ? template.name()
                : oldSection.getName();
        sections.put(sectionKey, new RestPVConfig.ConfigSection(template.id(), sectionName, newSection.getEntries()));
        return new RestPVConfig(sections);
    }

    private ModbusConfig mergeModbusSection(ModbusConfig existing, ModbusConfig replacement,
                                             ModbusConfigCreatorTemplate template) {
        Map<String, ModbusConfig.ConfigSection> sections = new LinkedHashMap<>(existing.getSections());
        String sectionKey = sections.entrySet().stream()
                .filter(entry -> template.id().equals(entry.getValue().getTemplateId()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(template.id());
        ModbusConfig.ConfigSection newSection = findModbusSection(replacement, template.id());
        ModbusConfig.ConfigSection oldSection = sections.get(sectionKey);
        String sectionName = oldSection == null || oldSection.getName() == null || oldSection.getName().isBlank()
                ? template.name()
                : oldSection.getName();
        sections.put(sectionKey, new ModbusConfig.ConfigSection(template.id(), sectionName, newSection.getEntries()));
        return new ModbusConfig(replacement.getFingerprint(), sections, replacement.getAddressOffset());
    }

    private RestConfigCreatorTemplate restTemplate(String templateId) {
        String id = requiredText(templateId, "Template", 64);
        if (RestConfigCreatorTemplate.HOME_ASSISTANT_PV.id().equals(id)) return RestConfigCreatorTemplate.HOME_ASSISTANT_PV;
        return RestConfigCreatorTemplate.getAll().stream()
                .filter(template -> template.id().equals(id))
                .findFirst()
                .orElseThrow(() -> badRequest("Unknown REST template"));
    }

    private ModbusConfigCreatorTemplate modbusTemplate(String templateId) {
        String id = requiredText(templateId, "Template", 64);
        if (ModbusConfigCreatorTemplate.PV_SITE.id().equals(id)) return ModbusConfigCreatorTemplate.PV_SITE;
        try {
            return ModbusConfigCreatorTemplate.byId(id);
        } catch (NoSuchElementException exception) {
            throw badRequest("Unknown Modbus template");
        }
    }

    private void requireExistingRestProfile(RestConfigCreatorTemplate template, String name) {
        try {
            if (!restStorage.doesConfigExistOnDisk(name)
                    || findRestSection(restStorage.loadConfig(name), template.id()) == null) {
                throw notFound("REST profile not found");
            }
        } catch (IOException exception) {
            throw storageFailure("Could not load REST profile", exception);
        }
    }

    private void requireExistingModbusProfile(ModbusConfigCreatorTemplate template, String name) {
        try {
            if (!modbusStorage.doesConfigExistOnDisk(name)
                    || findModbusSection(modbusStorage.loadConfig(name), template.id()) == null) {
                throw notFound("Modbus profile not found");
            }
        } catch (IOException exception) {
            throw storageFailure("Could not load Modbus profile", exception);
        }
    }

    private void requireMatchingName(String pathName, String bodyName) {
        if (!pathName.equals(profileName(bodyName))) {
            throw badRequest("Profile name in path and request body must match");
        }
    }

    private String profileName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (!PROFILE_NAME.matcher(name).matches() || name.equals(".") || name.equals("..")) {
            throw badRequest("Profile name contains unsupported characters");
        }
        return name;
    }

    private String safeToken(String token) {
        String value = token == null ? "" : token.trim();
        if (value.length() > 8_192 || hasControlCharacters(value)) {
            throw badRequest("API token is invalid");
        }
        return value;
    }

    private String requiredText(String value, String label, int maxLength) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty() || result.length() > maxLength || hasControlCharacters(result)) {
            throw badRequest(label + " is invalid");
        }
        return result;
    }

    private String optionalText(String value, int maxLength) {
        String result = value == null ? "" : value.trim();
        if (result.length() > maxLength || hasControlCharacters(result)) {
            throw badRequest("Text value is invalid");
        }
        return result;
    }

    private boolean hasControlCharacters(String value) {
        return value.chars().anyMatch(character -> Character.isISOControl(character));
    }

    private int integerRange(int value, int minimum, int maximum, String label) {
        if (value < minimum || value > maximum) {
            throw badRequest(label + " is outside the allowed range");
        }
        return value;
    }

    private void acquireTestSlot() {
        if (!connectionTestSlots.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many connection tests are running");
        }
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }

    private ResponseStatusException notFound(String reason) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
    }

    private ResponseStatusException storageFailure(String message, Exception exception) {
        LOGGER.log(Level.SEVERE, message, exception);
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    @FunctionalInterface
    private interface IoNames {
        List<String> get() throws IOException;
    }
}
