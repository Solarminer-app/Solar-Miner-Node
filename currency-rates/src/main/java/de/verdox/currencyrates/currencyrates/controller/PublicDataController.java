package de.verdox.currencyrates.currencyrates.controller;

import de.verdox.currencyrates.currencyrates.service.DataQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/public")
public class PublicDataController {

    private final DataQueryService queryService;

    public PublicDataController(DataQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/bitcoin-stats")
    public ResponseEntity<BitcoinNetworkStatsDTO> getBitcoinStats(
            @RequestParam(name = "date") LocalDate date,
            @RequestParam(name = "timezone", defaultValue = "UTC") String timezone) {
        var found = queryService.getBitcoinStatsForDate(date, timezone);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return found.map(stats -> new BitcoinNetworkStatsDTO(stats.getDate(), stats.getPriceInDollar(), stats.getMiningDifficulty(), stats.getHashRateInThs(), stats.getBlockSubsidy(), stats.getAverageTxPrice24h()))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/exchange-rates")
    public ResponseEntity<Map<String, Double>> getAllExchangeRates(
            @RequestParam(name = "date") LocalDate date,
            @RequestParam(name = "timezone", defaultValue = "UTC") String timezone) {

        return queryService.getAllRatesForDate(date, timezone)
                .map(dailyUsdRates -> ResponseEntity.ok(dailyUsdRates.getRates()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/exchange-rates/convert")
    public ResponseEntity<ConversionResponseDTO> getConversionRate(
            @RequestParam(name = "base") String baseCurrency,
            @RequestParam(name = "target") String targetCurrency,
            @RequestParam(name = "date") LocalDate date,
            @RequestParam(name = "timezone", defaultValue = "UTC") String timezone) {

        return queryService.getConversionRate(baseCurrency, targetCurrency, date, timezone)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record BitcoinNetworkStatsDTO(
            LocalDate date,
            double priceInDollar,
            long difficulty,
            double hashRateThs,
            int blockSubsidy,
            int averageTxPrice24h
    ) {
    }

    public record ConversionResponseDTO(
            String baseCurrency,
            String targetCurrency,
            double exchangeRate,
            LocalDate dataUtcDate
    ) {
    }
}