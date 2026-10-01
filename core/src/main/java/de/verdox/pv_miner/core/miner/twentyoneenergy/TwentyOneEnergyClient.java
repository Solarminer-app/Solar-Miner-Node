package de.verdox.pv_miner.core.miner.twentyoneenergy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Local-only client. It deliberately exposes no generic URL or endpoint forwarding API. */
public final class TwentyOneEnergyClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(4);
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;

    private final HttpClient http;
    private final ObjectMapper json;
    private final URI baseUri;

    public TwentyOneEnergyClient(MinerDetails details, ObjectMapper json) {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(), details, json);
    }

    TwentyOneEnergyClient(HttpClient http, MinerDetails details, ObjectMapper json) {
        this.http = http;
        this.json = json;
        this.baseUri = baseUri(details.ipv4(), details.port());
    }

    public TwentyOneEnergyDtos.StatusResponse status() { return get("status", TwentyOneEnergyDtos.StatusResponse.class); }
    public TwentyOneEnergyDtos.SystemStatusResponse system() { return get("status/system", TwentyOneEnergyDtos.SystemStatusResponse.class); }
    public String apiVersion() { return getTree("status/version").asText(); }
    public int powerLevel() { return getTree("heater/powerTarget").asInt(-1); }
    public long powerTargetWatts() { return getTree("heater/powerTarget/watt").asLong(-1); }
    public JsonNode summary() { return getTree("heater/status/summary"); }
    public JsonNode temperature() { return getTree("heater/status/temperature"); }
    public TwentyOneEnergyDtos.PoolConfigDto poolConfig() { return get("heater/poolConfig", TwentyOneEnergyDtos.PoolConfigDto.class); }

    public void setEnabled(boolean enabled) { post("heater/enable", new TwentyOneEnergyDtos.HeaterEnabledDto(enabled, false)); }
    public void setPowerLevel(int level) {
        if (level < 0 || level > 4) throw new IllegalArgumentException("21energy power level must be between 0 and 4");
        post("heater/powerTarget/" + level, java.util.Map.of("notifyApp", false));
    }
    public void setPoolConfig(TwentyOneEnergyDtos.PoolConfigDto value) { post("heater/poolConfig", value); }

    public static URI baseUri(String host, int port) {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid 21energy port");
        try {
            InetAddress address = InetAddress.getByName(host);
            if (!(address instanceof Inet4Address) || !(address.isSiteLocalAddress() || address.isLoopbackAddress())) {
                throw new IllegalArgumentException("21energy host must be a local IPv4 address");
            }
            return new URI("http", null, address.getHostAddress(), port, "/21control/", null, null);
        } catch (IOException | java.net.URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid 21energy host", exception);
        }
    }

    private <T> T get(String path, Class<T> type) {
        try {
            return json.treeToValue(getTree(path), type);
        } catch (IOException exception) {
            throw new TwentyOneEnergyApiException("Invalid response from 21energy heater", exception);
        }
    }

    private JsonNode getTree(String path) {
        // One retry is safe for a read-only GET. Mutating requests never retry.
        try {
            return execute(HttpRequest.newBuilder(endpoint(path)).timeout(REQUEST_TIMEOUT).GET().build(), true);
        } catch (TwentyOneEnergyApiException firstFailure) {
            return execute(HttpRequest.newBuilder(endpoint(path)).timeout(REQUEST_TIMEOUT).GET().build(), false);
        }
    }

    private void post(String path, Object body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint(path)).timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            execute(request, false);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new TwentyOneEnergyApiException("Could not encode 21energy request", exception);
        }
    }

    private JsonNode execute(HttpRequest request, boolean retryable) {
        try {
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.body().length > MAX_RESPONSE_BYTES) throw new TwentyOneEnergyApiException("21energy response exceeds size limit");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new TwentyOneEnergyApiException("21energy API returned HTTP " + response.statusCode());
            }
            return json.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TwentyOneEnergyApiException("21energy request interrupted", exception);
        } catch (IOException exception) {
            throw new TwentyOneEnergyApiException(retryable ? "21energy GET failed" : "21energy request failed", exception);
        }
    }

    private URI endpoint(String path) {
        if (path.startsWith("/") || path.contains("..")) throw new IllegalArgumentException("Invalid 21energy endpoint");
        return baseUri.resolve(path);
    }
}
