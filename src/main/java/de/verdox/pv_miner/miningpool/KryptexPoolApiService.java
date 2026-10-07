package de.verdox.pv_miner.miningpool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Read-only client for Kryptex's public wallet-address API. */
@Service
public class KryptexPoolApiService {
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final Map<String, Snapshot> cache = new ConcurrentHashMap<>();

    public KryptexPoolApiService(ObjectMapper mapper) { this.mapper = mapper; }

    public Snapshot read(MiningCoin coin, String address) {
        if (coin.kryptexTicker() == null || address == null || address.isBlank()) return Snapshot.unavailable();
        String key = coin.key() + ":" + address;
        Snapshot previous = cache.get(key);
        if (previous != null && previous.expiresAt().isAfter(Instant.now())) return previous;
        try {
            String ticker = coin.kryptexTicker();
            JsonNode balance = fetch("/" + ticker + "/api/v1/miner/balance/" + address);
            Snapshot snapshot = new Snapshot(parseBalance(balance), Instant.now(), Instant.now().plusSeconds(60));
            cache.put(key, snapshot);
            return snapshot;
        } catch (Exception ignored) {
            Snapshot unavailable = Snapshot.unavailable();
            cache.put(key, unavailable);
            return unavailable;
        }
    }

    private JsonNode fetch(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://pool.kryptex.com" + path))
                .timeout(Duration.ofSeconds(5)).header("Accept", "application/json").GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("Kryptex HTTP " + response.statusCode());
        return mapper.readTree(response.body());
    }

    static BigDecimal parseBalance(JsonNode json) {
        JsonNode total = json.path("total");
        if (!total.isNumber() || total.decimalValue().signum() < 0) throw new IllegalArgumentException("Missing pool balance");
        return total.decimalValue();
    }

    public record Snapshot(BigDecimal amount, Instant fetchedAt, Instant expiresAt) {
        public static Snapshot unavailable() { return new Snapshot(null, null, Instant.now().plusSeconds(15)); }
    }
}
