package de.verdox.pv_miner.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.MiningOS;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.miningpool.MiningCoin;
import de.verdox.pv_miner.globalconstants.GlobalConstantsService;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Consumes the PC-Agent forecast contract; algorithm mapping comes from MiningCoin. */
@Component
public class AgentEarningsSource implements MiningEarningsSource {
    private final ObjectMapper mapper;
    private final GlobalConstantsService constants;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public AgentEarningsSource(ObjectMapper mapper, GlobalConstantsService constants) {
        this.mapper = mapper;
        this.constants = constants;
    }
    @Override public boolean supports(MinerEntity<?> miner) { return miner.getOS() == MiningOS.AGENT; }

    @Override public List<MiningEarningsService.CoinEstimate> forecast(MinerEntity<?> miner, MinerStats stats) {
        List<MiningEarningsService.CoinEstimate> result = new ArrayList<>();
        try {
            URI uri = URI.create("http://" + miner.getIP() + ":8084/api/agent/earnings");
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("Agent returned " + response.statusCode());
            JsonNode forecasts = mapper.readTree(response.body());
            if (!forecasts.isArray()) throw new IllegalStateException("Agent earnings response is not an array");
            for (JsonNode forecast : forecasts) {
                MiningCoin coin;
                try { coin = MiningCoin.from(forecast.path("coin").asText()); }
                catch (IllegalArgumentException ignored) {
                    result.add(MiningEarningsService.CoinEstimate.unavailable(forecast.path("coin").asText(), "—", "Coin ist im Node nicht registriert"));
                    continue;
                }
                double hashrate = forecast.path("hashrateHps").asDouble();
                if (hashrate <= 0) continue;
                double watts = stats.workers() == null ? 0 : stats.workers().stream()
                        .filter(worker -> coin.algorithm().equals(worker.currentAlgorithm()))
                        .mapToDouble(MinerStats.Worker::approximatedPowerUsageWatts).sum();
                double priceUsd = constants.getCurrentCoinPrices().getOrDefault(coin.symbol().toLowerCase(java.util.Locale.ROOT), 0.0);
                double coinsPerDay = forecast.path("coinsPerDay").asDouble();
                String updatedAt = forecast.path("updatedAt").asText("");
                boolean fresh;
                try { fresh = Instant.parse(updatedAt).plus(Duration.ofMinutes(20)).isAfter(Instant.now()); }
                catch (Exception ignored) { fresh = false; }
                boolean available = forecast.path("available").asBoolean() && !forecast.path("stale").asBoolean()
                        && fresh && watts > 0 && coinsPerDay > 0 && priceUsd > 0;
                result.add(new MiningEarningsService.CoinEstimate(coin.key(), coin.symbol(), available,
                        available ? "" : forecast.path("unavailableReason").asText("Prognose oder Leistung fehlt"),
                        coinsPerDay, coinsPerDay * priceUsd,
                        watts, hashrate, updatedAt));
            }
        } catch (Exception ignored) {
            result.add(MiningEarningsService.CoinEstimate.unavailable("agent", "—", "PC-Agent nicht erreichbar"));
        }
        return result;
    }
}
