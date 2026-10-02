package de.verdox.currencyrates.currencyrates.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class BitcoinMiningDataFetcher {

    private final ObjectMapper objectMapper;
    private final HttpTextClient httpClient;

    public BitcoinMiningDataFetcher(ObjectMapper objectMapper, HttpTextClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    /**
     * Builds a complete snapshot before publishing it to callers. A failed provider request
     * therefore cannot expose a mix of values from two refreshes.
     */
    public BitcoinMiningSnapshot query() throws Exception {
        long miningDifficulty = new java.math.BigDecimal(get("https://blockchain.info/q/getdifficulty")).longValue();
        double hashRate = Double.parseDouble(get("https://blockchain.info/q/hashrate"));
        double priceInDollar = Double.parseDouble(get("https://blockchain.info/q/24hrprice")) / Math.pow(10, 8);
        int blockSubsidy = (int) (Double.parseDouble(get("https://blockchain.info/q/bcperblock")) * Math.pow(10, 8));

        String statsJson = get("https://api.blockchair.com/bitcoin/stats");
        JsonNode rootNode = objectMapper.readTree(statsJson);
        int averageTxPrice24h = rootNode.path("data").path("average_transaction_fee_24h").asInt();

        return new BitcoinMiningSnapshot(
                miningDifficulty,
                hashRate / 1000,
                priceInDollar,
                blockSubsidy,
                averageTxPrice24h
        );
    }

    private String get(String url) throws Exception {
        return httpClient.get(URI.create(url));
    }

    public record BitcoinMiningSnapshot(
            long miningDifficulty,
            double hashRateInThs,
            double priceInDollar,
            int blockSubsidy,
            int averageTxPrice24h
    ) {
    }
}
