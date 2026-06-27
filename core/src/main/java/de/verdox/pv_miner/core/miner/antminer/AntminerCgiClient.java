package de.verdox.pv_miner.core.miner.antminer;

import de.verdox.pv_miner.core.miner.dto.MinerDetails;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AntminerCgiClient {
    private static final Logger LOGGER = Logger.getLogger(AntminerCgiClient.class.getSimpleName());
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final Pattern REALM_PATTERN = Pattern.compile("realm=\"([^\"]+)\"");
    private static final Pattern NONCE_PATTERN = Pattern.compile("nonce=\"([^\"]+)\"");

    private String buildDigestAuthHeader(MinerDetails details, String method, String uri, String wwwAuthenticateHeader) {
        try {
            Matcher realmMatcher = REALM_PATTERN.matcher(wwwAuthenticateHeader);
            Matcher nonceMatcher = NONCE_PATTERN.matcher(wwwAuthenticateHeader);

            if (!realmMatcher.find() || !nonceMatcher.find()) {
                throw new IllegalStateException("Could not parse realm or nonce from header.");
            }

            String realm = realmMatcher.group(1);
            String nonce = nonceMatcher.group(1);

            String cnonce = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String nc = "00000001";
            String qop = "auth";

            String ha1 = md5(details.username() + ":" + realm + ":" + details.password());
            String ha2 = md5(method + ":" + uri);

            String response = md5(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2);

            return String.format(
                    "Digest username=\"%s\", realm=\"%s\", nonce=\"%s\", uri=\"%s\", response=\"%s\", qop=%s, nc=%s, cnonce=\"%s\"",
                    details.username(), realm, nonce, uri, response, qop, nc, cnonce
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to build Digest Auth header", e);
        }
    }

    private String md5(String data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private String executeWithDigestAuth(MinerDetails details, String method, String cgiEndpoint, String body) {
        try {
            String url = "http://" + details.ipv4() + cgiEndpoint;
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(url));

            if ("POST".equalsIgnoreCase(method) && body != null) {
                requestBuilder.header("Content-Type", "application/x-www-form-urlencoded");
                requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body));
            } else {
                requestBuilder.GET();
            }

            HttpRequest initialRequest = requestBuilder.build();
            HttpResponse<String> initialResponse = httpClient.send(initialRequest, HttpResponse.BodyHandlers.ofString());

            if (initialResponse.statusCode() != 401) {
                return initialResponse.body();
            }

            String authHeader = initialResponse.headers().firstValue("WWW-Authenticate")
                    .orElseThrow(() -> new IllegalStateException("No WWW-Authenticate header found"));

            String digestHeader = buildDigestAuthHeader(details, method, cgiEndpoint, authHeader);

            HttpRequest authenticatedRequest = requestBuilder
                    .setHeader("Authorization", digestHeader)
                    .build();

            HttpResponse<String> finalResponse = httpClient.send(authenticatedRequest, HttpResponse.BodyHandlers.ofString());

            if (finalResponse.statusCode() != 200) {
                LOGGER.log(Level.SEVERE, "Miner " + details.ipv4() + " rejected auth with HTTP " + finalResponse.statusCode());
                return null;
            }

            return finalResponse.body();

        } catch (HttpTimeoutException | ConnectException e) {
            return null;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "CGI communication failed with " + details.ipv4() + ": " + e.getMessage(), e);
            return null;
        }
    }

    public boolean checkIfCredentialsWork(MinerDetails details) {
        try {
            executeWithDigestAuth(details, "GET", "/cgi-bin/get_system_info.cgi", null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String executeCgiGet(MinerDetails details, String cgiEndpoint) {
        return executeWithDigestAuth(details, "GET", cgiEndpoint, null);
    }

    public String executeCgiPost(MinerDetails details, String cgiEndpoint, String formData) {
        return executeWithDigestAuth(details, "POST", cgiEndpoint, formData);
    }
}