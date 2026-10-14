package de.verdox.pv_miner.miningpool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Read-only QuanPool public mainnet wallet API. */
@Service
public class QuanPoolApiService {
    private static final BigDecimal PLANCK_PER_QTC = new BigDecimal("1000000000000");
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final Map<String, Snapshot> cache = new ConcurrentHashMap<>();

    public QuanPoolApiService(ObjectMapper mapper) { this.mapper = mapper; }

    public Snapshot read(String address) {
        if (address == null || address.isBlank()) return Snapshot.unavailable();
        Snapshot cached = cache.get(address);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) return cached;
        try {
            String encoded = URLEncoder.encode(address.trim(), StandardCharsets.UTF_8).replace("+", "%20");
            URI uri = URI.create("https://quanpool.com/api/miner/mainnet/" + encoded);
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json").GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("QuanPool HTTP " + response.statusCode());
            JsonNode json = mapper.readTree(response.body());
            JsonNode seen = json.path("seen");
            BigDecimal pending = !seen.isBoolean() ? invalid()
                    : !seen.booleanValue() ? BigDecimal.ZERO : planck(json.path("balance").path("pending"));
            Snapshot snapshot = new Snapshot(pending, Instant.now(), Instant.now().plusSeconds(60));
            cache.put(address, snapshot);
            return snapshot;
        } catch (Exception failure) {
            Snapshot unavailable = Snapshot.unavailable();
            cache.put(address, unavailable);
            return unavailable;
        }
    }

    private static BigDecimal planck(JsonNode value) {
        if (!value.isNumber() && !value.isTextual()) return invalid();
        try {
            BigDecimal amount = new BigDecimal(value.asText());
            amount.toBigIntegerExact();
            if (amount.signum() < 0) return invalid();
            return amount.divide(PLANCK_PER_QTC);
        } catch (ArithmeticException | NumberFormatException failure) { return invalid(); }
    }

    private static BigDecimal invalid() { throw new IllegalArgumentException("Invalid QuanPool balance"); }

    public record Snapshot(BigDecimal amount, Instant fetchedAt, Instant expiresAt) {
        static Snapshot unavailable() { return new Snapshot(null, null, Instant.now().plusSeconds(15)); }
    }
}
