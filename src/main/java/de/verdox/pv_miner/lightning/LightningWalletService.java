package de.verdox.pv_miner.lightning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.phoenixdjava.PhoenixClient;
import de.verdox.phoenixdjava.PhoenixClientImpl;
import de.verdox.phoenixdjava.PhoenixDTOs;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LightningWalletService {
    private static final Pattern authPattern = Pattern.compile("AUTH-[0-9a-fA-F]{8}");
    private static final Pattern claimPattern = Pattern.compile("CLAIM-[0-9a-fA-F]{12}");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String RECOMMENDED_FEES_URL = "https://mempool.space/api/v1/fees/recommended";

    private final PhoenixClient phoenixClient;
    private final String backendUrl;
    private String cachedLightningAddress;
    private String cachedBolt12;
    private final boolean isDebug;

    @Getter
    private final String seedWords;
    @Getter
    private final String webhookSecret;

    public LightningWalletService(
            Environment environment,
            @Value("${solarmining.phoenix.url}") String url,
            @Value("${solarmining.backend.url.rest}") String backendUrl,
            @Value("${solarmining.phoenix.config}") Path configPath,
            @Value("${solarmining.phoenix.seed}") Path seedPath
    ) {
        this.backendUrl = backendUrl;
        this.isDebug = environment.matchesProfiles("dev");

        String parsedPassword = null;
        String parsedWebhook = null;
        String parsedSeed = null;

        if (Files.exists(configPath)) {
            try {
                List<String> lines = Files.readAllLines(configPath, StandardCharsets.UTF_8);
                for (String line : lines) {
                    if (line.startsWith("http-password=")) {
                        parsedPassword = line.substring("http-password=".length()).trim();
                    } else if (line.startsWith("webhook-secret=")) {
                        parsedWebhook = line.substring("webhook-secret=".length()).trim();
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read phoenix.conf file at " + configPath.toAbsolutePath(), e);
            }
        } else {
            throw new IllegalStateException("phoenix.conf not found at " + configPath.toAbsolutePath());
        }

        if (parsedPassword == null || parsedPassword.isBlank()) {
            throw new IllegalStateException("No http-password found in phoenix.conf! PhoenixD requires a password to operate.");
        }

        if (Files.exists(seedPath)) {
            try {
                parsedSeed = Files.readString(seedPath, StandardCharsets.UTF_8).trim();
            } catch (IOException e) {
                System.err.println("Warning: Failed to read seed.dat at " + seedPath.toAbsolutePath() + " - " + e.getMessage());
            }
        } else {
            System.err.println("Warning: seed.dat not found at " + seedPath.toAbsolutePath());
        }

        this.webhookSecret = parsedWebhook;
        this.seedWords = parsedSeed;
        this.phoenixClient = new PhoenixClientImpl(url, parsedPassword);
    }

    @Scheduled(initialDelay = 24 * 60 * 60 * 1000, fixedRate = 24 * 60 * 60 * 1000)
    public void dailyDnsHeartbeat() {
        claimFreeLightningAddress();
    }

    public PhoenixDTOs.NodeInfo getNodeInfo() {
        try {
            return phoenixClient.getInfo();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }

    public long getBalanceSat() {
        try {
            return phoenixClient.getBalance().balanceSat();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return -1;
        }
    }


    public long getFreeCreditSat() {
        try {
            return phoenixClient.getBalance().feeCreditSat();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public String getLightningAddress() {
        try {
            return phoenixClient.getLightningAddress();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return "not available";
        }
    }

    public String getBolt12() {
        if (cachedBolt12 != null) {
            return cachedBolt12;
        }
        try {
            cachedBolt12 = phoenixClient.createBolt12Offer(0, "Solarminer.app - Mine bitcoin with solar power!");
            return cachedBolt12;
        } catch (IOException | InterruptedException e) {
            return "not available";
        }
    }

    public String claimFreeLightningAddress() {
        if (cachedLightningAddress != null) {
            return cachedLightningAddress;
        }
        try {
            var nodeInfo = getNodeInfo();
            String nodeId = nodeInfo.nodeId();
            String bolt12Offer = phoenixClient.createBolt12Offer(0, "Claim Solarminer.app lightning address");

            if (nodeId == null || "not available".equals(bolt12Offer)) {
                System.err.println("Could not load phoenix data");
                return null;
            }

            String targetMemo = sha256(bolt12Offer);
            var authInvoiceTransaction = phoenixClient.createBolt11Invoice(1, 3600, "CLAIM-" + targetMemo, null, null);
            String bolt11AuthInvoice = authInvoiceTransaction.serialized();

            var requestBody = Map.of(
                    "bolt12Offer", bolt12Offer,
                    "authInvoice", bolt11AuthInvoice
            );

            RestClient restClient;
            if (isDebug) {
                TrustManager[] trustAllCerts = new TrustManager[]{
                        new X509TrustManager() {
                            public X509Certificate[] getAcceptedIssuers() {
                                return null;
                            }

                            public void checkClientTrusted(X509Certificate[] certs, String authType) {
                            }

                            public void checkServerTrusted(X509Certificate[] certs, String authType) {
                            }
                        }
                };

                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

                HttpClient httpClient = HttpClient.newBuilder()
                        .sslContext(sslContext)
                        .build();
                restClient = RestClient.builder()
                        .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                        .build();
            } else {
                restClient = RestClient.create();
            }

            SolarMiningWebSocketClient.ClaimResponse response = restClient.post()
                    .uri(backendUrl + "/api/v1/addresses/claim-free")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(SolarMiningWebSocketClient.ClaimResponse.class);

            if (response != null) {
                cachedLightningAddress = response.lightningAddress();
                return cachedLightningAddress;
            }

        } catch (Exception e) {
            System.err.println("Could not fetch lightning address: " + e.getMessage());
            e.printStackTrace();
        }
        return "not available";
    }

    public List<LightningTransaction> getTransactions() {
        List<LightningTransaction> allTransactions = new ArrayList<>();
        try {
            List<PhoenixDTOs.IncomingPayment> incoming = phoenixClient.listIncomingPayments(true);
            for (PhoenixDTOs.IncomingPayment payment : incoming) {
                LightningTransaction.Status status = LightningTransaction.Status.PENDING;
                if (payment.isPaid()) status = LightningTransaction.Status.SETTLED;
                else if (payment.isExpired()) status = LightningTransaction.Status.EXPIRED;

                LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(payment.createdAt()), ZoneId.systemDefault());

                if (payment.description() != null) {
                    Matcher authMatcher = authPattern.matcher(payment.description());
                    if (authMatcher.find()) {
                        continue;
                    }
                    Matcher claimMatcher = claimPattern.matcher(payment.description());
                    if (claimMatcher.find()) {
                        continue;
                    }
                }

                allTransactions.add(new LightningTransaction(
                        payment.paymentHash(),
                        payment.invoice(),
                        payment.isPaid() && payment.receivedSat() != null ? payment.receivedSat() : payment.amountSat(),
                        payment.description() != null ? payment.description() : "Incoming Transfer",
                        status,
                        LightningTransaction.Type.INCOMING,
                        dateTime
                ));
            }

            List<PhoenixDTOs.IncomingPayment> outgoing = phoenixClient.listOutgoingPayments(true);
            for (PhoenixDTOs.IncomingPayment payment : outgoing) {
                LightningTransaction.Status status = LightningTransaction.Status.PENDING;
                if (payment.isPaid()) status = LightningTransaction.Status.SETTLED;
                else if (payment.isExpired()) status = LightningTransaction.Status.EXPIRED;

                LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(payment.createdAt()), ZoneId.systemDefault());

                allTransactions.add(new LightningTransaction(
                        payment.paymentHash(),
                        payment.invoice(),
                        payment.amountSat(),
                        payment.description() != null ? payment.description() : "Outgoing Payment",
                        status,
                        LightningTransaction.Type.OUTGOING,
                        dateTime
                ));
            }

            allTransactions.sort(Comparator.comparing(LightningTransaction::timestamp).reversed());

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return allTransactions;
    }

    public LightningTransaction createInvoice(long amountSat, String memo) {
        return createInvoice(amountSat, memo, 3600);
    }

    public LightningTransaction createInvoice(long amountSat, String memo, int timeOut) {
        try {
            PhoenixDTOs.CreateInvoiceResponse response = phoenixClient.createBolt11Invoice(amountSat, timeOut, memo, null, null);
            return new LightningTransaction(
                    response.paymentHash(),
                    response.serialized(),
                    amountSat,
                    memo,
                    LightningTransaction.Status.PENDING,
                    LightningTransaction.Type.INCOMING,
                    LocalDateTime.now()
            );
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String sendOnChainPayment(long amountSat, String address, long feeRateSatByte) {
        if (address == null || address.isBlank() || amountSat <= 0 || feeRateSatByte <= 0) {
            return null;
        }
        try {
            return phoenixClient.payOnChain(amountSat, address.trim(), feeRateSatByte);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Status of an on-chain transaction, resolved via the mempool.space API:
     * the txid is looked up at {@code /api/v1/tx/{txid}}. A 404 means the tx is
     * not indexed (unbroadcast / not in mempool yet) and is reported as pending.
     */
    public record OnChainTxStatus(boolean found, boolean confirmed, Integer blockHeight, Integer confirmations) {
    }

    public OnChainTxStatus getOnChainTransactionStatus(String txId) {
        if (txId == null || txId.isBlank()) {
            return new OnChainTxStatus(false, false, null, null);
        }
        String clean = txId.trim();
        try {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(java.net.URI.create("https://mempool.space/api/v1/tx/" + clean))
                            .timeout(java.time.Duration.ofSeconds(10))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                // Not yet in the mempool index -> still pending from the user's point of view.
                return new OnChainTxStatus(false, false, null, null);
            }
            if (response.statusCode() != 200) {
                System.err.println("Failed to fetch on-chain tx " + clean + ", HTTP " + response.statusCode());
                return new OnChainTxStatus(false, false, null, null);
            }

            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            JsonNode status = root.path("status");
            boolean confirmed = status.path("confirmed").asBoolean(false);
            Integer blockHeight = status.hasNonNull("block_height") ? status.path("block_height").asInt() : null;

            Integer confirmations = null;
            if (confirmed && blockHeight != null) {
                confirmations = tipHeight(httpClient) - blockHeight + 1;
                if (confirmations < 0) {
                    confirmations = 1;
                }
            }
            return new OnChainTxStatus(true, confirmed, blockHeight, confirmations);
        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to fetch on-chain tx " + clean + ": " + e.getMessage());
            return new OnChainTxStatus(false, false, null, null);
        }
    }

    /**
     * On-chain sends the phoenixd node has performed (splice / sendtoaddress).
     * These surface in {@code /payments/outgoing} carrying a {@code txId}; a
     * confirmation count is resolved live from mempool.space.
     */
    public record OnChainWithdrawal(String txId, long amountSat, String memo, long createdAt,
                                    boolean confirmed, Integer blockHeight, Integer confirmations) {
    }

    public List<OnChainWithdrawal> getOnChainWithdrawals() {
        List<OnChainWithdrawal> result = new ArrayList<>();
        try {
            List<PhoenixDTOs.IncomingPayment> outgoing = phoenixClient.listOutgoingPayments(true);
            for (PhoenixDTOs.IncomingPayment payment : outgoing) {
                if (payment.txId() == null || payment.txId().isBlank()) {
                    continue; // lightning payment, no on-chain tx
                }
                OnChainTxStatus status = getOnChainTransactionStatus(payment.txId());
                long amount = payment.amountSat() > 0 ? payment.amountSat()
                        : (payment.sent() != null ? payment.sent() : 0);
                String memo = payment.description() != null ? payment.description() : "On-chain withdrawal";
                result.add(new OnChainWithdrawal(
                        payment.txId(),
                        amount,
                        memo,
                        payment.createdAt(),
                        status.confirmed(),
                        status.blockHeight(),
                        status.confirmations()
                ));
            }
            result.sort(Comparator.comparingLong(OnChainWithdrawal::createdAt).reversed());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return result;
    }

    private int tipHeight(HttpClient httpClient) {
        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(java.net.URI.create("https://mempool.space/api/v1/blocks/tip/height"))
                            .timeout(java.time.Duration.ofSeconds(10))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return Integer.parseInt(response.body().trim());
            }
        } catch (IOException | InterruptedException | NumberFormatException e) {
            System.err.println("Failed to fetch current tip height: " + e.getMessage());
        }
        return -1;
    }

    public record OnChainFeeTier(long satPerVByte, Integer fastBlocks) {
    }

    public record OnChainFeeEstimate(long floor, long fast, long fastest, OnChainFeeTier floorTier, OnChainFeeTier fastTier, OnChainFeeTier fastestTier, Boolean reliable) {
    }

    public OnChainFeeEstimate getRecommendedOnChainFees() {
        try {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(java.net.URI.create(RECOMMENDED_FEES_URL))
                            .timeout(java.time.Duration.ofSeconds(10))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("Failed to fetch recommended fees from mempool.space, HTTP " + response.statusCode());
                return null;
            }

            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            long floor = root.path("hourFee").asLong();
            long fast = root.path("halfHourFee").asLong();
            long fastest = root.path("fastestFee").asLong();
            // mempool.space naming maps to confirmation speed: fastestFee ~ 1 block (~10 min),
            // halfHourFee ~ 2 blocks (~30 min), hourFee ~ 12 blocks (~1 h).
            OnChainFeeTier floorTier = new OnChainFeeTier(floor, 12);
            OnChainFeeTier fastTier = new OnChainFeeTier(fast, 2);
            OnChainFeeTier fastestTier = new OnChainFeeTier(fastest, 1);
            boolean reliable = floor > 0 && fast > 0 && fastest > 0;
            if (!reliable) {
                System.err.println("Recommended fees from mempool.space look incomplete: " + response.body());
                return null;
            }
            return new OnChainFeeEstimate(floor, fast, fastest, floorTier, fastTier, fastestTier, true);
        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to fetch recommended fees from mempool.space: " + e.getMessage());
            return null;
        }
    }

    public boolean sendPayment(String target, long amount) {
        if (target == null || target.isBlank()) return false;
        try {
            String cleanTarget = target.trim();
            PhoenixDTOs.PayResponse response;

            if (cleanTarget.startsWith("lnbc")) {
                response = phoenixClient.payBolt11Invoice(cleanTarget, amount);
            } else if (cleanTarget.toLowerCase().startsWith("lno")) {
                response = phoenixClient.payBolt12Offer(cleanTarget, amount, "Paid via Miner WebUI");
            } else if (cleanTarget.contains("@")) {
                response = phoenixClient.payLightningAddress(cleanTarget, amount, "Paid via Miner WebUI");
            } else {
                return false;
            }

            return response != null && response.paymentId() != null;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean sendPayment(String target) {
        if (target == null || target.isBlank()) return false;
        try {
            String cleanTarget = target.trim();
            PhoenixDTOs.PayResponse response;

            if (cleanTarget.startsWith("lnbc")) {
                response = phoenixClient.payBolt11InvoiceAll(cleanTarget);
            } else if (cleanTarget.toLowerCase().startsWith("lno")) {
                response = phoenixClient.payBolt12OfferAll(cleanTarget, "Paid via Miner WebUI");
            } else if (cleanTarget.contains("@")) {
                response = phoenixClient.payLightningAddressAll(cleanTarget, "Paid via Miner WebUI");
            } else {
                return false;
            }

            return response != null && response.paymentId() != null;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static String sha256(String bolt12Offer) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bolt12Offer.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.substring(0, 12);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}