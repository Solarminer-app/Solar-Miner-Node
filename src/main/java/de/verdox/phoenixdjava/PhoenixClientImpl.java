package de.verdox.phoenixdjava;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PhoenixClientImpl implements PhoenixClient {

    private final String baseUrl;
    private final String authHeader;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public PhoenixClientImpl(String baseUrl, String password) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String rawAuth = ":" + password;
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(rawAuth.getBytes(StandardCharsets.UTF_8));

        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public PhoenixDTOs.NodeInfo getInfo() throws IOException, InterruptedException {
        return executeGet("/getinfo", PhoenixDTOs.NodeInfo.class);
    }

    @Override
    public String getLightningAddress() throws IOException, InterruptedException {
        return executeGet("/getlnaddress", String.class);
    }

    @Override
    public String payOnChain(long amountSat, String address, long feeRateSatByte) throws IOException, InterruptedException {
        Map<String, String> params = new HashMap<>();
        params.put("address", address);
        params.put("amountSat", amountSat + "");
        params.put("feerateSatByte", feeRateSatByte + "");
        return executePost("/sendtoaddress", params, String.class, MimeType.PLAIN);
    }

    @Override
    public String bumpFee(long feeRateSatByte) throws IOException, InterruptedException {
        Map<String, String> params = new HashMap<>();
        params.put("feerateSatByte", feeRateSatByte + "");
        return executePost("/bumpfee", params, String.class, MimeType.PLAIN);
    }

    @Override
    public PhoenixDTOs.PayResponse payBolt11InvoiceRaw(String invoice, long amountSat, boolean sendAll) throws IOException, InterruptedException {
        Map<String, String> params = new HashMap<>();

        params.put("invoice", invoice);

        if (sendAll) {
            params.put("sendAll", "true");
        } else {
            params.put("amountSat", amountSat + "");
        }
        return executePost("/payinvoice", params, PhoenixDTOs.PayResponse.class, MimeType.JSON);
    }

    @Override
    public PhoenixDTOs.PayResponse payBolt12OfferRaw(String offer, long amountSat, boolean sendAll, String messageForRecipient) throws IOException, InterruptedException {
        Map<String, String> params = new HashMap<>();

        params.put("offer", offer);
        if (messageForRecipient != null) params.put("message", messageForRecipient);

        if (sendAll) {
            params.put("sendAll", "true");
        } else {
            params.put("amountSat", amountSat + "");
        }
        return executePost("/payinvoice", params, PhoenixDTOs.PayResponse.class, MimeType.JSON);
    }

    @Override
    public PhoenixDTOs.PayResponse payLightningAddressRaw(String address, long amountSat, boolean sendAll, String messageForRecipient) throws IOException, InterruptedException {
        Map<String, String> params = new HashMap<>();

        params.put("address", address);
        if (messageForRecipient != null) params.put("message", messageForRecipient);

        if (sendAll) {
            params.put("sendAll", "true");
        } else {
            params.put("amountSat", amountSat + "");
        }
        return executePost("/payinvoice", params, PhoenixDTOs.PayResponse.class, MimeType.JSON);
    }

    @Override
    public PhoenixDTOs.WalletBalance getBalance() throws IOException, InterruptedException {
        return executeGet("/getbalance", PhoenixDTOs.WalletBalance.class);
    }

    @Override
    public String closeChannel(String channelId, String refundAddress, long feerateSatByte) throws IOException, InterruptedException {
        Map<String, String> params = new HashMap<>();
        params.put("channelId", channelId);
        params.put("refundAddress", refundAddress);
        params.put("feerateSatByte", String.valueOf(feerateSatByte));

        return executePost("/createoffer", params, String.class, MimeType.PLAIN);
    }

    @Override
    public PhoenixDTOs.EstimatedLiquidityResponse estimateLiquidityFees(long amountSat) throws IOException, InterruptedException {
        return executeGet("/payments/estimateliquidityfees?amountSat=" + amountSat, PhoenixDTOs.EstimatedLiquidityResponse.class);
    }

    @Override
    public List<PhoenixDTOs.IncomingPayment> listIncomingPayments(Instant from, Instant to, int limit, int offset, boolean all, String externalId) throws IOException, InterruptedException {
        if (externalId != null) {
            return executeGet("/payments/incoming?all=" + all + "&from=" + from.toEpochMilli() + "&to=" + to.toEpochMilli() + "&limit=" + limit + "&offset=" + offset + "&externalId=" + externalId, new TypeReference<>() {
            });
        }
        return executeGet("/payments/incoming?all=" + all + "&from=" + from.toEpochMilli() + "&to=" + to.toEpochMilli() + "&limit=" + limit + "&offset=" + offset, new TypeReference<>() {
        });
    }

    @Override
    public PhoenixDTOs.IncomingPayment getIncomingPayment(String paymentHash) throws IOException, InterruptedException {
        return executeGet("/payments/incoming/"+paymentHash, PhoenixDTOs.IncomingPayment.class);
    }

    @Override
    public List<PhoenixDTOs.IncomingPayment> listOutgoingPayments(Instant from, Instant to, int limit, int offset, boolean includeUnpaidInvoices) throws IOException, InterruptedException {
        return executeGet("/payments/outgoing?all=" + includeUnpaidInvoices + "&from=" + from.toEpochMilli() + "&to=" + to.toEpochMilli() + "&limit=" + limit + "&offset=" + offset, new TypeReference<>() {
        });
    }

    @Override
    public PhoenixDTOs.IncomingPayment getOutgoingPaymentByHash(String paymentHash) throws IOException, InterruptedException {
        return executeGet("/payments/outgoingbyhash/"+paymentHash, PhoenixDTOs.IncomingPayment.class);
    }

    @Override
    public PhoenixDTOs.IncomingPayment getOutgoingPaymentById(String paymentId) throws IOException, InterruptedException {
        return executeGet("/payments/outgoing/"+paymentId, PhoenixDTOs.IncomingPayment.class);
    }

    @Override
    public PhoenixDTOs.CreateInvoiceResponse createBolt11Invoice(long amountSat, int expirySeconds, String description, String externalId, String webhookUrl) throws IOException, InterruptedException {
        Map<String, String> params = new HashMap<>();
        if (description != null) params.put("description", description);
        if (amountSat > 0) params.put("amountSat", String.valueOf(amountSat));
        if (expirySeconds > 0) params.put("expirySeconds", String.valueOf(expirySeconds));
        if (externalId != null) params.put("externalId", externalId);
        if (webhookUrl != null) params.put("webhookUrl", webhookUrl);

        return executePost("/createinvoice", params, PhoenixDTOs.CreateInvoiceResponse.class, MimeType.JSON);
    }

    @Override
    public String createBolt12Offer(long amountSat, String description) throws IOException, InterruptedException {
        Map<String, String> params = new HashMap<>();
        if (description != null) params.put("description", description);
        if (amountSat > 0) params.put("amountSat", String.valueOf(amountSat));

        return executePost("/createoffer", params, String.class, MimeType.PLAIN);
    }

    private <T> T executeGet(String path, Class<T> responseClass) throws IOException, InterruptedException {
        String body = executeGetRaw(path);
        if (responseClass == String.class) {
            return responseClass.cast(body);
        }
        return objectMapper.readValue(body, responseClass);
    }

    private <T> T executeGet(String path, TypeReference<T> typeReference) throws IOException, InterruptedException {
        String body = executeGetRaw(path);
        return objectMapper.readValue(body, typeReference);
    }

    private String executeGetRaw(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        ensureSuccess(response);
        return response.body();
    }

    private <T> T executePost(String path, Map<String, String> formData, Class<T> responseClass, MimeType mimeTypeResult) throws IOException, InterruptedException {
        String formBody = formData.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", mimeTypeResult.getMimeType())
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        ensureSuccess(response);

        if (responseClass == String.class) {
            return responseClass.cast(response.body());
        }

        return objectMapper.readValue(response.body(), responseClass);
    }

    public enum MimeType {
        JSON("application/json"),
        PLAIN("text/plain"),
        ;

        private final String mimeType;

        MimeType(String mimeType) {
            this.mimeType = mimeType;
        }

        public String getMimeType() {
            return mimeType;
        }
    }

    private void ensureSuccess(HttpResponse<String> response) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("PhoenixD API Fehler (HTTP " + response.statusCode() + "): " + response.body());
        }
    }
}