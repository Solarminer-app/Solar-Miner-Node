package de.verdox.currencyrates.currencyrates.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.currencyrates.currencyrates.model.DailyCoinPrices;
import de.verdox.currencyrates.currencyrates.repository.DailyCoinPricesRepository;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.time.*;
import java.util.*;

@Service
public class CoinGeckoPriceService {
    private static final Map<String,String> IDS = Map.of(
            "btc", "bitcoin",
            "xmr", "monero",
            "prl", "pearl-research",
            "rvn", "ravencoin",
            "etc", "ethereum-classic");
    private final DailyCoinPricesRepository repository; private final ObjectMapper mapper;
    private final HttpTextClient http;
    public CoinGeckoPriceService(DailyCoinPricesRepository repository, ObjectMapper mapper, HttpTextClient http) { this.repository=repository; this.mapper=mapper; this.http=http; }
    public synchronized Optional<DailyCoinPrices> prices(LocalDate date) {
        return repository.findById(date).or(() -> fetch(date));
    }
    public synchronized Optional<DailyCoinPrices> fetch(LocalDate date) {
        try {
            if (date.equals(LocalDate.now(ZoneOffset.UTC))) return refreshCurrentPrices().map(ignored -> repository.findById(date).orElseThrow());
            Map<String,Double> prices = new HashMap<>();
            for (var entry : IDS.entrySet()) {
                String url = "https://api.coingecko.com/api/v3/coins/" + entry.getValue() + "/history?date=" + date.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-uuuu"));
                JsonNode root = get(url);
                JsonNode value = root.path("market_data").path("current_price").path("usd");
                if (value.isNumber() && value.asDouble() > 0) prices.put(entry.getKey(), value.asDouble());
            }
            return prices.isEmpty() ? Optional.empty() : Optional.of(repository.save(new DailyCoinPrices(date, prices)));
        } catch (Exception ignored) { return Optional.empty(); }
    }

    /** Refreshes all current prices in one provider request and merges partial responses with today's stored row. */
    public synchronized Optional<Map<String, Double>> refreshCurrentPrices() {
        try {
            String ids = String.join(",", IDS.values());
            JsonNode root = get("https://api.coingecko.com/api/v3/simple/price?ids=" + ids + "&vs_currencies=usd");
            Map<String, Double> stored = repository.findById(LocalDate.now(ZoneOffset.UTC))
                    .map(DailyCoinPrices::getPrices).map(HashMap::new).orElseGet(HashMap::new);
            Map<String, Double> fresh = new HashMap<>();
            for (var entry : IDS.entrySet()) {
                JsonNode value = root.path(entry.getValue()).path("usd");
                if (value.isNumber() && value.asDouble() > 0) fresh.put(entry.getKey(), value.asDouble());
            }
            if (!root.path(IDS.get("prl")).path("usd").isNumber()) {
                try {
                    JsonNode pearl = get("https://pearlchain.live/api/explorer/price").path("price");
                    if (pearl.isNumber() && pearl.asDouble() > 0) fresh.put("prl", pearl.asDouble());
                } catch (Exception ignored) {
                    // Pearl's own combined network snapshot still reports its price independently.
                }
            }
            if (fresh.isEmpty()) return Optional.empty();
            stored.putAll(fresh);
            repository.save(new DailyCoinPrices(LocalDate.now(ZoneOffset.UTC), stored));
            return Optional.of(Map.copyOf(fresh));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
    private JsonNode get(String url) throws Exception { return mapper.readTree(http.get(URI.create(url))); }
}
