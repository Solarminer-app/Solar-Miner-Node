package de.verdox.currencyrates.currencyrates.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class BitcoinMiningDataFetcher extends BlockchainMiningDataFetcher {

    // Globaler, wiederverwendbarer HTTP-Client (Spart RAM und offene Ports)
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final ObjectMapper objectMapper;

    @Getter
    private transient long miningDifficulty;
    @Getter
    private transient double priceInDollar;
    @Getter
    private transient int blockSubsidy;
    @Getter
    private transient int averageTxPrice24h;

    private transient double hashRate;

    // Wir übergeben den ObjectMapper via Konstruktor
    public BitcoinMiningDataFetcher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public double getGlobalHashRateInThs() {
        return hashRate / 1000;
    }

    @Override
    public BigDecimal getCommaSeparator() {
        return BigDecimal.valueOf(Math.pow(10, 8));
    }

    public void query() throws Exception {
        miningDifficulty = new BigDecimal(sendGetRequest("https://blockchain.info/q/getdifficulty")).longValue();
        hashRate = Double.parseDouble(sendGetRequest("https://blockchain.info/q/hashrate"));
        priceInDollar = Double.parseDouble(sendGetRequest("https://blockchain.info/q/24hrprice")) / Math.pow(10, 8);
        blockSubsidy = (int) (Double.parseDouble(sendGetRequest("https://blockchain.info/q/bcperblock")) * Math.pow(10, 8));

        // Jackson statt Gson!
        String statsJson = sendGetRequest("https://api.blockchair.com/bitcoin/stats");
        JsonNode rootNode = objectMapper.readTree(statsJson);
        averageTxPrice24h = rootNode.path("data").path("average_transaction_fee_24h").asInt();
    }

    private static String sendGetRequest(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(url))
                .GET()
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
}