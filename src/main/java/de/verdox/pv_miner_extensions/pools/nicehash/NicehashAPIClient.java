package de.verdox.pv_miner_extensions.pools.nicehash;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class NicehashAPIClient {
    private static final HttpClient client = HttpClient.newHttpClient();

    public static List<String> queryWorkerNames(String orgId, String apiKey, String secret) throws Exception {
        JsonObject address = sendGetRequest("/main/api/v2/mining/groups/list", "", null, UUID.randomUUID().toString(), orgId, apiKey, secret).getAsJsonObject();

        List<String> names = new LinkedList<>();

        JsonObject groups = address.get("groups").getAsJsonObject();
        groups.keySet().forEach(groupName -> {

            JsonArray rigsOfThisGroup = groups.get(groupName).getAsJsonObject().get("rigs").getAsJsonArray();
            for (JsonElement jsonElement : rigsOfThisGroup) {
                names.add(jsonElement.getAsJsonObject().get("rigId").getAsString());
            }

        });
        return names;
    }

    public static List<NiceHashPoolEntity.Payout> queryPayouts(String orgId, String apiKey, String secret) throws Exception {
        List<NiceHashPoolEntity.Payout> payoutList = new LinkedList<>();
        JsonObject payouts = sendGetRequest("/main/api/v2/mining/rigs/payouts", "", null, UUID.randomUUID().toString(), orgId, apiKey, secret).getAsJsonObject();
        for (JsonElement jsonElement : payouts.get("list").getAsJsonArray()) {

            long date = jsonElement.getAsJsonObject().get("created").getAsLong();
            int amountSatoshis = (int) ((jsonElement.getAsJsonObject().get("amount").getAsDouble() * Math.pow(10, 8)));

            payoutList.add(new NiceHashPoolEntity.Payout(date, amountSatoshis));
        }
        return payoutList;
    }

    public static void ping(String orgId, String apiKey, String secret) throws Exception {
        JsonObject address = sendGetRequest("/main/api/v2/mining/miningAddress", "", null, UUID.randomUUID().toString(), orgId, apiKey, secret).getAsJsonObject();
        address.get("address").getAsString();
    }

    public static NiceHashPoolEntity.UnpaidAmount queryUnpaid(String worker, String orgId, String apiKey, String secret) throws Exception {
        JsonObject minerStats = sendGetRequest("/main/api/v2/mining/rig2/" + worker, "", null, UUID.randomUUID().toString(), orgId, apiKey, secret).getAsJsonObject();
        int satoshiUnpaid = (int) (minerStats.get("unpaidAmount").getAsDouble() * Math.pow(10, 8));
        return new NiceHashPoolEntity.UnpaidAmount(worker, satoshiUnpaid);
    }

    public static int querySatoshiPayoutMin(String orgId, String apiKey, String secret) throws Exception {
        JsonObject globalStats = sendGetRequest("/main/api/v2/mining/rigs2", "", null, UUID.randomUUID().toString(), orgId, apiKey, secret).getAsJsonObject();
        return globalStats.get("payoutAmount").getAsInt();
    }

    private static JsonElement sendGetRequest(String requestPath, String queryString, @Nullable JsonObject requestBody, String requestId, String orgId, String apiKey, String secret) throws Exception {

        Map<String, String> headers = buildHeaders(apiKey, secret, orgId, "GET", requestPath, queryString, requestBody);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(new URI("https://api2.nicehash.com" + requestPath))
                .GET();

        headers.forEach(requestBuilder::setHeader);

        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        try (JsonReader jsonReader = new JsonReader(new StringReader(response.body()))) {
            jsonReader.setStrictness(Strictness.LENIENT);

            return JsonParser.parseReader(jsonReader);
        }
    }

    public static Map<String, String> buildHeaders(String apiKey,
                                                   String apiSecret,
                                                   String orgId,
                                                   String requestMethod,
                                                   String requestPath,
                                                   String queryString,
                                                   @Nullable JsonObject requestBody) throws Exception {
        Charset charset = StandardCharsets.ISO_8859_1;
        String time = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();

        byte zero = 0x00;
        ByteArrayBuilder input = new ByteArrayBuilder();
        input.append(apiKey.getBytes(charset));
        input.append(zero);
        input.append(time.getBytes(charset));
        input.append(zero);
        input.append(nonce.getBytes(charset));
        input.append(zero);
        input.append(zero);
        input.append(orgId.getBytes(charset));
        input.append(zero);
        input.append(zero);
        input.append(requestMethod.toUpperCase().getBytes(charset));
        input.append(zero);
        input.append(requestPath.getBytes(charset));
        input.append(zero);
        input.append(queryString.getBytes(charset));

        if (requestBody != null) {
            byte[] requestBodyBytes = requestBody.getAsString().getBytes(StandardCharsets.UTF_8);

            if (requestBodyBytes.length > 0) {
                input.append(zero);
                input.append(requestBodyBytes);
            }
        }

        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.ISO_8859_1), "HmacSHA256");
        mac.init(secretKey);
        byte[] hmac = mac.doFinal(input.toByteArray());

        StringBuilder hex = new StringBuilder();
        for (byte b : hmac) {
            hex.append(String.format("%02x", b));
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Time", time);
        headers.put("X-Nonce", nonce);
        headers.put("X-Organization-Id", orgId);
        headers.put("X-Request-Id", requestId);
        headers.put("X-Auth", apiKey + ":" + hex);

        return headers;
    }

    static class ByteArrayBuilder {
        private final ByteArrayOutputStream stream = new ByteArrayOutputStream();

        public void append(byte b) {
            stream.write(b);
        }

        public void append(byte[] bytes) {
            try {
                stream.write(bytes);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public byte[] toByteArray() {
            return stream.toByteArray();
        }
    }
}
