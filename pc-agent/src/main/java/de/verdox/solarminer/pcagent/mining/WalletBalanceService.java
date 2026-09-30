package de.verdox.solarminer.pcagent.mining;

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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Actual pool credits and, for Pearl, the balance of the configured payout address. */
@Service
public class WalletBalanceService {
    private static final Duration CACHE_TIME = Duration.ofMinutes(1);
    private static final BigDecimal GRAINS_PER_PEARL = new BigDecimal("100000000");

    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final Map<String, CachedBalance> cache = new ConcurrentHashMap<>();

    public WalletBalanceService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<Balance> balances(String xmrPool, String xmrWallet, String pearlPool, String pearlWallet) {
        CompletableFuture<Balance> xmr = CompletableFuture.supplyAsync(() -> balance("xmr", xmrPool, xmrWallet));
        CompletableFuture<Balance> prl = CompletableFuture.supplyAsync(() -> balance("prl", pearlPool, pearlWallet));
        return List.of(
                xmr.join(),
                prl.join()
        );
    }

    private Balance balance(String coin, String pool, String wallet) {
        if (wallet == null || wallet.isBlank()) return new Balance(coin, null, null, "NOT_CONFIGURED", "NOT_CONFIGURED");
        String address = wallet.trim();
        boolean valid = "prl".equals(coin) ? address.matches("prl1[023456789acdefghjklmnpqrstuvwxyz]{20,120}")
                : address.matches("[1-9A-HJ-NP-Za-km-z]{95,120}");
        if (!valid) return new Balance(coin, null, null, "UNAVAILABLE", "UNAVAILABLE");

        CompletableFuture<BigDecimal> poolFuture = null;
        String poolStatus = "UNSUPPORTED_POOL";
        if (isKryptex(pool)) {
            poolFuture = CompletableFuture.supplyAsync(() -> cached("pool:" + coin + ":" + address,
                    URI.create("https://pool.kryptex.com/" + coin + "/api/v1/miner/balance/" + address),
                    WalletBalanceService::parsePoolBalance));
        }

        CompletableFuture<BigDecimal> chainFuture = null;
        String onChainStatus = "NOT_SUPPORTED";
        if ("prl".equals(coin)) {
            chainFuture = CompletableFuture.supplyAsync(() -> cached("chain:prl:" + address,
                    URI.create("https://pearlchain.live/api/explorer/address/" + address),
                    WalletBalanceService::parsePearlBalance));
        }
        BigDecimal poolBalance = poolFuture == null ? null : poolFuture.join();
        if (poolFuture != null) poolStatus = poolBalance == null ? "UNAVAILABLE" : "AVAILABLE";
        BigDecimal onChainBalance = chainFuture == null ? null : chainFuture.join();
        if (chainFuture != null) onChainStatus = onChainBalance == null ? "UNAVAILABLE" : "AVAILABLE";
        return new Balance(coin, poolBalance, onChainBalance, poolStatus, onChainStatus);
    }

    private static boolean isKryptex(String pool) {
        if (pool == null) return false;
        try {
            URI uri = URI.create(pool);
            String host = uri.getHost();
            return host != null && (host.equalsIgnoreCase("kryptex.network")
                    || host.toLowerCase(java.util.Locale.ROOT).endsWith(".kryptex.network"));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private BigDecimal cached(String key, URI uri, BalanceParser parser) {
        CachedBalance previous = cache.get(key);
        if (previous != null && Instant.now().isBefore(previous.expiresAt())) return previous.amount();
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json").GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
            BigDecimal amount = parser.parse(mapper.readTree(response.body()));
            cache.put(key, new CachedBalance(amount, Instant.now().plus(CACHE_TIME)));
            return amount;
        } catch (Exception ignored) {
            // Never turn an unavailable upstream into a zero balance.
            cache.put(key, new CachedBalance(null, Instant.now().plusSeconds(15)));
            return null;
        }
    }

    static BigDecimal parsePoolBalance(JsonNode json) {
        JsonNode total = json.path("total");
        if (!total.isNumber() || total.decimalValue().signum() < 0) throw new IllegalArgumentException("Missing pool balance");
        return total.decimalValue();
    }

    static BigDecimal parsePearlBalance(JsonNode json) {
        JsonNode grains = json.path("balance");
        if (!grains.isIntegralNumber() || grains.decimalValue().signum() < 0) throw new IllegalArgumentException("Missing Pearl balance");
        return grains.decimalValue().divide(GRAINS_PER_PEARL);
    }

    private interface BalanceParser { BigDecimal parse(JsonNode json); }
    private record CachedBalance(BigDecimal amount, Instant expiresAt) { }
    public record Balance(String coin, BigDecimal poolBalance, BigDecimal onChainBalance,
                          String poolStatus, String onChainStatus) { }
}
