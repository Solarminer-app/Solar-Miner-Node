package de.verdox.currencyrates.currencyrates.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import de.verdox.currencyrates.currencyrates.service.DataQueryService;
import de.verdox.currencyrates.currencyrates.service.CoinGeckoPriceService;
import de.verdox.currencyrates.currencyrates.service.MiningNetworkDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Market data")
public class PublicDataController {

    private final DataQueryService queryService;
    private final CoinGeckoPriceService coinPrices;
    private final MiningNetworkDataService miningNetworks;

    public PublicDataController(DataQueryService queryService, CoinGeckoPriceService coinPrices,
                                MiningNetworkDataService miningNetworks) {
        this.queryService = queryService;
        this.coinPrices = coinPrices;
        this.miningNetworks = miningNetworks;
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

    /** Current or historic USD prices. Coin IDs are SolarMiner's btc, xmr, prl, rvn and etc. */
    @GetMapping("/coin-prices")
    public ResponseEntity<Map<String, Double>> coinPrices(@RequestParam(name = "date", required = false) LocalDate date) {
        return coinPrices.prices(date == null ? LocalDate.now(java.time.ZoneOffset.UTC) : date)
                .map(prices -> ResponseEntity.ok(prices.getPrices())).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/mining-networks")
    public java.util.List<MiningNetworkDataService.PublicSnapshot> miningNetworks() {
        return miningNetworks.snapshots();
    }

    @GetMapping("/mining-networks/{coin}")
    public ResponseEntity<MiningNetworkDataService.PublicSnapshot> miningNetwork(@PathVariable String coin) {
        return miningNetworks.snapshot(coin).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
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
