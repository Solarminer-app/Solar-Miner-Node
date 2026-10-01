package de.verdox.pv_miner.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.MiningOS;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.miningpool.MiningCoin;
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
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public AgentEarningsSource(ObjectMapper mapper) {
        this.mapper = mapper;
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
                boolean minerActive = stats.workers() != null && stats.workers().stream()
                        .anyMatch(worker -> coin.algorithm().equals(worker.currentAlgorithm())
                                && worker.miningStatus() == MinerStats.MinerStatus.MINING);
                if (hashrate <= 0 && !minerActive) continue;
                double watts = stats.workers() == null ? 0 : stats.workers().stream()
                        .filter(worker -> coin.algorithm().equals(worker.currentAlgorithm())
                                && worker.miningStatus() == MinerStats.MinerStatus.MINING)
                        .mapToDouble(MinerStats.Worker::approximatedPowerUsageWatts).sum();
                double priceUsd = forecast.path("priceUsd").asDouble();
                double coinsPerDay = forecast.path("coinsPerDay").asDouble();
                String updatedAt = forecast.path("updatedAt").asText("");
                List<String> sources = new ArrayList<>();
                forecast.path("sources").forEach(value -> sources.add(value.asText()));
                if (sources.isEmpty()) sources.add("PC-Agent Netzwerk-/Preisdaten");
                List<String> diagnostics = new ArrayList<>();
                if (!forecast.path("available").asBoolean() || forecast.path("stale").asBoolean()) {
                    String reason = forecast.path("unavailableReason").asText("");
                    if (!reason.isBlank()) diagnostics.add(reason);
                    else if (!forecast.path("available").asBoolean()) diagnostics.add("PC-Agent hat keine Prognose geliefert");
                }
                if (forecast.path("stale").asBoolean()) diagnostics.add("PC-Agent-Daten sind als veraltet markiert");
                boolean fresh;
                try { fresh = Instant.parse(updatedAt).plus(Duration.ofMinutes(20)).isAfter(Instant.now()); }
                catch (Exception ignored) { fresh = false; }
                if (!fresh) diagnostics.add("PC-Agent-Prognose fehlt oder ist älter als 20 Minuten");
                if (watts <= 0) diagnostics.add("Gemessene Miner-Leistung fehlt oder ist 0 W");
                if (coinsPerDay <= 0) diagnostics.add("Prognostizierte Coins pro Tag fehlen oder sind 0");
                if (priceUsd <= 0) diagnostics.add(coin.symbol() + "/USD-Kurs fehlt in den PC-Agent-Daten");
                boolean available = diagnostics.isEmpty();
                result.add(new MiningEarningsService.CoinEstimate(coin.key(), coin.symbol(), available,
                        available ? "" : String.join("; ", diagnostics),
                        coinsPerDay, coinsPerDay * priceUsd,
                        watts, hashrate, updatedAt, sources, List.copyOf(diagnostics)));
            }
        } catch (Exception ignored) {
            String detail = "PC-Agent-Abruf fehlgeschlagen (" + ignored.getClass().getSimpleName() + ")";
            result.add(new MiningEarningsService.CoinEstimate("agent", "—", false, detail, 0, 0, 0, 0, "",
                    List.of("PC-Agent API :8084/api/agent/earnings"), List.of(detail)));
        }
        return result;
    }
}
