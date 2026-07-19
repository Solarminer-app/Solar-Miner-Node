package de.verdox.pv_miner.setup;

import de.verdox.pv_miner.configfetcher.ConfigFetcherService;
import de.verdox.pv_miner.dto.SetupCatalogDto;
import de.verdox.pv_miner.dto.SetupCatalogDto.SetupFieldDto;
import de.verdox.pv_miner.dto.SetupCatalogDto.SetupOptionDto;
import de.verdox.pv_miner.dto.SetupCatalogDto.SetupSelectOptionDto;
import de.verdox.pv_miner.dto.SetupRequests;
import de.verdox.pv_miner.dto.SetupRequests.CreateSetupRequest;
import de.verdox.pv_miner.dto.SetupRequests.ProviderSelection;
import de.verdox.pv_miner.discovery.DiscoveryService;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.entity.EntityService;
import de.verdox.pv_miner.miningpool.MiningPoolEntity;
import de.verdox.pv_miner.pvsite.HistoricalPrice;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import de.verdox.pv_miner.pvsite.panels.PVPanels;
import de.verdox.pv_miner.pvsite.battery.BatteryEntity;
import de.verdox.pv_miner.pvsite.inverter.InverterEntity;
import de.verdox.pv_miner.pvsite.smartmeter.SmartMeterEntity;
import de.verdox.pv_miner.util.Money;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import de.verdox.pv_miner_extensions.device.modbus.ModbusConfigStorage;
import de.verdox.pv_miner_extensions.device.modbus.battery.ModbusBattery;
import de.verdox.pv_miner_extensions.device.modbus.inverter.ModbusInverter;
import de.verdox.pv_miner_extensions.device.modbus.smartmeter.ModbusSmartMeter;
import de.verdox.pv_miner_extensions.device.rest.RestConfigStorage;
import de.verdox.pv_miner_extensions.device.rest.battery.RestBattery;
import de.verdox.pv_miner_extensions.device.rest.inverter.RestInverter;
import de.verdox.pv_miner_extensions.device.rest.smartmeter.RestSmartMeter;
import de.verdox.pv_miner_extensions.device.message.MessageConfigStorage;
import de.verdox.pv_miner_extensions.device.message.MessageEntity;
import de.verdox.pv_miner_extensions.device.modbusrtu.ModbusRtuEntity;
import de.verdox.pv_miner_extensions.device.modbusrtu.battery.ModbusRtuBattery;
import de.verdox.pv_miner_extensions.device.modbusrtu.inverter.ModbusRtuInverter;
import de.verdox.pv_miner_extensions.device.modbusrtu.smartmeter.ModbusRtuSmartMeter;
import de.verdox.pv_miner_extensions.device.mqtt.MqttEntity;
import de.verdox.pv_miner_extensions.device.mqtt.MqttMessageService;
import de.verdox.pv_miner_extensions.device.mqtt.battery.MqttBattery;
import de.verdox.pv_miner_extensions.device.mqtt.inverter.MqttInverter;
import de.verdox.pv_miner_extensions.device.mqtt.smartmeter.MqttSmartMeter;
import de.verdox.pv_miner_extensions.device.websocket.WebSocketEntity;
import de.verdox.pv_miner_extensions.device.websocket.WebSocketMessageService;
import de.verdox.pv_miner_extensions.device.websocket.battery.WebSocketBattery;
import de.verdox.pv_miner_extensions.device.websocket.inverter.WebSocketInverter;
import de.verdox.pv_miner_extensions.device.websocket.smartmeter.WebSocketSmartMeter;
import de.verdox.solarminer.modbusrtu.RTUModbusClient;
import de.verdox.pv_miner_extensions.pools.braiins.BraiinsPoolEntity;
import de.verdox.solarminer.modbustcp.ModbusConfigCreatorTemplate;
import de.verdox.solarminer.modbustcp.ModbusConfig;
import de.verdox.solarminer.modbustcp.TCPModbusClient;
import de.verdox.solarminer.rest.RestConfigCreatorTemplate;
import de.verdox.solarminer.rest.RestPVConfig;
import de.verdox.solarminer.rest.RestPVClient;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SetupService {
    public static final String KIND_PV_SOURCE = "PV_SOURCE";
    public static final String KIND_MINING_POOL = "MINING_POOL";
    public static final String MODBUS = "MODBUS_TCP";
    public static final String REST = "REST_API";
    public static final String MODBUS_RTU = "MODBUS_RTU";
    public static final String MQTT = "MQTT";
    public static final String WEBSOCKET = "WEBSOCKET";
    public static final String BRAIINS = "BRAIINS";

    private final PVSiteRepository siteRepository;
    private final EntityService entityService;
    private final EntityQueryService queryService;
    private final ConfigFetcherService configFetcherService;
    private final ModbusConfigStorage modbusConfigStorage;
    private final RestConfigStorage restConfigStorage;
    private final MessageConfigStorage messageConfigStorage;
    private final MqttMessageService mqttMessageService;
    private final WebSocketMessageService webSocketMessageService;
    private final DiscoveryService discoveryService;

    public SetupService(PVSiteRepository siteRepository,
                        EntityService entityService,
                        EntityQueryService queryService,
                        ConfigFetcherService configFetcherService,
                        ModbusConfigStorage modbusConfigStorage,
                        RestConfigStorage restConfigStorage,
                        MessageConfigStorage messageConfigStorage,
                        MqttMessageService mqttMessageService,
                        WebSocketMessageService webSocketMessageService,
                        DiscoveryService discoveryService) {
        this.siteRepository = siteRepository;
        this.entityService = entityService;
        this.queryService = queryService;
        this.configFetcherService = configFetcherService;
        this.modbusConfigStorage = modbusConfigStorage;
        this.restConfigStorage = restConfigStorage;
        this.messageConfigStorage = messageConfigStorage;
        this.mqttMessageService = mqttMessageService;
        this.webSocketMessageService = webSocketMessageService;
        this.discoveryService = discoveryService;
    }

    public SetupCatalogDto getCatalog(String localeTag) {
        boolean german = localeTag != null && Locale.forLanguageTag(localeTag).getLanguage().equals("de");
        List<SetupSelectOptionDto> modbusProfiles = getProfiles("Modbus-TCP");
        List<SetupSelectOptionDto> restProfiles = getProfiles("Rest-API");
        List<SetupSelectOptionDto> rtuProfiles = getProfiles("Modbus-RTU");
        List<SetupSelectOptionDto> mqttProfiles = getProfiles("MQTT");
        List<SetupSelectOptionDto> webSocketProfiles = getProfiles("WebSocket");

        List<SetupOptionDto> sources = List.of(
                new SetupOptionDto(MODBUS, KIND_PV_SOURCE, "Modbus TCP",
                        german ? "Direkte Verbindung zu Wechselrichter oder Smart Meter über Modbus TCP." : "Direct connection to an inverter or smart meter using Modbus TCP.",
                        true,
                        List.of(
                                field("host", german ? "IP-Adresse oder Hostname" : "IP address or hostname", german ? "Adresse des Modbus-Geräts" : "Address of the Modbus device", "TEXT", true, "", null, null, List.of()),
                                field("port", "Port", "", "NUMBER", true, "502", 1d, 65535d, List.of()),
                                field("profile", german ? "Geräteprofil" : "Device profile", german ? "Bestimmt die verfügbaren Messwerte." : "Determines the available measurements.", "SELECT", true, firstValue(modbusProfiles), null, null, modbusProfiles),
                                field("slaveId", "Slave ID", "", "NUMBER", true, "1", 1d, 255d, List.of())
                        )),
                new SetupOptionDto(REST, KIND_PV_SOURCE, german ? "HTTP / REST erweitert" : "Extended HTTP / REST",
                        german ? "HTTP mit JSON, XML, Text, GET/POST, Authentifizierung, Datenpfaden und Formeln." : "HTTP with JSON, XML, text, GET/POST, authentication, data paths and formulas.",
                        false,
                        List.of(
                                field("host", german ? "Basis-URL oder Hostname" : "Base URL or hostname", german ? "Zum Beispiel http://192.168.1.20" : "For example http://192.168.1.20", "TEXT", true, "", null, null, List.of()),
                                field("port", "Port", "", "NUMBER", true, "80", 1d, 65535d, List.of()),
                                field("profile", german ? "Geräteprofil" : "Device profile", german ? "Bestimmt die verfügbaren Messwerte." : "Determines the available measurements.", "SELECT", true, firstValue(restProfiles), null, null, restProfiles),
                                field("apiToken", "API Token", german ? "Nur erforderlich, wenn die Schnittstelle geschützt ist." : "Only required when the interface is protected.", "PASSWORD", false, "", null, null, List.of())
                        )),
                new SetupOptionDto(MODBUS_RTU, KIND_PV_SOURCE, "Modbus RTU",
                        german ? "Serielle Modbus-Verbindung, zum Beispiel über einen USB-RS485-Adapter." : "Serial Modbus connection, for example through a USB-to-RS485 adapter.",
                        false, List.of(
                        field("serialPort", german ? "Serielle Schnittstelle" : "Serial port", german ? "Stabiler, im Docker-Container sichtbarer Pfad, z. B. /dev/solarminer-rs485" : "Stable path visible inside the Docker container, e.g. /dev/solarminer-rs485", "TEXT", true, "/dev/solarminer-rs485", null, null, List.of()),
                        field("baudRate", "Baud rate", "", "SELECT", true, "9600", null, null, options("4800", "9600", "19200", "38400", "57600", "115200")),
                        field("dataBits", "Data bits", "", "SELECT", true, "8", null, null, options("7", "8")),
                        field("stopBits", "Stop bits", "", "SELECT", true, "1", null, null, options("1", "2")),
                        field("parity", german ? "Parität" : "Parity", "", "SELECT", true, "NONE", null, null, options("NONE", "EVEN", "ODD")),
                        field("slaveId", "Slave ID", "", "NUMBER", true, "1", 1d, 255d, List.of()),
                        field("profile", german ? "Geräteprofil" : "Device profile", "", "SELECT", true, firstValue(rtuProfiles), null, null, rtuProfiles)
                )),
                new SetupOptionDto(MQTT, KIND_PV_SOURCE, "MQTT",
                        german ? "Abonnieren von Gerätewerten über MQTT 3.1.1, optional mit TLS." : "Subscribe to device values using MQTT 3.1.1, optionally with TLS.",
                        false, List.of(
                        field("brokerUri", "Broker URI", german ? "z. B. mqtt://192.168.1.10:1883 oder mqtts://…" : "e.g. mqtt://192.168.1.10:1883 or mqtts://…", "TEXT", true, "mqtt://", null, null, List.of()),
                        field("profile", german ? "Geräteprofil" : "Device profile", "", "SELECT", true, firstValue(mqttProfiles), null, null, mqttProfiles),
                        field("clientId", "Client ID", german ? "Leer lassen für eine automatisch stabile ID." : "Leave empty for an automatically stable ID.", "TEXT", false, "", null, null, List.of()),
                        field("username", german ? "Benutzername" : "Username", "", "TEXT", false, "", null, null, List.of()),
                        field("password", german ? "Passwort" : "Password", "", "PASSWORD", false, "", null, null, List.of())
                )),
                new SetupOptionDto(WEBSOCKET, KIND_PV_SOURCE, "WebSocket",
                        german ? "Live-Telemetrie über ws:// oder verschlüsselt über wss://." : "Live telemetry over ws:// or encrypted wss://.",
                        false, List.of(
                        field("url", "WebSocket URL", "ws://… / wss://…", "TEXT", true, "ws://", null, null, List.of()),
                        field("profile", german ? "Geräteprofil" : "Device profile", "", "SELECT", true, firstValue(webSocketProfiles), null, null, webSocketProfiles),
                        field("apiToken", "API Token", "Bearer token", "PASSWORD", false, "", null, null, List.of())
                ))
        );

        List<SetupOptionDto> pools = List.of(
                new SetupOptionDto(BRAIINS, KIND_MINING_POOL, "Braiins Pool",
                        german ? "Braiins-Konto über einen Access Token verbinden." : "Connect a Braiins account using an access token.",
                        true,
                        List.of(field("accessToken", "Access Token", german ? "Wird verschlüsselt als Zugangsdaten der Poolverbindung verwendet." : "Used as the pool connection credential.", "PASSWORD", true, "", null, null, List.of())))
        );

        long count = siteRepository.count();
        return new SetupCatalogDto(count, EntityService.PV_SITE_LIMIT, count >= EntityService.PV_SITE_LIMIT, sources, pools);
    }

    public SetupCatalogDto refreshCatalog(String localeTag) throws IOException, InterruptedException {
        configFetcherService.fetchLatestConfigs();
        return getCatalog(localeTag);
    }

    public SetupRequests.ProviderValidationDto validateProvider(String kind, String providerId, Map<String, String> values) {
        try {
            if (KIND_PV_SOURCE.equals(kind)) {
                validatePvSource(providerId, values, true);
            } else if (KIND_MINING_POOL.equals(kind)) {
                validatePool(providerId, values, true);
            } else {
                throw badRequest("Unsupported setup option kind");
            }
            return new SetupRequests.ProviderValidationDto(true, "Connection successful");
        } catch (Exception exception) {
            String message = exception instanceof ResponseStatusException response && response.getReason() != null
                    ? response.getReason()
                    : exception.getMessage();
            return new SetupRequests.ProviderValidationDto(false, message == null ? "Connection failed" : message);
        }
    }

    @Transactional
    public SetupRequests.SetupCreatedDto createSetup(CreateSetupRequest request) {
        if (siteRepository.count() >= EntityService.PV_SITE_LIMIT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PV site limit reached");
        }
        String siteName = required(request.name(), "Site name is required");
        if (request.effectivePvDevices().isEmpty()) {
            throw badRequest("At least one PV device is required");
        }
        if (request.electricityPrice() < 0 || request.pvCost() < 0 || request.feedInTariff() < 0) {
            throw badRequest("Economic values must not be negative");
        }
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(required(request.timeZone(), "Time zone is required"));
        } catch (Exception exception) {
            throw badRequest("Invalid time zone");
        }
        String currencyCode = required(request.currency(), "Currency is required");
        if (!Set.of("EUR", "USD", "CHF").contains(currencyCode)) {
            throw badRequest("Unsupported currency");
        }
        CustomCurrency currency = CustomCurrency.getInstance(currencyCode);

        for (ProviderSelection device : request.effectivePvDevices()) {
            validatePvSource(device.providerId(), device.values(), true);
        }
        Set<String> selectedPoolProviders = new LinkedHashSet<>();
        for (ProviderSelection pool : request.miningPools()) {
            if (!selectedPoolProviders.add(pool.providerId())) {
                throw badRequest("A mining pool provider can only be selected once");
            }
            validatePool(pool.providerId(), pool.values(), true);
        }
        validatePanels(request.panelGroups());

        PVSiteEntity site = new PVSiteEntity();
        LocalDate setupDate = request.setupDate() == null ? LocalDate.now() : request.setupDate();
        site.setName(siteName);
        site.setSetupDate(setupDate);
        site.setTimezoneId(zoneId.getId());
        site.setBatteryCapacityWh(Math.max(0, request.batteryCapacityWh()));
        site.setPvCost(new Money(request.pvCost(), currency));
        site.getElectricityPriceHistory().add(new HistoricalPrice(setupDate, new Money(request.electricityPrice(), currency)));
        if (request.feedInTariff() > 0) {
            site.getFeedInTariffHistory().add(new HistoricalPrice(setupDate, new Money(request.feedInTariff(), currency)));
        }
        PVSiteEntity savedSite = entityService.save(site);
        for (ProviderSelection device : request.effectivePvDevices()) {
            createPvDevices(savedSite, device, request.batteryCapacityWh());
        }

        for (SetupRequests.PanelGroupInput input : request.panelGroups()) {
            PVPanels panels = new PVPanels();
            panels.setGroupName(input.name().trim());
            panels.setLatitudeDeg(input.latitude());
            panels.setLongitudeDeg(input.longitude());
            panels.setAmountOfPanels(input.panelCount());
            panels.setPowerPerPanelInWatts(input.powerPerPanelWatts());
            panels.setPanelAzimuthDegree(input.azimuthDegrees());
            panels.setPanelSlopeDeg(input.slopeDegrees());
            panels.setParentSite(savedSite);
            entityService.save(savedSite, panels);
        }

        for (ProviderSelection poolSelection : request.miningPools()) {
            MiningPoolEntity<?> pool = createPool(poolSelection);
            entityService.save(pool, savedSite);
        }

        return new SetupRequests.SetupCreatedDto(savedSite.getId());
    }

    public void createPvDevices(PVSiteEntity site, ProviderSelection selection, int batteryCapacityWh) {
        Map<String, String> values = selection.values();
        String profile = required(values.get("profile"), "Device profile is required");
        Set<String> selectedSections = selection.selectedSectionKeys().isEmpty()
                ? Set.of()
                : Set.copyOf(selection.selectedSectionKeys());
        int createdDevices = 0;
        if (MODBUS.equals(selection.providerId())) {
            ensureModbusProfile(profile);
            String host = required(values.get("host"), "Host is required");
            int port = integer(values, "port", 1, 65535);
            int slaveId = integer(values, "slaveId", 1, 255);
            try {
                ModbusConfig config = modbusConfigStorage.loadConfig(profile);
                for (Map.Entry<String, ModbusConfig.ConfigSection> entry : config.getSections().entrySet()) {
                    if (!selectedSections.isEmpty() && !selectedSections.contains(entry.getKey())) continue;
                    if (isConfigured(site, MODBUS, host, port, slaveId, profile, entry.getKey())) continue;
                    String name = deviceName(entry.getValue().getName(), entry.getValue().getTemplateId(), host);
                    if (ModbusConfigCreatorTemplate.INVERTER.id().equals(entry.getValue().getTemplateId())) {
                        ModbusInverter device = new ModbusInverter();
                        configureModbus(device, site, name, host, port, slaveId, profile, entry.getKey());
                        entityService.save(device);
                        site.getInverters().add(device);
                        createdDevices++;
                    } else if (ModbusConfigCreatorTemplate.BATTERY.id().equals(entry.getValue().getTemplateId())) {
                        ModbusBattery device = new ModbusBattery();
                        configureModbus(device, site, name, host, port, slaveId, profile, entry.getKey());
                        device.setNominalCapacityWh(Math.max(0, batteryCapacityWh));
                        entityService.save(device);
                        site.getBatteries().add(device);
                        createdDevices++;
                    } else if (ModbusConfigCreatorTemplate.SMART_METER.id().equals(entry.getValue().getTemplateId())
                            || ModbusConfigCreatorTemplate.PV_SITE.id().equals(entry.getValue().getTemplateId())) {
                        ModbusSmartMeter device = new ModbusSmartMeter();
                        configureModbus(device, site, name, host, port, slaveId, profile, entry.getKey());
                        entityService.save(device);
                        site.getSmartMeters().add(device);
                        createdDevices++;
                    }
                }
                if (createdDevices == 0) throw badRequest("The selected profile sections do not contain a supported PV device");
                return;
            } catch (IOException exception) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not load Modbus profile", exception);
            }
        }
        if (REST.equals(selection.providerId())) {
            ensureRestProfile(profile);
            String host = normalizeRestHost(required(values.get("host"), "Host is required"));
            int port = integer(values, "port", 1, 65535);
            String token = values.getOrDefault("apiToken", "");
            try {
                RestPVConfig config = restConfigStorage.loadConfig(profile);
                for (Map.Entry<String, RestPVConfig.ConfigSection> entry : config.getSections().entrySet()) {
                    if (!selectedSections.isEmpty() && !selectedSections.contains(entry.getKey())) continue;
                    if (isConfigured(site, REST, host, port, 1, profile, entry.getKey())) continue;
                    String name = deviceName(entry.getValue().getName(), entry.getValue().getTemplateId(), host);
                    if (RestConfigCreatorTemplate.INVERTER.id().equals(entry.getValue().getTemplateId())) {
                        RestInverter device = new RestInverter();
                        configureRest(device, site, name, host, port, token, profile, entry.getKey());
                        entityService.save(device);
                        site.getInverters().add(device);
                        createdDevices++;
                    } else if (RestConfigCreatorTemplate.BATTERY.id().equals(entry.getValue().getTemplateId())) {
                        RestBattery device = new RestBattery();
                        configureRest(device, site, name, host, port, token, profile, entry.getKey());
                        device.setNominalCapacityWh(Math.max(0, batteryCapacityWh));
                        entityService.save(device);
                        site.getBatteries().add(device);
                        createdDevices++;
                    } else if (RestConfigCreatorTemplate.SMART_METER.id().equals(entry.getValue().getTemplateId())
                            || RestConfigCreatorTemplate.HOME_ASSISTANT_PV.id().equals(entry.getValue().getTemplateId())) {
                        RestSmartMeter device = new RestSmartMeter();
                        configureRest(device, site, name, host, port, token, profile, entry.getKey());
                        entityService.save(device);
                        site.getSmartMeters().add(device);
                        createdDevices++;
                    }
                }
                if (createdDevices == 0) throw badRequest("The selected profile sections do not contain a supported PV device");
                return;
            } catch (IOException exception) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not load REST profile", exception);
            }
        }
        if (MODBUS_RTU.equals(selection.providerId())) {
            ensureModbusProfile(profile);
            String serialPort = required(values.get("serialPort"), "Serial port is required");
            int baudRate = integer(values, "baudRate", 300, 1_000_000);
            int dataBits = integer(values, "dataBits", 7, 8);
            int stopBits = integer(values, "stopBits", 1, 2);
            String parity = required(values.get("parity"), "Parity is required").toUpperCase(Locale.ROOT);
            int slaveId = integer(values, "slaveId", 1, 255);
            try {
                ModbusConfig config = modbusConfigStorage.loadConfig(profile);
                for (Map.Entry<String, ModbusConfig.ConfigSection> entry : config.getSections().entrySet()) {
                    if (!selectedSections.isEmpty() && !selectedSections.contains(entry.getKey())) continue;
                    if (isConfigured(site, MODBUS_RTU, serialPort, baudRate, slaveId, profile, entry.getKey())) continue;
                    String name = deviceName(entry.getValue().getName(), entry.getValue().getTemplateId(), serialPort);
                    Object device;
                    if (ModbusConfigCreatorTemplate.INVERTER.id().equals(entry.getValue().getTemplateId())) device = new ModbusRtuInverter();
                    else if (ModbusConfigCreatorTemplate.BATTERY.id().equals(entry.getValue().getTemplateId())) device = new ModbusRtuBattery();
                    else if (Set.of(ModbusConfigCreatorTemplate.SMART_METER.id(), ModbusConfigCreatorTemplate.PV_SITE.id()).contains(entry.getValue().getTemplateId())) device = new ModbusRtuSmartMeter();
                    else continue;
                    configureRtu(device, site, name, serialPort, baudRate, dataBits, stopBits, parity, slaveId, profile, entry.getKey());
                    if (device instanceof ModbusRtuBattery battery) battery.setNominalCapacityWh(Math.max(0, batteryCapacityWh));
                    saveAndAttach(site, device);
                    createdDevices++;
                }
                if (createdDevices == 0) throw badRequest("The selected profile sections do not contain a supported PV device");
                return;
            } catch (IOException exception) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not load Modbus RTU profile", exception);
            }
        }
        if (MQTT.equals(selection.providerId()) || WEBSOCKET.equals(selection.providerId())) {
            String protocol = MQTT.equals(selection.providerId()) ? MessageConfigStorage.MQTT : MessageConfigStorage.WEBSOCKET;
            ensureMessageProfile(protocol, profile);
            String endpoint = MQTT.equals(selection.providerId())
                    ? required(values.get("brokerUri"), "Broker URI is required")
                    : required(values.get("url"), "WebSocket URL is required");
            try {
                RestPVConfig config = messageConfigStorage.loadConfig(protocol, profile);
                for (Map.Entry<String, RestPVConfig.ConfigSection> entry : config.getSections().entrySet()) {
                    if (!selectedSections.isEmpty() && !selectedSections.contains(entry.getKey())) continue;
                    if (isConfigured(site, selection.providerId(), endpoint, 0, 0, profile, entry.getKey())) continue;
                    String name = deviceName(entry.getValue().getName(), entry.getValue().getTemplateId(), endpoint);
                    Object device = createMessageDevice(selection.providerId(), entry.getValue().getTemplateId());
                    if (device == null) continue;
                    configureMessage(device, site, name, values, profile, entry.getKey());
                    if (device instanceof BatteryEntity battery) battery.setNominalCapacityWh(Math.max(0, batteryCapacityWh));
                    saveAndAttach(site, device);
                    createdDevices++;
                }
                if (createdDevices == 0) throw badRequest("The selected profile sections do not contain a supported PV device");
                return;
            } catch (IOException exception) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not load message profile", exception);
            }
        }
        throw badRequest("Unsupported PV source");
    }

    public List<SetupRequests.PvDeviceProfileDto> getPvDeviceProfiles(String providerId, String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<String> protocols = providerId == null || providerId.isBlank()
                ? List.of(MODBUS, REST, MODBUS_RTU, MQTT, WEBSOCKET)
                : List.of(providerId);
        return protocols.stream()
                .flatMap(protocol -> getProfiles(profileProtocol(protocol)).stream()
                        .map(SetupSelectOptionDto::value)
                        .filter(name -> normalizedQuery.isEmpty() || name.toLowerCase(Locale.ROOT).contains(normalizedQuery))
                        .map(name -> describeProfile(protocol, name)))
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(SetupRequests.PvDeviceProfileDto::profileName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<SetupRequests.DiscoveredPvDeviceDto> discoverPvDevices(SetupRequests.PvDiscoveryRequest request) {
        String providerId = request.providerId() == null ? MODBUS : request.providerId();
        String subnetPrefix = request.subnetPrefix() == null || request.subnetPrefix().isBlank()
                ? DiscoveryService.getLocalSubnetPrefix()
                : request.subnetPrefix().trim();
        int port = request.port() == null ? 502 : request.port();
        int slaveId = request.slaveId() == null ? 1 : request.slaveId();
        List<SetupRequests.DiscoveredPvDeviceDto> found = new CopyOnWriteArrayList<>();
        CountDownLatch finished = new CountDownLatch(1);

        if (MODBUS.equals(providerId)) {
            discoveryService.discoverModbusDevices(subnetPrefix, port, device -> {
                SetupRequests.PvDeviceProfileDto profile = describeProfile(MODBUS, device.matchingProfileName());
                if (profile != null) found.add(new SetupRequests.DiscoveredPvDeviceDto(
                        MODBUS, device.ipAddress(), port, slaveId, device.matchingProfileName(), false, profile.sections()));
            }, finished::countDown);
        } else if (REST.equals(providerId)) {
            discoveryService.discoverRestDevices(subnetPrefix, device -> {
                SetupRequests.PvDeviceProfileDto profile = describeProfile(REST, device.matchingProfileName());
                if (profile != null) found.add(new SetupRequests.DiscoveredPvDeviceDto(
                        REST, device.ipAddress(), device.port(), 1, device.matchingProfileName(), device.requiresAuth(), profile.sections()));
            }, finished::countDown);
        } else {
            throw badRequest("Unsupported PV source");
        }
        try {
            finished.await(35, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        return found.stream()
                .distinct()
                .sorted(java.util.Comparator.comparing(SetupRequests.DiscoveredPvDeviceDto::host)
                        .thenComparing(SetupRequests.DiscoveredPvDeviceDto::profileName))
                .toList();
    }

    public void addPvDevices(PVSiteEntity site, ProviderSelection selection, int batteryCapacityWh) {
        validatePvSource(selection.providerId(), selection.values(), true);
        createPvDevices(site, selection, batteryCapacityWh);
    }

    private boolean isConfigured(PVSiteEntity site, String providerId, String host, int port, int slaveId, String profile, String sectionKey) {
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.concat(site.getInverters().stream(), site.getBatteries().stream()),
                        site.getSmartMeters().stream())
                .anyMatch(device -> {
                    if (MODBUS.equals(providerId) && device instanceof de.verdox.pv_miner_extensions.device.modbus.ModbusEntity<?> modbus) {
                        return host.equals(modbus.getIpAddress()) && port == modbus.getPort() && slaveId == modbus.getSlaveId()
                                && profile.equals(modbus.getModbusConfigName()) && sectionKey.equals(modbus.getSectionKey());
                    }
                    if (REST.equals(providerId) && device instanceof de.verdox.pv_miner_extensions.device.rest.RestEntity<?> rest) {
                        return host.equals(rest.getHostName()) && port == rest.getPort()
                                && profile.equals(rest.getRestConfigName()) && sectionKey.equals(rest.getSectionKey());
                    }
                    if (MODBUS_RTU.equals(providerId) && device instanceof ModbusRtuEntity<?> rtu) {
                        return host.equals(rtu.getSerialPort()) && port == rtu.getBaudRate() && slaveId == rtu.getSlaveId()
                                && profile.equals(rtu.getModbusConfigName()) && sectionKey.equals(rtu.getSectionKey());
                    }
                    if (MQTT.equals(providerId) && device instanceof MqttEntity<?> mqtt) {
                        return host.equals(mqtt.getBrokerUri()) && profile.equals(mqtt.getMessageConfigName())
                                && sectionKey.equals(mqtt.getSectionKey());
                    }
                    if (WEBSOCKET.equals(providerId) && device instanceof WebSocketEntity<?> websocket) {
                        return host.equals(websocket.getUrl()) && profile.equals(websocket.getMessageConfigName())
                                && sectionKey.equals(websocket.getSectionKey());
                    }
                    return false;
                });
    }

    private SetupRequests.PvDeviceProfileDto describeProfile(String providerId, String profileName) {
        try {
            if (MODBUS.equals(providerId)) {
                ensureModbusProfile(profileName);
                ModbusConfig config = modbusConfigStorage.loadConfig(profileName);
                return new SetupRequests.PvDeviceProfileDto(providerId, profileName, config.getSections().entrySet().stream()
                        .map(entry -> section(entry.getKey(), entry.getValue().getTemplateId(), entry.getValue().getName()))
                        .filter(java.util.Objects::nonNull)
                        .toList());
            }
            if (REST.equals(providerId)) {
                ensureRestProfile(profileName);
                RestPVConfig config = restConfigStorage.loadConfig(profileName);
                return new SetupRequests.PvDeviceProfileDto(providerId, profileName, config.getSections().entrySet().stream()
                        .map(entry -> section(entry.getKey(), entry.getValue().getTemplateId(), entry.getValue().getName()))
                        .filter(java.util.Objects::nonNull)
                        .toList());
            }
            if (MODBUS_RTU.equals(providerId)) {
                ensureModbusProfile(profileName);
                ModbusConfig config = modbusConfigStorage.loadConfig(profileName);
                return new SetupRequests.PvDeviceProfileDto(providerId, profileName, config.getSections().entrySet().stream()
                        .map(entry -> section(entry.getKey(), entry.getValue().getTemplateId(), entry.getValue().getName()))
                        .filter(java.util.Objects::nonNull).toList());
            }
            if (MQTT.equals(providerId) || WEBSOCKET.equals(providerId)) {
                String protocol = MQTT.equals(providerId) ? MessageConfigStorage.MQTT : MessageConfigStorage.WEBSOCKET;
                ensureMessageProfile(protocol, profileName);
                RestPVConfig config = messageConfigStorage.loadConfig(protocol, profileName);
                return new SetupRequests.PvDeviceProfileDto(providerId, profileName, config.getSections().entrySet().stream()
                        .map(entry -> section(entry.getKey(), entry.getValue().getTemplateId(), entry.getValue().getName()))
                        .filter(java.util.Objects::nonNull).toList());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private SetupRequests.PvDeviceSectionDto section(String key, String templateId, String name) {
        String type;
        if (Set.of(ModbusConfigCreatorTemplate.INVERTER.id(), RestConfigCreatorTemplate.INVERTER.id()).contains(templateId)) type = "INVERTER";
        else if (Set.of(ModbusConfigCreatorTemplate.BATTERY.id(), RestConfigCreatorTemplate.BATTERY.id()).contains(templateId)) type = "BATTERY";
        else if (Set.of(ModbusConfigCreatorTemplate.SMART_METER.id(), ModbusConfigCreatorTemplate.PV_SITE.id(), RestConfigCreatorTemplate.SMART_METER.id(), RestConfigCreatorTemplate.HOME_ASSISTANT_PV.id()).contains(templateId)) type = "SMART_METER";
        else return null;
        return new SetupRequests.PvDeviceSectionDto(key, templateId, name == null || name.isBlank() ? type : name, type);
    }

    private MiningPoolEntity<?> createPool(ProviderSelection selection) {
        if (BRAIINS.equals(selection.providerId())) {
            BraiinsPoolEntity pool = new BraiinsPoolEntity();
            pool.setAuthToken(required(selection.values().get("accessToken"), "Access token is required"));
            return pool;
        }
        throw badRequest("Unsupported mining pool");
    }

    private String deviceName(String configuredName, String templateId, String host) {
        String baseName = configuredName == null || configuredName.isBlank() ? templateId : configuredName.trim();
        return baseName + " (" + host + ")";
    }

    private void configureModbus(Object device, PVSiteEntity site, String name, String host, int port,
                                 int slaveId, String profile, String sectionKey) {
        if (device instanceof ModbusInverter inverter) {
            inverter.setName(name);
            inverter.setParentSite(site);
            inverter.setIpAddress(host);
            inverter.setPort(port);
            inverter.setSlaveId(slaveId);
            inverter.setModbusConfigName(profile);
            inverter.setSectionKey(sectionKey);
        } else if (device instanceof ModbusBattery battery) {
            battery.setName(name);
            battery.setParentSite(site);
            battery.setIpAddress(host);
            battery.setPort(port);
            battery.setSlaveId(slaveId);
            battery.setModbusConfigName(profile);
            battery.setSectionKey(sectionKey);
        } else if (device instanceof ModbusSmartMeter smartMeter) {
            smartMeter.setName(name);
            smartMeter.setParentSite(site);
            smartMeter.setIpAddress(host);
            smartMeter.setPort(port);
            smartMeter.setSlaveId(slaveId);
            smartMeter.setModbusConfigName(profile);
            smartMeter.setSectionKey(sectionKey);
        }
    }

    private void configureRest(Object device, PVSiteEntity site, String name, String host, int port,
                               String token, String profile, String sectionKey) {
        if (device instanceof RestInverter inverter) {
            inverter.setName(name);
            inverter.setParentSite(site);
            inverter.setHostName(host);
            inverter.setPort(port);
            inverter.setApiToken(token);
            inverter.setRestConfigName(profile);
            inverter.setSectionKey(sectionKey);
        } else if (device instanceof RestBattery battery) {
            battery.setName(name);
            battery.setParentSite(site);
            battery.setHostName(host);
            battery.setPort(port);
            battery.setApiToken(token);
            battery.setRestConfigName(profile);
            battery.setSectionKey(sectionKey);
        } else if (device instanceof RestSmartMeter smartMeter) {
            smartMeter.setName(name);
            smartMeter.setParentSite(site);
            smartMeter.setHostName(host);
            smartMeter.setPort(port);
            smartMeter.setApiToken(token);
            smartMeter.setRestConfigName(profile);
            smartMeter.setSectionKey(sectionKey);
        }
    }

    private void configureRtu(Object device, PVSiteEntity site, String name, String serialPort, int baudRate,
                              int dataBits, int stopBits, String parity, int slaveId, String profile, String sectionKey) {
        if (device instanceof InverterEntity inverter) { inverter.setName(name); inverter.setParentSite(site); }
        else if (device instanceof BatteryEntity battery) { battery.setName(name); battery.setParentSite(site); }
        else if (device instanceof SmartMeterEntity meter) { meter.setName(name); meter.setParentSite(site); }
        if (device instanceof ModbusRtuInverter rtu) configureRtuFields(rtu, serialPort, baudRate, dataBits, stopBits, parity, slaveId, profile, sectionKey);
        else if (device instanceof ModbusRtuBattery rtu) configureRtuFields(rtu, serialPort, baudRate, dataBits, stopBits, parity, slaveId, profile, sectionKey);
        else if (device instanceof ModbusRtuSmartMeter rtu) configureRtuFields(rtu, serialPort, baudRate, dataBits, stopBits, parity, slaveId, profile, sectionKey);
    }

    private void configureRtuFields(Object device, String serialPort, int baudRate, int dataBits, int stopBits,
                                    String parity, int slaveId, String profile, String sectionKey) {
        if (device instanceof ModbusRtuInverter rtu) {
            rtu.setSerialPort(serialPort); rtu.setBaudRate(baudRate); rtu.setDataBits(dataBits); rtu.setStopBits(stopBits); rtu.setParity(parity); rtu.setSlaveId(slaveId); rtu.setModbusConfigName(profile); rtu.setSectionKey(sectionKey);
        } else if (device instanceof ModbusRtuBattery rtu) {
            rtu.setSerialPort(serialPort); rtu.setBaudRate(baudRate); rtu.setDataBits(dataBits); rtu.setStopBits(stopBits); rtu.setParity(parity); rtu.setSlaveId(slaveId); rtu.setModbusConfigName(profile); rtu.setSectionKey(sectionKey);
        } else if (device instanceof ModbusRtuSmartMeter rtu) {
            rtu.setSerialPort(serialPort); rtu.setBaudRate(baudRate); rtu.setDataBits(dataBits); rtu.setStopBits(stopBits); rtu.setParity(parity); rtu.setSlaveId(slaveId); rtu.setModbusConfigName(profile); rtu.setSectionKey(sectionKey);
        }
    }

    private Object createMessageDevice(String providerId, String templateId) {
        boolean inverter = RestConfigCreatorTemplate.INVERTER.id().equals(templateId);
        boolean battery = RestConfigCreatorTemplate.BATTERY.id().equals(templateId);
        boolean meter = Set.of(RestConfigCreatorTemplate.SMART_METER.id(), RestConfigCreatorTemplate.HOME_ASSISTANT_PV.id()).contains(templateId);
        if (MQTT.equals(providerId)) return inverter ? new MqttInverter() : battery ? new MqttBattery() : meter ? new MqttSmartMeter() : null;
        if (WEBSOCKET.equals(providerId)) return inverter ? new WebSocketInverter() : battery ? new WebSocketBattery() : meter ? new WebSocketSmartMeter() : null;
        return null;
    }

    private void configureMessage(Object device, PVSiteEntity site, String name, Map<String, String> values,
                                  String profile, String sectionKey) {
        if (device instanceof InverterEntity inverter) { inverter.setName(name); inverter.setParentSite(site); }
        else if (device instanceof BatteryEntity battery) { battery.setName(name); battery.setParentSite(site); }
        else if (device instanceof SmartMeterEntity meter) { meter.setName(name); meter.setParentSite(site); }
        if (device instanceof MqttInverter mqtt) configureMqtt(mqtt, values, profile, sectionKey);
        else if (device instanceof MqttBattery mqtt) configureMqtt(mqtt, values, profile, sectionKey);
        else if (device instanceof MqttSmartMeter mqtt) configureMqtt(mqtt, values, profile, sectionKey);
        else if (device instanceof WebSocketInverter ws) configureWebSocket(ws, values, profile, sectionKey);
        else if (device instanceof WebSocketBattery ws) configureWebSocket(ws, values, profile, sectionKey);
        else if (device instanceof WebSocketSmartMeter ws) configureWebSocket(ws, values, profile, sectionKey);
    }

    private void configureMqtt(Object device, Map<String, String> values, String profile, String sectionKey) {
        java.util.function.Consumer<MqttTarget> apply = target -> target.set(values.get("brokerUri"), values.getOrDefault("clientId", ""), values.getOrDefault("username", ""), values.getOrDefault("password", ""), profile, sectionKey);
        if (device instanceof MqttInverter value) apply.accept(new MqttTarget(value::setBrokerUri, value::setClientId, value::setUsername, value::setPassword, value::setMessageConfigName, value::setSectionKey));
        else if (device instanceof MqttBattery value) apply.accept(new MqttTarget(value::setBrokerUri, value::setClientId, value::setUsername, value::setPassword, value::setMessageConfigName, value::setSectionKey));
        else if (device instanceof MqttSmartMeter value) apply.accept(new MqttTarget(value::setBrokerUri, value::setClientId, value::setUsername, value::setPassword, value::setMessageConfigName, value::setSectionKey));
    }

    private void configureWebSocket(Object device, Map<String, String> values, String profile, String sectionKey) {
        if (device instanceof WebSocketInverter value) { value.setUrl(values.get("url")); value.setApiToken(values.getOrDefault("apiToken", "")); value.setMessageConfigName(profile); value.setSectionKey(sectionKey); }
        else if (device instanceof WebSocketBattery value) { value.setUrl(values.get("url")); value.setApiToken(values.getOrDefault("apiToken", "")); value.setMessageConfigName(profile); value.setSectionKey(sectionKey); }
        else if (device instanceof WebSocketSmartMeter value) { value.setUrl(values.get("url")); value.setApiToken(values.getOrDefault("apiToken", "")); value.setMessageConfigName(profile); value.setSectionKey(sectionKey); }
    }

    private record MqttTarget(java.util.function.Consumer<String> broker, java.util.function.Consumer<String> client,
                              java.util.function.Consumer<String> user, java.util.function.Consumer<String> password,
                              java.util.function.Consumer<String> profile, java.util.function.Consumer<String> section) {
        void set(String brokerUri, String clientId, String username, String secret, String profileName, String sectionKey) {
            broker.accept(brokerUri); client.accept(clientId); user.accept(username); password.accept(secret); profile.accept(profileName); section.accept(sectionKey);
        }
    }

    private void saveAndAttach(PVSiteEntity site, Object device) {
        if (device instanceof InverterEntity inverter) { entityService.save(inverter); site.getInverters().add(inverter); }
        else if (device instanceof BatteryEntity battery) { entityService.save(battery); site.getBatteries().add(battery); }
        else if (device instanceof SmartMeterEntity meter) { entityService.save(meter); site.getSmartMeters().add(meter); }
    }

    private void validatePvSource(String providerId, Map<String, String> values, boolean testConnection) {
        if (MODBUS.equals(providerId)) {
            String host = required(values.get("host"), "Host is required");
            int port = integer(values, "port", 1, 65535);
            int slaveId = integer(values, "slaveId", 1, 255);
            required(values.get("profile"), "Device profile is required");
            if (testConnection) {
                try (TCPModbusClient ignored = new TCPModbusClient(host, port, slaveId)) {
                    // Opening the client verifies that the endpoint accepts a Modbus connection.
                } catch (Exception exception) {
                    throw badRequest("Modbus connection failed: " + exception.getMessage());
                }
            }
            return;
        }
        if (REST.equals(providerId)) {
            String host = normalizeRestHost(required(values.get("host"), "Host is required"));
            int port = integer(values, "port", 1, 65535);
            required(values.get("profile"), "Device profile is required");
            if (testConnection) {
                try (RestPVClient client = new RestPVClient(host + ":" + port, values.getOrDefault("apiToken", ""))) {
                    client.ping();
                } catch (Exception exception) {
                    throw badRequest("REST connection failed: " + exception.getMessage());
                }
            }
            return;
        }
        if (MODBUS_RTU.equals(providerId)) {
            String serialPort = required(values.get("serialPort"), "Serial port is required");
            int baudRate = integer(values, "baudRate", 300, 1_000_000);
            int dataBits = integer(values, "dataBits", 7, 8);
            int stopBits = integer(values, "stopBits", 1, 2);
            String parity = required(values.get("parity"), "Parity is required").toUpperCase(Locale.ROOT);
            if (!Set.of("NONE", "EVEN", "ODD").contains(parity)) throw badRequest("Unsupported parity");
            int slaveId = integer(values, "slaveId", 1, 247);
            required(values.get("profile"), "Device profile is required");
            if (testConnection) {
                try (RTUModbusClient ignored = new RTUModbusClient(serialPort, baudRate, dataBits, stopBits, parity, slaveId)) {}
                catch (Exception exception) { throw badRequest("Modbus RTU connection failed: " + exception.getMessage()); }
            }
            return;
        }
        if (MQTT.equals(providerId)) {
            String brokerUri = required(values.get("brokerUri"), "Broker URI is required");
            required(values.get("profile"), "Device profile is required");
            if (testConnection) {
                try { mqttMessageService.ping(brokerUri, values.getOrDefault("clientId", ""), values.getOrDefault("username", ""), values.getOrDefault("password", "")); }
                catch (Exception exception) { throw badRequest("MQTT connection failed: " + exception.getMessage()); }
            }
            return;
        }
        if (WEBSOCKET.equals(providerId)) {
            String url = required(values.get("url"), "WebSocket URL is required");
            if (!url.startsWith("ws://") && !url.startsWith("wss://")) throw badRequest("WebSocket URL must start with ws:// or wss://");
            required(values.get("profile"), "Device profile is required");
            if (testConnection) {
                try { webSocketMessageService.ping(url, values.getOrDefault("apiToken", "")); }
                catch (Exception exception) { throw badRequest("WebSocket connection failed: " + exception.getMessage()); }
            }
            return;
        }
        throw badRequest("Unsupported PV source");
    }

    private void validatePool(String providerId, Map<String, String> values, boolean testConnection) {
        MiningPoolEntity<?> pool = createPool(new ProviderSelection(providerId, values));
        if (testConnection && !queryService.ping(pool, 10, TimeUnit.SECONDS).join()) {
            throw badRequest("Pool connection could not be verified");
        }
    }

    private void validatePanels(List<SetupRequests.PanelGroupInput> panels) {
        if (panels.isEmpty()) {
            throw badRequest("At least one panel group is required");
        }
        for (SetupRequests.PanelGroupInput panel : panels) {
            required(panel.name(), "Panel group name is required");
            if (panel.latitude() < -90 || panel.latitude() > 90 || panel.longitude() < -180 || panel.longitude() > 180) {
                throw badRequest("Invalid panel location");
            }
            if (panel.panelCount() <= 0 || panel.powerPerPanelWatts() <= 0) {
                throw badRequest("Panel count and power must be greater than zero");
            }
            if (panel.azimuthDegrees() < 0 || panel.azimuthDegrees() > 360 || panel.slopeDegrees() < 0 || panel.slopeDegrees() > 90) {
                throw badRequest("Invalid panel orientation");
            }
        }
    }

    private List<SetupSelectOptionDto> getProfiles(String protocol) {
        Set<String> names = new LinkedHashSet<>();
        configFetcherService.getCachedProfiles().stream()
                .filter(profile -> profile.supportedProtocols().contains(protocol)
                        || ("Modbus-RTU".equals(protocol) && profile.supportedProtocols().contains("Modbus-TCP")))
                .map(profile -> profile.name())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(names::add);
        try {
            if ("Modbus-TCP".equals(protocol) || "Modbus-RTU".equals(protocol)) {
                names.addAll(modbusConfigStorage.getSavedConfigs());
            } else if ("Rest-API".equals(protocol)) {
                names.addAll(restConfigStorage.getSavedConfigs());
            } else if ("MQTT".equals(protocol)) {
                names.addAll(messageConfigStorage.getSavedConfigs(MessageConfigStorage.MQTT));
            } else if ("WebSocket".equals(protocol)) {
                names.addAll(messageConfigStorage.getSavedConfigs(MessageConfigStorage.WEBSOCKET));
            }
        } catch (IOException ignored) {
            // Cached repository profiles remain available.
        }
        names.removeIf(name -> name == null || name.isBlank());
        return names.stream().map(name -> new SetupSelectOptionDto(name, name)).toList();
    }

    private void ensureModbusProfile(String profile) {
        if (modbusConfigStorage.doesConfigExistOnDisk(profile)) return;
        var config = configFetcherService.getModbusConfig(profile)
                .orElseThrow(() -> badRequest("Unknown Modbus device profile"));
        try {
            modbusConfigStorage.save(profile, config);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save Modbus profile", exception);
        }
    }

    private void ensureRestProfile(String profile) {
        if (restConfigStorage.doesConfigExistOnDisk(profile)) return;
        var config = configFetcherService.getRestPVConfig(profile)
                .orElseThrow(() -> badRequest("Unknown REST device profile"));
        try {
            restConfigStorage.save(profile, config);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save REST profile", exception);
        }
    }

    private void ensureMessageProfile(String protocol, String profile) {
        if (messageConfigStorage.exists(protocol, profile)) return;
        var config = configFetcherService.getMessagePVConfig(protocol, profile)
                .orElseThrow(() -> badRequest("Unknown " + protocol + " device profile"));
        try { messageConfigStorage.save(protocol, profile, config); }
        catch (IOException exception) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save message profile", exception); }
    }

    private SetupFieldDto field(String key, String label, String help, String type, boolean required,
                                String defaultValue, Double minimum, Double maximum, List<SetupSelectOptionDto> options) {
        return new SetupFieldDto(key, label, help, type, required, defaultValue, minimum, maximum, options);
    }

    private String firstValue(List<SetupSelectOptionDto> options) {
        return options.isEmpty() ? "" : options.getFirst().value();
    }

    private List<SetupSelectOptionDto> options(String... values) {
        return java.util.Arrays.stream(values).map(value -> new SetupSelectOptionDto(value, value)).toList();
    }

    private String profileProtocol(String providerId) {
        return switch (providerId) {
            case MODBUS -> "Modbus-TCP";
            case MODBUS_RTU -> "Modbus-RTU";
            case REST -> "Rest-API";
            case MQTT -> "MQTT";
            case WEBSOCKET -> "WebSocket";
            default -> throw badRequest("Unsupported PV source");
        };
    }

    private String normalizeRestHost(String host) {
        String normalized = host.trim();
        return normalized.startsWith("http://") || normalized.startsWith("https://") ? normalized : "http://" + normalized;
    }

    private int integer(Map<String, String> values, String key, int minimum, int maximum) {
        try {
            int value = Integer.parseInt(required(values.get(key), key + " is required"));
            if (value < minimum || value > maximum) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw badRequest(key + " must be between " + minimum + " and " + maximum);
        }
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) throw badRequest(message);
        return value.trim();
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
