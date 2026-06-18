package de.verdox.currencyrates.currencyrates.service;

import com.google.gson.JsonParser;
import lombok.Getter;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class BitcoinMiningDataFetcher extends BlockchainMiningDataFetcher {
    @Getter
    private transient long miningDifficulty;
    @Getter
    private transient double priceInDollar;
    @Getter
    private transient int blockSubsidy;
    @Getter
    private transient int averageTxPrice24h;

    private transient double hashRate;

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
        averageTxPrice24h = JsonParser.parseString(sendGetRequest("https://api.blockchair.com/bitcoin/stats")).getAsJsonObject().get("data").getAsJsonObject().get("average_transaction_fee_24h").getAsInt();
    }

    private static String sendGetRequest(String url) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        }
    }
}
