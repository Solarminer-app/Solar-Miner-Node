package de.verdox.currencyrates.currencyrates.service;

import de.verdox.currencyrates.currencyrates.controller.PublicDataController;
import de.verdox.currencyrates.currencyrates.model.BitcoinNetworkStats;
import de.verdox.currencyrates.currencyrates.model.DailyUsdRates;
import de.verdox.currencyrates.currencyrates.repository.BitcoinNetworkStatsRepository;
import de.verdox.currencyrates.currencyrates.repository.DailyUsdRatesRepository;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class DataQueryService {
    private static final Logger LOGGER = Logger.getLogger(DataQueryService.class.getName());
    private final BitcoinNetworkStatsRepository bitcoinRepository;
    private final DailyUsdRatesRepository ratesRepository;
    private final DataGathererService dataGathererService;

    public DataQueryService(BitcoinNetworkStatsRepository bitcoinRepository, DailyUsdRatesRepository ratesRepository, DataGathererService dataGathererService) {
        this.bitcoinRepository = bitcoinRepository;
        this.ratesRepository = ratesRepository;
        this.dataGathererService = dataGathererService;
    }

    public Optional<BitcoinNetworkStats> getBitcoinStatsForDate(LocalDate date, String timezone) {
        LocalDate utcDate = resolveUtcDate(date, timezone);
        return bitcoinRepository.findById(utcDate);
    }

    public Optional<DailyUsdRates> getAllRatesForDate(LocalDate date, String timezone) {
        LocalDate utcDate = resolveUtcDate(date, timezone);
        Optional<DailyUsdRates> existingData = ratesRepository.findById(utcDate);
        if (existingData.isEmpty()) {
            LOGGER.info("Loading currency data for " + utcDate);
            try {
                DailyUsdRates freshData = dataGathererService.fetchAndSaveForDate(utcDate);
                return Optional.of(freshData);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Could not load currency data for "+utcDate, e);
                return Optional.empty();
            }
        }

        return existingData;
    }

    public Optional<PublicDataController.ConversionResponseDTO> getConversionRate(String baseCurrency, String targetCurrency, LocalDate date, String timezone) {
        LocalDate utcDate = resolveUtcDate(date, timezone);
        Optional<DailyUsdRates> ratesOpt = ratesRepository.findById(utcDate);

        if (ratesOpt.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Double> rates = ratesOpt.get().getRates();
        String baseLower = baseCurrency.toLowerCase();
        String targetLower = targetCurrency.toLowerCase();

        double baseToUsdRate = baseLower.equals("usd") ? 1.0 : rates.getOrDefault(baseLower, 0.0);
        double targetToUsdRate = targetLower.equals("usd") ? 1.0 : rates.getOrDefault(targetLower, 0.0);

        if (baseToUsdRate == 0.0 || targetToUsdRate == 0.0) {
            return Optional.empty();
        }
        double conversionRate = targetToUsdRate / baseToUsdRate;

        return Optional.of(new PublicDataController.ConversionResponseDTO(baseCurrency, targetCurrency, conversionRate, utcDate));
    }

    private LocalDate resolveUtcDate(LocalDate localDate, String timezoneId) {
        ZoneId zoneId = ZoneId.of(timezoneId);
        ZonedDateTime zonedDateTime = ZonedDateTime.of(localDate, LocalTime.NOON, zoneId);
        return zonedDateTime.withZoneSameInstant(ZoneOffset.UTC).toLocalDate();
    }
}
