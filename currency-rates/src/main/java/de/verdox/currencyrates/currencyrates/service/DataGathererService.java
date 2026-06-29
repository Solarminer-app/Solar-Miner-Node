package de.verdox.currencyrates.currencyrates.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.currencyrates.currencyrates.model.BitcoinNetworkStats;
import de.verdox.currencyrates.currencyrates.model.DailyUsdRates;
import de.verdox.currencyrates.currencyrates.repository.BitcoinNetworkStatsRepository;
import de.verdox.currencyrates.currencyrates.repository.DailyUsdRatesRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class DataGathererService {
    private static final Logger LOGGER = Logger.getLogger(DataGathererService.class.getSimpleName());

    // Globaler, wiederverwendbarer HTTP-Client spart RAM und Netzwerkressourcen
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final BitcoinNetworkStatsRepository bitcoinRepository;
    private final DailyUsdRatesRepository ratesRepository;
    private final ObjectMapper objectMapper; // Spring Boot injiziert Jackson automatisch

    private BitcoinMiningDataFetcher bitcoinMiningDataFetcher;

    public DataGathererService(BitcoinNetworkStatsRepository bitcoinRepository,
                               DailyUsdRatesRepository ratesRepository,
                               ObjectMapper objectMapper) {
        this.bitcoinRepository = bitcoinRepository;
        this.ratesRepository = ratesRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationReady() {
        // Wir übergeben den ObjectMapper an den Fetcher
        bitcoinMiningDataFetcher = new BitcoinMiningDataFetcher(objectMapper);
        LOGGER.log(Level.INFO, "Fetching global constants...");
        collectGlobalConstants();
        saveDailyStatsToDatabase();
        LOGGER.log(Level.INFO, "Done...");
    }

    private void collectGlobalConstants() {
        queryBitcoinMiningData();
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    public void scheduledFetch() {
        collectGlobalConstants();
    }

    @Scheduled(cron = "0 0 0 * * ?", zone = "UTC")
    @Transactional
    public void scheduledDailyDatabaseSave() {
        LOGGER.log(Level.INFO, "Starting scheduled daily UTC database backup...");
        collectGlobalConstants();
        saveDailyStatsToDatabase();
    }

    @Transactional
    public DailyUsdRates fetchAndSaveForDate(LocalDate date) {
        LOGGER.log(Level.INFO, "Fetching data for date: " + date);
        JsonNode ratesNode = queryExchangeRates(date);

        Map<String, Double> ratesMap = extractRatesMap(ratesNode);

        DailyUsdRates dailyRates = new DailyUsdRates(date, ratesMap);
        return ratesRepository.save(dailyRates);
    }

    @Transactional
    public void saveDailyStatsToDatabase() {
        try {
            LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
            JsonNode currencyRatesUSD = queryExchangeRates();

            if (currencyRatesUSD == null || bitcoinMiningDataFetcher == null) {
                LOGGER.log(Level.WARNING, "Cannot save to database: Data fetchers are empty.");
                return;
            }

            if (!bitcoinRepository.existsById(todayUtc)) {
                BitcoinNetworkStats btcStats = new BitcoinNetworkStats(todayUtc);
                btcStats.setMiningDifficulty(bitcoinMiningDataFetcher.getMiningDifficulty());
                btcStats.setHashRateInThs(bitcoinMiningDataFetcher.getGlobalHashRateInThs());
                btcStats.setPriceInDollar(bitcoinMiningDataFetcher.getPriceInDollar());
                btcStats.setBlockSubsidy(bitcoinMiningDataFetcher.getBlockSubsidy());
                btcStats.setAverageTxPrice24h(bitcoinMiningDataFetcher.getAverageTxPrice24h());

                bitcoinRepository.save(btcStats);
                LOGGER.log(Level.INFO, "Successfully saved Bitcoin network stats for UTC date: " + todayUtc);
            } else {
                LOGGER.log(Level.INFO, "Bitcoin network stats for " + todayUtc + " already exist. Skipping.");
            }

            if (!ratesRepository.existsById(todayUtc)) {
                Map<String, Double> ratesMap = extractRatesMap(currencyRatesUSD);

                DailyUsdRates dailyRates = new DailyUsdRates(todayUtc, ratesMap);
                ratesRepository.save(dailyRates);
                LOGGER.log(Level.INFO, "Successfully saved daily currency rates for UTC date: " + todayUtc);
            } else {
                LOGGER.log(Level.INFO, "Currency rates for " + todayUtc + " already exist. Skipping.");
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error while saving daily stats to database: " + e.getMessage(), e);
        }
    }

    // Hilfsmethode, um Jackson JsonNodes sauber in eine Map zu überführen
    private Map<String, Double> extractRatesMap(JsonNode node) {
        Map<String, Double> ratesMap = new HashMap<>();
        if (node != null && node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (entry.getValue().isNumber()) {
                    ratesMap.put(entry.getKey(), entry.getValue().asDouble());
                }
            }
        }
        return ratesMap;
    }

    // Nutzt nun den globalen HTTP_CLIENT
    private static String sendGetRequest(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(url))
                .GET()
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private JsonNode queryExchangeRates(LocalDate date) {
        try {
            String dateParam = (date == null) ? "latest" : date.toString();
            String url = String.format("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json", dateParam);
            LOGGER.log(Level.INFO, "Collecting currency exchange rates for date: " + dateParam);
            String jsonResponse = sendGetRequest(url);

            // Jackson Parsing
            return objectMapper.readTree(jsonResponse).path("usd");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not collect currency exchange rates: " + e.getMessage());
            return null;
        }
    }

    private JsonNode queryExchangeRates() {
        return queryExchangeRates(null);
    }

    private void queryBitcoinMiningData() {
        try {
            LOGGER.log(Level.INFO, "Collecting bitcoin mining data...");
            bitcoinMiningDataFetcher.query();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not collect bitcoin mining data: " + e.getMessage());
        }
    }

    @Transactional
    public BitcoinNetworkStats fetchAndSaveBitcoinStatsForDate(LocalDate targetDate) {
        LOGGER.log(Level.INFO, "Fetching historical Bitcoin network data from mempool.space for date: " + targetDate);
        try {
            // HIER WURDE DER SPEICHERFRESSER BEHOBEN: Nur 1x parsen!
            String hashrateResponse = sendGetRequest("https://mempool.space/api/v1/mining/hashrate/all");
            JsonNode hashrateRoot = objectMapper.readTree(hashrateResponse);
            JsonNode hashrateArray = hashrateRoot.path("hashrates");

            long difficulty = 0;
            double hashRateThs = 0.0;
            boolean foundHashrate = false;

            for (JsonNode obj : hashrateArray) {
                LocalDate date = Instant.ofEpochSecond(obj.path("timestamp").asLong()).atZone(ZoneOffset.UTC).toLocalDate();

                if (date.equals(targetDate)) {
                    // Werte direkt vom Root lesen
                    difficulty = hashrateRoot.path("currentDifficulty").asLong();
                    hashRateThs = hashrateRoot.path("avgHashrate").asDouble() / 1_000_000_000_000.0;
                    foundHashrate = true;
                    break;
                }
            }

            if (!foundHashrate) {
                LOGGER.log(Level.WARNING, "No network data found for date " + targetDate);
                return null;
            }

            String priceResponse = sendGetRequest("https://mempool.space/api/v1/historical-price");
            JsonNode pricesRoot = objectMapper.readTree(priceResponse);
            JsonNode pricesArray = pricesRoot.path("prices");

            double priceUsd = 0.0;
            for (JsonNode obj : pricesArray) {
                LocalDate date = Instant.ofEpochSecond(obj.path("time").asLong()).atZone(ZoneOffset.UTC).toLocalDate();

                if (date.equals(targetDate)) {
                    priceUsd = obj.path("USD").asDouble(0.0);
                    break;
                }
            }

            String feesResponse = sendGetRequest("https://mempool.space/api/v1/mining/blocks/fees/3y");
            JsonNode feesArray = objectMapper.readTree(feesResponse);

            int averageBlockFee = 0;
            long avgBlockHeight = 0;

            for (JsonNode obj : feesArray) {
                LocalDate date = Instant.ofEpochSecond(obj.path("timestamp").asLong()).atZone(ZoneOffset.UTC).toLocalDate();

                if (date.equals(targetDate)) {
                    averageBlockFee = obj.path("avgFees").asInt();
                    avgBlockHeight = obj.path("avgHeight").asLong();
                    break;
                }
            }

            BitcoinNetworkStats btcStats = new BitcoinNetworkStats(targetDate);
            btcStats.setMiningDifficulty(difficulty);
            btcStats.setHashRateInThs(hashRateThs);
            btcStats.setPriceInDollar(priceUsd);
            btcStats.setAverageTxPrice24h(averageBlockFee);

            long initialSubsidySats = 50_0000_0000L;
            long halvings = avgBlockHeight / 210000;
            long currentSubsidy = initialSubsidySats >> halvings;

            btcStats.setBlockSubsidy((int) currentSubsidy);

            LOGGER.log(Level.INFO, "Successfully backfilled full Bitcoin stats for UTC date: " + targetDate);
            return bitcoinRepository.save(btcStats);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not fetch historical Bitcoin stats: " + e.getMessage(), e);
            return null;
        }
    }
}