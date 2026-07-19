package de.verdox.pv_miner.setup;

import de.verdox.pv_miner.configfetcher.ConfigFetcherService;
import de.verdox.pv_miner.dto.SetupCatalogDto;
import de.verdox.pv_miner.dto.SetupCatalogDto.SetupFieldDto;
import de.verdox.pv_miner.dto.SetupCatalogDto.SetupOptionDto;
import de.verdox.pv_miner.dto.SetupCatalogDto.SetupSelectOptionDto;
import de.verdox.pv_miner.dto.SetupRequests;
import de.verdox.pv_miner.dto.SetupRequests.CreateSetupRequest;
import de.verdox.pv_miner.dto.SetupRequests.ProviderSelection;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.entity.EntityService;
import de.verdox.pv_miner.miningpool.MiningPoolEntity;
import de.verdox.pv_miner.pvsite.HistoricalPrice;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import de.verdox.pv_miner.pvsite.panels.PVPanels;
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

@Service
public class SetupService {
    public static final String KIND_PV_SOURCE = "PV_SOURCE";
    public static final String KIND_MINING_POOL = "MINING_POOL";
    public static final String MODBUS = "MODBUS_TCP";
    public static final String REST = "REST_API";
    public static final String BRAIINS = "BRAIINS";

    private final PVSiteRepository siteRepository;
    private final EntityService entityService;
    private final EntityQueryService queryService;
    private final ConfigFetcherService configFetcherService;
    private final ModbusConfigStorage modbusConfigStorage;
    private final RestConfigStorage restConfigStorage;

    public SetupService(PVSiteRepository siteRepository,
                        EntityService entityService,
                        EntityQueryService queryService,
                        ConfigFetcherService configFetcherService,
                        ModbusConfigStorage modbusConfigStorage,
                        RestConfigStorage restConfigStorage) {
        this.siteRepository = siteRepository;
        this.entityService = entityService;
        this.queryService = queryService;
        this.configFetcherService = configFetcherService;
        this.modbusConfigStorage = modbusConfigStorage;
        this.restConfigStorage = restConfigStorage;
    }

    public SetupCatalogDto getCatalog(String localeTag) {
        boolean german = localeTag != null && Locale.forLanguageTag(localeTag).getLanguage().equals("de");
        List<SetupSelectOptionDto> modbusProfiles = getProfiles("Modbus-TCP");
        List<SetupSelectOptionDto> restProfiles = getProfiles("Rest-API");

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
                new SetupOptionDto(REST, KIND_PV_SOURCE, "REST API",
                        german ? "Verbindung zu einer vorhandenen HTTP/REST-Schnittstelle." : "Connection to an existing HTTP/REST interface.",
                        false,
                        List.of(
                                field("host", german ? "Basis-URL oder Hostname" : "Base URL or hostname", german ? "Zum Beispiel http://192.168.1.20" : "For example http://192.168.1.20", "TEXT", true, "", null, null, List.of()),
                                field("port", "Port", "", "NUMBER", true, "80", 1d, 65535d, List.of()),
                                field("profile", german ? "Geräteprofil" : "Device profile", german ? "Bestimmt die verfügbaren Messwerte." : "Determines the available measurements.", "SELECT", true, firstValue(restProfiles), null, null, restProfiles),
                                field("apiToken", "API Token", german ? "Nur erforderlich, wenn die Schnittstelle geschützt ist." : "Only required when the interface is protected.", "PASSWORD", false, "", null, null, List.of())
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

    private void createPvDevices(PVSiteEntity site, ProviderSelection selection, int batteryCapacityWh) {
        Map<String, String> values = selection.values();
        String profile = required(values.get("profile"), "Device profile is required");
        if (MODBUS.equals(selection.providerId())) {
            ensureModbusProfile(profile);
            String host = required(values.get("host"), "Host is required");
            int port = integer(values, "port", 1, 65535);
            int slaveId = integer(values, "slaveId", 1, 255);
            try {
                ModbusConfig config = modbusConfigStorage.loadConfig(profile);
                for (Map.Entry<String, ModbusConfig.ConfigSection> entry : config.getSections().entrySet()) {
                    String name = deviceName(entry.getValue().getName(), entry.getValue().getTemplateId(), host);
                    if (ModbusConfigCreatorTemplate.INVERTER.id().equals(entry.getValue().getTemplateId())) {
                        ModbusInverter device = new ModbusInverter();
                        configureModbus(device, site, name, host, port, slaveId, profile, entry.getKey());
                        entityService.save(device);
                    } else if (ModbusConfigCreatorTemplate.BATTERY.id().equals(entry.getValue().getTemplateId())) {
                        ModbusBattery device = new ModbusBattery();
                        configureModbus(device, site, name, host, port, slaveId, profile, entry.getKey());
                        device.setNominalCapacityWh(Math.max(0, batteryCapacityWh));
                        entityService.save(device);
                    } else if (ModbusConfigCreatorTemplate.SMART_METER.id().equals(entry.getValue().getTemplateId())
                            || ModbusConfigCreatorTemplate.PV_SITE.id().equals(entry.getValue().getTemplateId())) {
                        ModbusSmartMeter device = new ModbusSmartMeter();
                        configureModbus(device, site, name, host, port, slaveId, profile, entry.getKey());
                        entityService.save(device);
                    }
                }
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
                    String name = deviceName(entry.getValue().getName(), entry.getValue().getTemplateId(), host);
                    if (RestConfigCreatorTemplate.INVERTER.id().equals(entry.getValue().getTemplateId())) {
                        RestInverter device = new RestInverter();
                        configureRest(device, site, name, host, port, token, profile, entry.getKey());
                        entityService.save(device);
                    } else if (RestConfigCreatorTemplate.BATTERY.id().equals(entry.getValue().getTemplateId())) {
                        RestBattery device = new RestBattery();
                        configureRest(device, site, name, host, port, token, profile, entry.getKey());
                        device.setNominalCapacityWh(Math.max(0, batteryCapacityWh));
                        entityService.save(device);
                    } else if (RestConfigCreatorTemplate.SMART_METER.id().equals(entry.getValue().getTemplateId())
                            || RestConfigCreatorTemplate.HOME_ASSISTANT_PV.id().equals(entry.getValue().getTemplateId())) {
                        RestSmartMeter device = new RestSmartMeter();
                        configureRest(device, site, name, host, port, token, profile, entry.getKey());
                        entityService.save(device);
                    }
                }
                return;
            } catch (IOException exception) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not load REST profile", exception);
            }
        }
        throw badRequest("Unsupported PV source");
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
                .filter(profile -> profile.supportedProtocols().contains(protocol))
                .map(profile -> profile.name())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(names::add);
        try {
            if ("Modbus-TCP".equals(protocol)) {
                names.addAll(modbusConfigStorage.getSavedConfigs());
            } else {
                names.addAll(restConfigStorage.getSavedConfigs());
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

    private SetupFieldDto field(String key, String label, String help, String type, boolean required,
                                String defaultValue, Double minimum, Double maximum, List<SetupSelectOptionDto> options) {
        return new SetupFieldDto(key, label, help, type, required, defaultValue, minimum, maximum, options);
    }

    private String firstValue(List<SetupSelectOptionDto> options) {
        return options.isEmpty() ? "" : options.getFirst().value();
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
