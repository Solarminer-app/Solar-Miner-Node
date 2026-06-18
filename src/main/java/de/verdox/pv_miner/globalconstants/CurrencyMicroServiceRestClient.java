package de.verdox.pv_miner.globalconstants;

import java.time.LocalDate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

public class CurrencyMicroServiceRestClient {
    private final RestClient restClient;

    public CurrencyMicroServiceRestClient(String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public Optional<BitcoinNetworkStatsDTO> getBitcoinStats(LocalDate date, String timezone) {
        try {
            BitcoinNetworkStatsDTO response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/public/bitcoin-stats")
                            .queryParam("date", date.toString())
                            .queryParam("timezone", timezone)
                            .build())
                    .retrieve()
                    .body(BitcoinNetworkStatsDTO.class);
            return Optional.ofNullable(response);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public Optional<Map<String, Double>> getAllExchangeRates(LocalDate date, String timezone) {
        try {
            Map<String, Double> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/public/exchange-rates")
                            .queryParam("date", date.toString())
                            .queryParam("timezone", timezone)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return Optional.ofNullable(response);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public Optional<ConversionResponseDTO> getConversionRate(String base, String target, LocalDate date, String timezone) {
        try {
            ConversionResponseDTO response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/public/exchange-rates/convert")
                            .queryParam("base", base)
                            .queryParam("target", target)
                            .queryParam("date", date.toString())
                            .queryParam("timezone", timezone)
                            .build())
                    .retrieve()
                    .body(ConversionResponseDTO.class);
            return Optional.ofNullable(response);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public record BitcoinNetworkStatsDTO(
            LocalDate date,
            double priceInDollar,
            long difficulty,
            double hashRateThs,
            int blockSubsidy,
            int averageTxPrice24h
    ) {}

    public record ConversionResponseDTO(
            String baseCurrency,
            String targetCurrency,
            double exchangeRate,
            LocalDate dataUtcDate
    ) {}
}
