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
    private static final Map<String,String> IDS = Map.of("btc","bitcoin", "xmr","monero", "prl","pearl-research");
    private final DailyCoinPricesRepository repository; private final ObjectMapper mapper;
    private final HttpTextClient http;
    public CoinGeckoPriceService(DailyCoinPricesRepository repository, ObjectMapper mapper, HttpTextClient http) { this.repository=repository; this.mapper=mapper; this.http=http; }
    public synchronized Optional<DailyCoinPrices> prices(LocalDate date) {
        return repository.findById(date).or(() -> fetch(date));
    }
    public Optional<DailyCoinPrices> fetch(LocalDate date) {
        try {
            Map<String,Double> prices = new HashMap<>();
            for (var entry : IDS.entrySet()) {
                String url = date.equals(LocalDate.now(ZoneOffset.UTC))
                        ? "https://api.coingecko.com/api/v3/simple/price?ids=" + entry.getValue() + "&vs_currencies=usd"
                        : "https://api.coingecko.com/api/v3/coins/" + entry.getValue() + "/history?date=" + date.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-uuuu"));
                JsonNode root = get(url);
                JsonNode value = date.equals(LocalDate.now(ZoneOffset.UTC)) ? root.path(entry.getValue()).path("usd") : root.path("market_data").path("current_price").path("usd");
                if (value.isNumber() && value.asDouble() > 0) prices.put(entry.getKey(), value.asDouble());
            }
            return prices.isEmpty() ? Optional.empty() : Optional.of(repository.save(new DailyCoinPrices(date, prices)));
        } catch (Exception ignored) { return Optional.empty(); }
    }
    private JsonNode get(String url) throws Exception { return mapper.readTree(http.get(URI.create(url))); }
}
