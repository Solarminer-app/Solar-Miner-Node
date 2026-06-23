package de.verdox.pv_miner.globalconstants;

import de.verdox.pv_miner.util.Money;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@EnableScheduling
@Service
public class GlobalConstantsService {
    private static final Logger LOGGER = Logger.getLogger(GlobalConstantsService.class.getSimpleName());

    private final CurrencyMicroServiceRestClient restClient;

    private Map<String, Double> currentExchangeRates = new ConcurrentHashMap<>();
    private CurrencyMicroServiceRestClient.BitcoinNetworkStatsDTO currentBitcoinStats;

    private final Map<LocalDate, Map<String, Double>> historicalRatesCache = new ConcurrentHashMap<>();
    private final Map<LocalDate, CurrencyMicroServiceRestClient.BitcoinNetworkStatsDTO> historicalBtcCache = new ConcurrentHashMap<>();

    public GlobalConstantsService(@Value("${solarmining.currency-service.url}") String url) {
        if (url == null) {
            throw new NullPointerException("Environment variable solarmining.currency-service.url is not set");
        }
        this.restClient = new CurrencyMicroServiceRestClient(url);
        LOGGER.info("Started Global Constants Client Service listening on: " + url);
    }

    @PostConstruct
    public void onApplicationReady() {
        LOGGER.log(Level.INFO, "Initial fetching of global constants from remote Microservice...");
        fetchLatestData();
        fetchHistoricalData();
        LOGGER.log(Level.INFO, "Done...");
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    public void scheduledFetch() {
        fetchLatestData();
    }

    @Scheduled(cron = "0 5 0 * * ?", zone = "UTC")
    public void scheduledHistoricalFetch() {
        fetchHistoricalData();
    }

    public long getTodayMiningDifficulty() {
        if(currentBitcoinStats == null) {
            return 0;
        }
        return currentBitcoinStats.difficulty();
    }

    public int getTodayBlockSubsidy() {
        if(currentBitcoinStats == null) {
            return 0;
        }
        return currentBitcoinStats.blockSubsidy();
    }

    public int getTodayAverageTxPrice24h() {
        if(currentBitcoinStats == null) {
            return 0;
        }
        return currentBitcoinStats.averageTxPrice24h();
    }

    private void fetchLatestData() {
        try {
            LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);

            restClient.getAllExchangeRates(todayUtc, "UTC")
                    .ifPresent(rates -> this.currentExchangeRates = rates);

            restClient.getBitcoinStats(todayUtc, "UTC")
                    .ifPresent(stats -> this.currentBitcoinStats = stats);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not fetch latest data from Microservice: " + e.getMessage());
        }
    }

    private double getHistoricalRateInUsd(String currencyCode, LocalDate date) {
        if (!historicalRatesCache.containsKey(date)) {
            synchronized (restClient) {
                restClient.getAllExchangeRates(date, "UTC").ifPresent(rates -> historicalRatesCache.put(date, rates));
            }
        }
        return Optional.ofNullable(historicalRatesCache.get(date))
                .map(rates -> rates.getOrDefault(currencyCode.toLowerCase(), -1.0))
                .orElse(-1.0);
    }

    private void fetchHistoricalData() {
        LOGGER.log(Level.INFO, "Refreshing historical 30-day cache...");
        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);

        for (int i = 0; i <= 30; i++) {
            LocalDate targetDate = todayUtc.minusDays(i);

            if (!historicalRatesCache.containsKey(targetDate)) {
                synchronized (restClient) {
                    restClient.getAllExchangeRates(targetDate, "UTC").ifPresent(rates -> historicalRatesCache.put(targetDate, rates));
                }
            }
            if (!historicalBtcCache.containsKey(targetDate)) {
                synchronized (restClient) {
                    restClient.getBitcoinStats(targetDate, "UTC").ifPresent(stats -> historicalBtcCache.put(targetDate, stats));
                }
            }
        }
    }

    public double getDollarRate(CustomCurrency currency) {
        String code = currency.getCurrencyCode().toLowerCase(Locale.ROOT);
        if (code.equals("usd")) return 1.0;

        if (!currentExchangeRates.containsKey(code)) {
            LOGGER.log(Level.WARNING, "Unknown currency or no data available for code: " + code);
            return 0.0;
        }
        return currentExchangeRates.get(code);
    }

    public double getExchangeRate(CustomCurrency from, CustomCurrency to) {
        double dollarRateFrom = getDollarRate(from);
        double dollarRateTo = getDollarRate(to);

        if (dollarRateFrom == 0.0) return 0.0;
        return dollarRateTo / dollarRateFrom;
    }

    public Money convert(Money money, CustomCurrency currency) {
        double rate = getExchangeRate(money.getCurrency(), currency);
        return new Money(rate * money.getRawMoneyAmount(), currency);
    }

    public double convertHistorical(double amount, CustomCurrency from, CustomCurrency to, LocalDate date) {
        String fromCode = from.getCurrencyCode().toLowerCase(Locale.ROOT);
        String toCode = to.getCurrencyCode().toLowerCase(Locale.ROOT);

        double rateFromInUsd = getHistoricalRateInUsd(fromCode, date);
        double rateToInUsd = getHistoricalRateInUsd(toCode, date);

        if (rateFromInUsd <= 0 || rateToInUsd <= 0) {
            return -1.0;
        }

        double amountInUsd = amount / rateFromInUsd;

        if (toCode.equals("btc")) {
            return amountInUsd / rateToInUsd;
        } else {
            return amountInUsd * rateToInUsd;
        }
    }
}