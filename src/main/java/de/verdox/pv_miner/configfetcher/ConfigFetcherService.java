package de.verdox.pv_miner.configfetcher;

import de.verdox.solarminer.modbustcp.ModbusConfig;
import de.verdox.solarminer.rest.RestPVConfig;
import de.verdox.vserializer.generic.SerializationElement;
import de.verdox.vserializer.json.JsonSerializerContext;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Getter
    private volatile List<DeviceProfile> cachedProfiles = List.of();
    private volatile Map<String, ModbusConfig> cachedModbusConfigs = Map.of();
    private volatile Map<String, RestPVConfig> cachedRestPVConfigs = Map.of();

    public void fetchLatestConfigs() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(REPO_ZIP_URL)).GET().build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new IOException("Error while loading github: HTTP " + response.statusCode());
        }

        List<DeviceProfile> tempProfiles = new ArrayList<>();
        Map<String, ModbusConfig> tempModbus = new HashMap<>();
        Map<String, RestPVConfig> tempRest = new HashMap<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(response.body()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".json")) {
                    String jsonContent = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    processJsonFile(entry.getName(), jsonContent, tempProfiles, tempModbus, tempRest);
                }
                zis.closeEntry();
            }
        }

        this.cachedProfiles = Collections.unmodifiableList(tempProfiles);
        this.cachedModbusConfigs = Collections.unmodifiableMap(tempModbus);
        this.cachedRestPVConfigs = Collections.unmodifiableMap(tempRest);

        LOGGER.info("Sync successfully: " + tempProfiles.size() + " profiles loaded.");
    }

    private void processJsonFile(String filePath, String jsonContent,
                                 List<DeviceProfile> tempProfiles,
                                 Map<String, ModbusConfig> tempModbus,
                                 Map<String, RestPVConfig> tempRest) {

        String lowerPath = filePath.toLowerCase();

        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        String deviceName = fileName.replace(".json", "");

        try {
            JsonSerializerContext jsonSerializerContext = new JsonSerializerContext();
            SerializationElement element = jsonSerializerContext.fromJsonString(jsonContent);

            if (lowerPath.contains("modbus")) {
                ModbusConfig config = ModbusConfig.SERIALIZER.deserialize(element);
                tempModbus.put(deviceName, config);
                updateProfiles(deviceName, "Modbus-TCP", tempProfiles);

                LOGGER.info("Erfolgreich gecached (Modbus-TCP): " + deviceName);

            } else if (lowerPath.contains("rest")) {
                RestPVConfig config = RestPVConfig.SERIALIZER.deserialize(element);
                tempRest.put(deviceName, config);
                updateProfiles(deviceName, "Rest-API", tempProfiles);

                LOGGER.info("Erfolgreich gecached (Rest-API): " + deviceName);
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
        return Optional.ofNullable(cachedModbusConfigs.get(deviceName));
    }

    public Optional<RestPVConfig> getRestPVConfig(String deviceName) {
        return Optional.ofNullable(cachedRestPVConfigs.get(deviceName));
    }

    public record DeviceProfile(String name, List<String> supportedProtocols) {
        @Override
        public String toString() {
            return name + " (" + String.join("/", supportedProtocols) + ")";
        }
    }
}