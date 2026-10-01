package de.verdox.solarminer.pcagent.mining;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.solarminer.pcagent.dto.MinerStats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Supplies gross, probability-based daily mining estimates. Pool payout schemes,
 * stale shares and pool/miner/SolarMiner fees are deliberately not deducted.
 */
@Service
public class EarningsForecastService {
    private static final Logger LOGGER = Logger.getLogger(EarningsForecastService.class.getName());
    private static final Duration CACHE_TIME = Duration.ofMinutes(10);
    private static final double SECONDS_PER_DAY = 86_400.0;

    private final ObjectMapper mapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final Map<String, NetworkSnapshot> snapshots = new ConcurrentHashMap<>();
    private volatile Instant nextRefreshAt = Instant.EPOCH;

    private final URI moneroNetworkUrl;
    private final String moneroBlockUrl;
    private final URI moneroPriceUrl;
    private final URI pearlStatsUrl;
    private final URI pearlPriceUrl;

    public EarningsForecastService(
            ObjectMapper mapper,
            @Value("${solarminer.earnings.monero-network-url:https://xmrchain.net/api/networkinfo}") URI moneroNetworkUrl,
            @Value("${solarminer.earnings.monero-block-url:https://xmrchain.net/api/block/%d}") String moneroBlockUrl,
            @Value("${solarminer.earnings.monero-price-url:https://api.kraken.com/0/public/Ticker?pair=XMRUSD}") URI moneroPriceUrl,
            @Value("${solarminer.earnings.pearl-stats-url:https://pearlchain.live/api/explorer/stats}") URI pearlStatsUrl,
            @Value("${solarminer.earnings.pearl-price-url:https://pearlchain.live/api/explorer/price}") URI pearlPriceUrl) {
        this.mapper = mapper;
        this.moneroNetworkUrl = moneroNetworkUrl;
        this.moneroBlockUrl = moneroBlockUrl;
        this.moneroPriceUrl = moneroPriceUrl;
        this.pearlStatsUrl = pearlStatsUrl;
        this.pearlPriceUrl = pearlPriceUrl;
    }

    public List<Forecast> forecasts(List<MinerStats.Worker> workers) {
        refreshIfNeeded();
        return List.of(
                forecast("monero", "XMR", hashrate(workers, "RandomX"), snapshots.get("monero")),
                forecast("pearl", "PRL", hashrate(workers, "PearlHash"), snapshots.get("pearl"))
        );
    }

    private static double hashrate(List<MinerStats.Worker> workers, String algorithm) {
        double value = workers.stream().filter(worker -> algorithm.equals(worker.currentAlgorithm()))
                .mapToDouble(worker -> worker.terahashPerSecond() * 1_000_000_000_000.0).sum();
        return Double.isFinite(value) && value > 0 ? value : 0.0;
    }

    private Forecast forecast(String coin, String ticker, double hashrate, NetworkSnapshot network) {
        if (network == null) {
            return Forecast.unavailable(coin, ticker, hashrate, "Netzwerk- und Preisdaten werden geladen");
        }
        if (!network.available()) {
            return Forecast.unavailable(coin, ticker, hashrate, network.error());
        }
        double coinsPerDay = hashrate > 0 ? estimateDailyCoins(hashrate, network) : 0.0;
        String reason = network.stale() ? network.error() : hashrate > 0 ? null : "Miner liefert noch keine Hashrate";
        return new Forecast(coin, ticker, hashrate > 0, reason,
                hashrate, network.networkHashrateHps(), network.difficulty(), network.targetBlockSeconds(),
                network.blockReward(), network.priceUsd(), coinsPerDay, coinsPerDay * network.priceUsd(),
                network.collectedAt(), network.stale(), network.sources());
    }

    static double estimateDailyCoins(double hashrateHps, NetworkSnapshot network) {
        return hashrateHps / network.networkHashrateHps()
                * (SECONDS_PER_DAY / network.targetBlockSeconds()) * network.blockReward();
    }

    private void refreshIfNeeded() {
        Instant now = Instant.now();
        if (now.isBefore(nextRefreshAt) || !refreshing.compareAndSet(false, true)) return;
        nextRefreshAt = now.plus(CACHE_TIME);
        Thread.startVirtualThread(() -> {
            try {
                Thread monero = Thread.startVirtualThread(() -> refreshCoin("monero", this::fetchMonero));
                Thread pearl = Thread.startVirtualThread(() -> refreshCoin("pearl", this::fetchPearl));
                monero.join();
                pearl.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                refreshing.set(false);
            }
        });
    }

    private void refreshCoin(String coin, SnapshotFetcher fetcher) {
        try {
            snapshots.put(coin, fetcher.fetch());
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Could not update " + coin + " earnings forecast", exception);
            NetworkSnapshot previous = snapshots.get(coin);
            snapshots.put(coin, previous == null
                    ? NetworkSnapshot.unavailable("Datenabruf fehlgeschlagen (" + exception.getClass().getSimpleName() + ")", sourcesFor(coin))
                    : previous.asStale("Letzter Datenabruf fehlgeschlagen (" + exception.getClass().getSimpleName() + ")"));
        }
    }

    private static List<String> sourcesFor(String coin) {
        return switch (coin) {
            case "monero" -> List.of("xmrchain.net (Netzwerk und letzter Block)", "Kraken (XMR/USD)");
            case "pearl" -> List.of("pearlchain.live (Netzwerkdaten, Blockreward und Preis)");
            default -> List.of();
        };
    }

    private NetworkSnapshot fetchMonero() throws Exception {
        JsonNode network = get(moneroNetworkUrl).path("data");
        long height = network.path("height").asLong();
        if (height < 1) throw new IllegalStateException("Monero block height is missing");
        JsonNode block = get(URI.create(moneroBlockUrl.formatted(height - 1))).path("data");
        JsonNode price = get(moneroPriceUrl).path("result");
        return parseMonero(network, block, price, Instant.now());
    }

    private NetworkSnapshot fetchPearl() throws Exception {
        return parsePearl(get(pearlStatsUrl), get(pearlPriceUrl), Instant.now());
    }

    private JsonNode get(URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(6))
                .header("Accept", "application/json")
                .header("User-Agent", "SolarMiner-PC-Agent/1.0")
                .GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IllegalStateException(uri.getHost() + " returned HTTP " + response.statusCode());
        return mapper.readTree(response.body());
    }

    static NetworkSnapshot parseMonero(JsonNode network, JsonNode block, JsonNode priceResult, Instant collectedAt) {
        double difficulty = network.path("difficulty").asDouble();
        double networkHashrate = network.path("hash_rate").asDouble();
        double target = network.path("target").asDouble();
        double reward = block.path("txs").path(0).path("xmr_outputs").asDouble() / 1_000_000_000_000.0;
        var tickers = priceResult.elements();
        JsonNode ticker = tickers.hasNext() ? tickers.next() : null;
        double price = ticker == null ? 0.0 : ticker.path("c").path(0).asDouble();
        validate(networkHashrate, difficulty, target, reward, price);
        return new NetworkSnapshot(true, null, networkHashrate, difficulty, target, reward, price, collectedAt,
                false, List.of("xmrchain.net", "Kraken"));
    }

    static NetworkSnapshot parsePearl(JsonNode stats, JsonNode priceData, Instant collectedAt) {
        double networkHashrate = stats.path("networkHashPs").asDouble();
        double difficulty = stats.path("difficulty").asDouble();
        double target = stats.path("targetBlockSecs").asDouble();
        double reward = stats.path("blockRewardPearl").asDouble();
        double price = priceData.path("price").asDouble();
        validate(networkHashrate, difficulty, target, reward, price);
        return new NetworkSnapshot(true, null, networkHashrate, difficulty, target, reward, price, collectedAt,
                priceData.path("stale").asBoolean(false), List.of("pearlchain.live"));
    }

    private static void validate(double networkHashrate, double difficulty, double target, double reward, double price) {
        if (!(networkHashrate > 0) || !(difficulty > 0) || !(target > 0) || !(reward > 0) || !(price > 0))
            throw new IllegalArgumentException("Incomplete mining market data");
    }

    @FunctionalInterface
    private interface SnapshotFetcher { NetworkSnapshot fetch() throws Exception; }

    record NetworkSnapshot(boolean available, String error, double networkHashrateHps, double difficulty,
                           double targetBlockSeconds, double blockReward, double priceUsd, Instant collectedAt,
                           boolean stale, List<String> sources) {
        static NetworkSnapshot unavailable(String error, List<String> sources) {
            return new NetworkSnapshot(false, error, 0, 0, 0, 0, 0, Instant.now(), false, sources);
        }

        NetworkSnapshot asStale(String refreshError) {
            return new NetworkSnapshot(available, refreshError, networkHashrateHps, difficulty, targetBlockSeconds,
                    blockReward, priceUsd, collectedAt, true, sources);
        }
    }

    public record Forecast(String coin, String ticker, boolean available, String unavailableReason,
                           double hashrateHps, double networkHashrateHps, double difficulty,
                           double targetBlockSeconds, double blockReward, double priceUsd,
                           double coinsPerDay, double usdPerDay, Instant updatedAt,
                           boolean stale, List<String> sources) {
        static Forecast unavailable(String coin, String ticker, double hashrate, String reason) {
            return new Forecast(coin, ticker, false, reason, hashrate, 0, 0, 0, 0, 0, 0, 0,
                    null, false, List.of());
        }
    }
}
