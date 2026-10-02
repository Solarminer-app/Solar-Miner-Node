package de.verdox.currencyrates.currencyrates.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.currencyrates.currencyrates.model.BitcoinNetworkStats;
import de.verdox.currencyrates.currencyrates.model.DailyUsdRates;
import de.verdox.currencyrates.currencyrates.repository.BitcoinNetworkStatsRepository;
import de.verdox.currencyrates.currencyrates.repository.DailyUsdRatesRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class DataGathererService {
    private static final Logger LOGGER = Logger.getLogger(DataGathererService.class.getSimpleName());

    private final BitcoinNetworkStatsRepository bitcoinRepository;
    private final DailyUsdRatesRepository ratesRepository;
    private final ObjectMapper objectMapper;
    private final CoinGeckoPriceService coinPrices;
    private final BitcoinMiningDataFetcher bitcoinMiningDataFetcher;
    private final HttpTextClient httpClient;
    private final boolean refreshOnStartup;

    private volatile BitcoinMiningDataFetcher.BitcoinMiningSnapshot latestBitcoinSnapshot;

    public DataGathererService(BitcoinNetworkStatsRepository bitcoinRepository,
                               DailyUsdRatesRepository ratesRepository,
                               ObjectMapper objectMapper,
                               CoinGeckoPriceService coinPrices,
                               BitcoinMiningDataFetcher bitcoinMiningDataFetcher,
                               HttpTextClient httpClient,
                               @Value("${currency-rates.refresh-on-startup:true}") boolean refreshOnStartup) {
        this.bitcoinRepository = bitcoinRepository;
        this.ratesRepository = ratesRepository;
        this.objectMapper = objectMapper;
        this.coinPrices = coinPrices;
        this.bitcoinMiningDataFetcher = bitcoinMiningDataFetcher;
        this.httpClient = httpClient;
        this.refreshOnStartup = refreshOnStartup;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationReady() {
        if (!refreshOnStartup) {
            LOGGER.log(Level.INFO, "Skipping initial market refresh because currency-rates.refresh-on-startup is disabled.");
            return;
        }
        LOGGER.log(Level.INFO, "Fetching global constants...");
        collectGlobalConstants();
        if (!saveDailyStatsToDatabase()) {
            LOGGER.log(Level.WARNING, "Initial daily market snapshot was not persisted; the next scheduled refresh will retry.");
        }
        coinPrices.fetch(LocalDate.now(ZoneOffset.UTC));
        LOGGER.log(Level.INFO, "Done...");
    }

    private void collectGlobalConstants() {
        queryBitcoinMiningData();
    }

    @Scheduled(fixedRateString = "${currency-rates.refresh.fixed-rate-ms:3600000}")
    public void scheduledFetch() {
        collectGlobalConstants();
    }

    @Scheduled(cron = "0 0 0 * * ?", zone = "UTC")
    @Transactional
    public void scheduledDailyDatabaseSave() {
        LOGGER.log(Level.INFO, "Starting scheduled daily UTC database backup...");
        collectGlobalConstants();
        if (!saveDailyStatsToDatabase()) {
            LOGGER.log(Level.WARNING, "Scheduled daily market snapshot was not persisted; it will be retried on the next run.");
        }
        coinPrices.fetch(LocalDate.now(ZoneOffset.UTC));
    }

    @Transactional
    public DailyUsdRates fetchAndSaveForDate(LocalDate date) {
        LOGGER.log(Level.INFO, "Fetching data for date: " + date);
        JsonNode ratesNode = queryExchangeRates(date);

        Map<String, Double> ratesMap = extractRatesMap(ratesNode);

        if (ratesMap.isEmpty()) {
            LOGGER.log(Level.WARNING, "No data found for date: " + date);
            return null;
        }

        DailyUsdRates dailyRates = new DailyUsdRates(date, ratesMap);
        return ratesRepository.save(dailyRates);
    }

    @Transactional
    public synchronized boolean saveDailyStatsToDatabase() {
        try {
            LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
            JsonNode currencyRatesUSD = queryExchangeRates();
            Map<String, Double> ratesMap = extractRatesMap(currencyRatesUSD);
            BitcoinMiningDataFetcher.BitcoinMiningSnapshot bitcoinSnapshot = latestBitcoinSnapshot;

            if (bitcoinSnapshot == null || ratesMap.isEmpty()) {
                LOGGER.log(Level.WARNING, "Cannot save to database: a complete Bitcoin and exchange-rate snapshot is unavailable.");
                return false;
            }

            if (!bitcoinRepository.existsById(todayUtc)) {
                BitcoinNetworkStats btcStats = new BitcoinNetworkStats(todayUtc);
                btcStats.setMiningDifficulty(bitcoinSnapshot.miningDifficulty());
                btcStats.setHashRateInThs(bitcoinSnapshot.hashRateInThs());
                btcStats.setPriceInDollar(bitcoinSnapshot.priceInDollar());
                btcStats.setBlockSubsidy(bitcoinSnapshot.blockSubsidy());
                btcStats.setAverageTxPrice24h(bitcoinSnapshot.averageTxPrice24h());

                bitcoinRepository.save(btcStats);
                LOGGER.log(Level.INFO, "Successfully saved Bitcoin network stats for UTC date: " + todayUtc);
            } else {
                LOGGER.log(Level.INFO, "Bitcoin network stats for " + todayUtc + " already exist. Skipping.");
            }

            if (!ratesRepository.existsById(todayUtc)) {
                DailyUsdRates dailyRates = new DailyUsdRates(todayUtc, ratesMap);
                ratesRepository.save(dailyRates);
                LOGGER.log(Level.INFO, "Successfully saved daily currency rates for UTC date: " + todayUtc);
            } else {
                LOGGER.log(Level.INFO, "Currency rates for " + todayUtc + " already exist. Skipping.");
            }

            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error while saving daily stats to database: " + e.getMessage(), e);
            return false;
        }
    }

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

    private JsonNode queryExchangeRates(LocalDate date) {
        try {
            String dateParam = (date == null) ? "latest" : date.toString();
            LOGGER.log(Level.INFO, "Collecting currency exchange rates for date: " + dateParam);
            String jsonResponse = httpClient.get(exchangeRatesUri(date));


            return objectMapper.readTree(jsonResponse).path("usd");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not collect currency exchange rates: " + e.getMessage());
            return null;
        }
    }

    private JsonNode queryExchangeRates() {
        return queryExchangeRates(null);
    }

    static URI exchangeRatesUri(LocalDate date) {
        String version = date == null ? "latest" : date.toString();
        return URI.create("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@" + version + "/v1/currencies/usd.json");
    }

    private void queryBitcoinMiningData() {
        try {
            LOGGER.log(Level.INFO, "Collecting bitcoin mining data...");
            latestBitcoinSnapshot = bitcoinMiningDataFetcher.query();
        } catch (Exception e) {
            latestBitcoinSnapshot = null;
            LOGGER.log(Level.SEVERE, "Could not collect bitcoin mining data: " + e.getMessage());
        }
    }

    @Transactional
    public BitcoinNetworkStats fetchAndSaveBitcoinStatsForDate(LocalDate targetDate) {
        LOGGER.log(Level.INFO, "Fetching historical Bitcoin network data from mempool.space for date: " + targetDate);
        try {
            String hashrateResponse = httpClient.get(URI.create("https://mempool.space/api/v1/mining/hashrate/all"));
            JsonNode hashrateRoot = objectMapper.readTree(hashrateResponse);
            JsonNode hashrateArray = hashrateRoot.path("hashrates");

            long difficulty = 0;
            double hashRateThs = 0.0;
            boolean foundHashrate = false;

            for (JsonNode obj : hashrateArray) {
                LocalDate date = Instant.ofEpochSecond(obj.path("timestamp").asLong()).atZone(ZoneOffset.UTC).toLocalDate();

                if (date.equals(targetDate)) {

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

            String priceResponse = httpClient.get(URI.create("https://mempool.space/api/v1/historical-price"));
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

            String feesResponse = httpClient.get(URI.create("https://mempool.space/api/v1/mining/blocks/fees/3y"));
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
