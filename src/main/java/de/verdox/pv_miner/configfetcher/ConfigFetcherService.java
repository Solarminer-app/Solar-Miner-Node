package de.verdox.pv_miner.configfetcher;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.verdox.solarminer.modbustcp.ModbusConfig;
import de.verdox.solarminer.rest.RestPVConfig;
import de.verdox.vserializer.generic.SerializationElement;
import de.verdox.vserializer.json.JsonSerializerContext;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ConfigFetcherService {

    private static final Logger LOGGER = Logger.getLogger(ConfigFetcherService.class.getName());
    private static final String REPO_ZIP_URL = "https://github.com/derverdox/SolarMiner-Configs/archive/refs/heads/main.zip";
    private static final String BUNDLED_PROFILE_ROOT = "device-profiles/bundled/";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Getter
    private volatile List<DeviceProfile> cachedProfiles = List.of();
    private volatile Map<String, ModbusConfig> cachedModbusConfigs = Map.of();
    private volatile Map<String, RestPVConfig> cachedRestPVConfigs = Map.of();
    private volatile Map<String, RestPVConfig> cachedMqttConfigs = Map.of();
    private volatile Map<String, RestPVConfig> cachedWebSocketConfigs = Map.of();

    private List<DeviceProfile> bundledProfiles = List.of();
    private Map<String, ModbusConfig> bundledModbusConfigs = Map.of();
    private Map<String, RestPVConfig> bundledRestPVConfigs = Map.of();
    private Map<String, RestPVConfig> bundledMqttConfigs = Map.of();
    private Map<String, RestPVConfig> bundledWebSocketConfigs = Map.of();

    @PostConstruct
    void loadBundledConfigs() {
        List<DeviceProfile> profiles = new ArrayList<>();
        Map<String, ModbusConfig> modbus = new HashMap<>();
        Map<String, RestPVConfig> rest = new HashMap<>();
        Map<String, RestPVConfig> mqtt = new HashMap<>();
        Map<String, RestPVConfig> webSocket = new HashMap<>();

        ClassLoader classLoader = ConfigFetcherService.class.getClassLoader();
        try (InputStream manifestStream = classLoader.getResourceAsStream(BUNDLED_PROFILE_ROOT + "manifest.json")) {
            if (manifestStream == null) {
                LOGGER.warning("No bundled device profile manifest found. Only downloaded and locally saved profiles are available.");
                return;
            }
            JsonObject manifest = JsonParser.parseReader(new java.io.InputStreamReader(manifestStream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement profileElement : manifest.getAsJsonArray("profiles")) {
                String output = profileElement.getAsJsonObject().get("output").getAsString();
                try (InputStream profileStream = classLoader.getResourceAsStream(BUNDLED_PROFILE_ROOT + output)) {
                    if (profileStream == null) {
                        LOGGER.warning("Bundled device profile listed in manifest is missing: " + output);
                        continue;
                    }
                    String json = new String(profileStream.readAllBytes(), StandardCharsets.UTF_8);
                    processJsonFile(output, json, profiles, modbus, rest, mqtt, webSocket);
                }
            }
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Could not load bundled device profiles", exception);
            return;
        }

        bundledProfiles = freezeProfiles(profiles);
        bundledModbusConfigs = Map.copyOf(modbus);
        bundledRestPVConfigs = Map.copyOf(rest);
        bundledMqttConfigs = Map.copyOf(mqtt);
        bundledWebSocketConfigs = Map.copyOf(webSocket);
        replaceCaches(profiles, modbus, rest, mqtt, webSocket);
        LOGGER.info("Loaded " + profiles.size() + " bundled device profiles.");
    }

    public void fetchLatestConfigs() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(REPO_ZIP_URL)).GET().build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new IOException("Error while loading github: HTTP " + response.statusCode());
        }

        List<DeviceProfile> tempProfiles = mutableProfiles(bundledProfiles);
        Map<String, ModbusConfig> tempModbus = new HashMap<>(bundledModbusConfigs);
        Map<String, RestPVConfig> tempRest = new HashMap<>(bundledRestPVConfigs);
        Map<String, RestPVConfig> tempMqtt = new HashMap<>(bundledMqttConfigs);
        Map<String, RestPVConfig> tempWebSocket = new HashMap<>(bundledWebSocketConfigs);

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(response.body()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".json")) {
                    String jsonContent = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    processJsonFile(entry.getName(), jsonContent, tempProfiles, tempModbus, tempRest, tempMqtt, tempWebSocket);
                }
                zis.closeEntry();
            }
        }

        replaceCaches(tempProfiles, tempModbus, tempRest, tempMqtt, tempWebSocket);

        LOGGER.info("Sync successfully: " + tempProfiles.size() + " profiles loaded.");
    }

    private void processJsonFile(String filePath, String jsonContent,
                                 List<DeviceProfile> tempProfiles,
                                 Map<String, ModbusConfig> tempModbus,
                                 Map<String, RestPVConfig> tempRest,
                                 Map<String, RestPVConfig> tempMqtt,
                                 Map<String, RestPVConfig> tempWebSocket) {

        String lowerPath = filePath.toLowerCase();

        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        String deviceName = fileName.replace(".json", "");

        try {
            JsonSerializerContext jsonSerializerContext = new JsonSerializerContext();
            SerializationElement element = jsonSerializerContext.fromJsonString(jsonContent);

            if (lowerPath.contains("modbus-rtu")) {
                ModbusConfig config = ModbusConfig.SERIALIZER.deserialize(element);
                tempModbus.put(deviceName, config);
                updateProfiles(deviceName, "Modbus-RTU", tempProfiles);

                LOGGER.fine("Successfully cached (Modbus-RTU): " + deviceName);
            } else if (lowerPath.contains("modbus")) {
                ModbusConfig config = ModbusConfig.SERIALIZER.deserialize(element);
                tempModbus.put(deviceName, config);
                updateProfiles(deviceName, "Modbus-TCP", tempProfiles);

                LOGGER.fine("Successfully cached (Modbus-TCP): " + deviceName);

            } else if (lowerPath.contains("mqtt")) {
                RestPVConfig config = RestPVConfig.SERIALIZER.deserialize(element);
                tempMqtt.put(deviceName, config);
                updateProfiles(deviceName, "MQTT", tempProfiles);

                LOGGER.fine("Successfully cached (MQTT): " + deviceName);
            } else if (lowerPath.contains("websocket")) {
                RestPVConfig config = RestPVConfig.SERIALIZER.deserialize(element);
                tempWebSocket.put(deviceName, config);
                updateProfiles(deviceName, "WebSocket", tempProfiles);

                LOGGER.fine("Successfully cached (WebSocket): " + deviceName);
            } else if (lowerPath.contains("rest")) {
                RestPVConfig config = RestPVConfig.SERIALIZER.deserialize(element);
                tempRest.put(deviceName, config);
                updateProfiles(deviceName, "Rest-API", tempProfiles);

                LOGGER.fine("Successfully cached (Rest-API): " + deviceName);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Parsen der Config für '" + deviceName + "': " + e.getMessage(), e);
        }
    }

    private void updateProfiles(String deviceName, String protocol, List<DeviceProfile> tempProfiles) {
        DeviceProfile existing = tempProfiles.stream()
                .filter(p -> p.name().equalsIgnoreCase(deviceName))
                .findFirst()
                .orElse(null);

        if (existing == null) {
            List<String> protocols = new ArrayList<>(List.of(protocol));
            tempProfiles.add(new DeviceProfile(deviceName, protocols));
        } else if (!existing.supportedProtocols().contains(protocol)) {
            existing.supportedProtocols().add(protocol);
        }
    }

    public Optional<ModbusConfig> getModbusConfig(String deviceName) {
        return findCompatibleConfig(cachedModbusConfigs, deviceName);
    }

    public Optional<RestPVConfig> getRestPVConfig(String deviceName) {
        return findCompatibleConfig(cachedRestPVConfigs, deviceName);
    }

    public Optional<RestPVConfig> getMessagePVConfig(String protocol, String deviceName) {
        if (protocol == null) return Optional.empty();
        return switch (protocol.toLowerCase(Locale.ROOT)) {
            case "mqtt" -> findCompatibleConfig(cachedMqttConfigs, deviceName);
            case "websocket" -> findCompatibleConfig(cachedWebSocketConfigs, deviceName);
            default -> Optional.empty();
        };
    }

    public Optional<String> resolveProfileName(String protocol, String configuredName) {
        if (protocol == null) return Optional.empty();
        Map<String, ?> configs = switch (protocol.toLowerCase(Locale.ROOT)) {
            case "modbus-tcp", "modbus-rtu" -> cachedModbusConfigs;
            case "rest-api" -> cachedRestPVConfigs;
            case "mqtt" -> cachedMqttConfigs;
            case "websocket" -> cachedWebSocketConfigs;
            default -> Map.of();
        };
        return findCompatibleKey(configs, configuredName);
    }

    private <T> Optional<T> findCompatibleConfig(Map<String, T> configs, String configuredName) {
        return findCompatibleKey(configs, configuredName).map(configs::get);
    }

    private Optional<String> findCompatibleKey(Map<String, ?> configs, String configuredName) {
        if (configuredName == null || configuredName.isBlank()) return Optional.empty();
        if (configs.containsKey(configuredName)) return Optional.of(configuredName);
        String normalized = normalizeProfileName(configuredName);
        return configs.keySet().stream()
                .filter(name -> normalizeProfileName(name).equals(normalized))
                .findFirst();
    }

    private String normalizeProfileName(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private void replaceCaches(List<DeviceProfile> profiles,
                               Map<String, ModbusConfig> modbus,
                               Map<String, RestPVConfig> rest,
                               Map<String, RestPVConfig> mqtt,
                               Map<String, RestPVConfig> webSocket) {
        cachedProfiles = freezeProfiles(profiles);
        cachedModbusConfigs = Map.copyOf(modbus);
        cachedRestPVConfigs = Map.copyOf(rest);
        cachedMqttConfigs = Map.copyOf(mqtt);
        cachedWebSocketConfigs = Map.copyOf(webSocket);
    }

    private List<DeviceProfile> freezeProfiles(List<DeviceProfile> profiles) {
        return profiles.stream()
                .map(profile -> new DeviceProfile(profile.name(), List.copyOf(profile.supportedProtocols())))
                .sorted(Comparator.comparing(DeviceProfile::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<DeviceProfile> mutableProfiles(List<DeviceProfile> profiles) {
        return profiles.stream()
                .map(profile -> new DeviceProfile(profile.name(), new ArrayList<>(profile.supportedProtocols())))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    public record DeviceProfile(String name, List<String> supportedProtocols) {
        @Override
        public String toString() {
            return name + " (" + String.join("/", supportedProtocols) + ")";
        }
    }
}
