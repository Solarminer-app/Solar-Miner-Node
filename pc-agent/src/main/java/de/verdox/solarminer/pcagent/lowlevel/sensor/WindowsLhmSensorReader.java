package de.verdox.solarminer.pcagent.lowlevel.sensor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.solarminer.pcagent.lowlevel.HardwareIdentityService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WindowsLhmSensorReader implements HardwareSensorReader {

    private static final Logger LOGGER = Logger.getLogger(WindowsLhmSensorReader.class.getName());
    private static final String LHM_URL = "http://localhost:8085/data.json";

    private final HttpClient httpClient;
    private final HardwareIdentityService hardwareIdentityService;
    private final ObjectMapper objectMapper;

    private double cachedWatts = -1.0;
    private double cachedTemp = -1.0;
    private long lastFetchTime = 0;

    public WindowsLhmSensorReader(HardwareIdentityService hardwareIdentityService, ObjectMapper objectMapper) {
        this.hardwareIdentityService = hardwareIdentityService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    @Override
    public double getCpuPowerWatts() {
        fetchDataIfNeeded();
        return cachedWatts;
    }

    @Override
    public double getCpuTemperatureCelsius() {
        fetchDataIfNeeded();
        return cachedTemp;
    }

    @Override
    public boolean isAccurate() {
        return true;
    }

    private synchronized void fetchDataIfNeeded() {
        if (System.currentTimeMillis() - lastFetchTime < 2000) {
            return;
        }

        try {

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(LHM_URL)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                parseLhmJson(root);
                lastFetchTime = System.currentTimeMillis();
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not connect to LibreHardwareMonitor on port 8085. Is it running?");
            cachedWatts = -1.0;
            cachedTemp = -1.0;
        }
    }

    private void parseLhmJson(JsonNode rootNode) {
        JsonNode cpuNode = findNodeByNameOrType(rootNode, hardwareIdentityService.getProcessor());
        if (cpuNode != null) {
            JsonNode tempCategory = findNodeByNameOrType(cpuNode, "Temperatures");
            if (tempCategory != null && tempCategory.has("Children")) {
                JsonNode firstTemp = tempCategory.get("Children").get(0);
                cachedTemp = parseValueString(firstTemp.path("Value").asText());
            }

            JsonNode powerCategory = findNodeByNameOrType(cpuNode, "Powers");
            if (powerCategory != null && powerCategory.has("Children")) {
                for (JsonNode sensor : powerCategory.get("Children")) {
                    if (sensor.path("Text").asText().toLowerCase().contains("package")) {
                        cachedWatts = parseValueString(sensor.path("Value").asText());
                        break;
                    }
                }
            }
        }
    }

    private JsonNode findNodeByNameOrType(JsonNode current, String keyword) {
        if (current.path("Text").asText().toLowerCase().contains(keyword.toLowerCase()) || keyword.toLowerCase().contains(current.path("Text").asText().toLowerCase()) || current.path("ImageURL").asText().toLowerCase().contains(keyword.toLowerCase())) {
            return current;
        }
        if (current.has("Children")) {
            for (JsonNode child : current.get("Children")) {
                JsonNode found = findNodeByNameOrType(child, keyword);
                if (found != null) return found;
            }
        }
        return null;
    }

    private double parseValueString(String val) {
        if (val == null || val.isBlank() || val.equals("-")) return -1.0;
        String cleanNumber = val.replaceAll("[^0-9.,-]", "").replace(",", ".");
        try {
            return Double.parseDouble(cleanNumber);
        } catch (NumberFormatException e) {
            return -1.0;
        }
    }
}