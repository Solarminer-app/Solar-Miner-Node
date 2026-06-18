package de.verdox.pv_miner_extensions.restpv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.pv_miner_extensions.restpv.config.RestHttpMethod;
import de.verdox.pv_miner_extensions.restpv.config.RestPVConfig;
import org.springframework.data.web.JsonPath;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class RestPVClient implements AutoCloseable {
    private final String baseUrl;
    private final String apiToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RestPVClient(String baseUrl, String apiToken) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiToken = apiToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public double read(RestPVConfig.Entry<?> entry) throws IOException, InterruptedException {
        String fullUrl = this.baseUrl + entry.urlExtension();

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header("Authorization", "Bearer " + apiToken)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5));

        if (entry.httpMethod() == RestHttpMethod.GET) {
            requestBuilder.GET();
        } else if (entry.httpMethod() == RestHttpMethod.POST) {
            requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP request failed with status code: " + response.statusCode() + " for URL: " + fullUrl);
        }
        JsonNode currentNode = objectMapper.readTree(response.body());

        String path = entry.jsonPath();
        if (path.startsWith("$.")) {
            path = path.substring(2);
        } else if (path.startsWith("$")) {
            path = path.substring(1);
        }

        if (!path.isBlank()) {
            String[] keys = path.split("\\.");
            for (String key : keys) {
                if (currentNode == null) {
                    break;
                }
                currentNode = currentNode.get(key);
            }
        }

        if (currentNode == null || currentNode.isMissingNode() || currentNode.isNull()) {
            throw new IOException("Could not find json path '" + entry.jsonPath() + "' in response from " + fullUrl);
        }
        Object rawValue = currentNode.asText();
        Number parsedNumber = entry.restParameterType().parser().apply(rawValue);
        double finalValue = parsedNumber.doubleValue();
        finalValue = finalValue * entry.scaleFactor();

        return finalValue;
    }

    public void ping() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/config"))
                .header("Authorization", "Bearer " + apiToken)
                .GET()
                .timeout(Duration.ofSeconds(3))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Ping failed. API returned status: " + response.statusCode());
        }
    }

    private double evaluateFormula(double value, String formula) {
        return value;
    }

    @Override
    public void close() {
    }
}