package de.verdox.pv_miner.core.miner.antminer;

import de.verdox.pv_miner.core.miner.dto.MinerDetails;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AntminerCgiClient {

    // Ein einziger, wiederverwendbarer Client für alle Miner (sehr effizient)
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // Regex zum Auslesen der Digest-Parameter vom Antminer
    private static final Pattern REALM_PATTERN = Pattern.compile("realm=\"([^\"]+)\"");
    private static final Pattern NONCE_PATTERN = Pattern.compile("nonce=\"([^\"]+)\"");

    /*
     * ============================================================
     * Digest Authentication Core Logic
     * ============================================================
     */

    private String buildDigestAuthHeader(MinerDetails details, String method, String uri, String wwwAuthenticateHeader) {
        try {
            Matcher realmMatcher = REALM_PATTERN.matcher(wwwAuthenticateHeader);
            Matcher nonceMatcher = NONCE_PATTERN.matcher(wwwAuthenticateHeader);

            if (!realmMatcher.find() || !nonceMatcher.find()) {
                throw new IllegalStateException("Could not parse realm or nonce from header.");
            }

            String realm = realmMatcher.group(1);
            String nonce = nonceMatcher.group(1);

            // Client Nonce (zufällig) und Counter (immer 1 für Einzelrequests)
            String cnonce = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String nc = "00000001";
            String qop = "auth";

            // Hash 1: MD5(username:realm:password)
            String ha1 = md5(details.username() + ":" + realm + ":" + details.password());

            // Hash 2: MD5(method:uri)
            String ha2 = md5(method + ":" + uri);

            // Response: MD5(HA1:nonce:nc:cnonce:qop:HA2)
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
        MessageDigest md = MessageDigest.getInstance("MD5"); // GraalVM unterstützt das nativ
        byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /*
     * ============================================================
     * Request Execution with Automatic Handshake
     * ============================================================
     */

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

            // 1. Erster Versuch (wird 401 liefern)
            HttpRequest initialRequest = requestBuilder.build();
            HttpResponse<String> initialResponse = httpClient.send(initialRequest, HttpResponse.BodyHandlers.ofString());

            if (initialResponse.statusCode() != 401) {
                return initialResponse.body(); // Falls der Miner ausnahmsweise kein Auth braucht
            }

            // 2. WWW-Authenticate Header auslesen
            String authHeader = initialResponse.headers().firstValue("WWW-Authenticate")
                    .orElseThrow(() -> new IllegalStateException("No WWW-Authenticate header found on 401 response"));

            // 3. Hash berechnen und Header bauen
            String digestHeader = buildDigestAuthHeader(details, method, cgiEndpoint, authHeader);

            // 4. Zweiten Request mit gültigem Hash senden
            HttpRequest authenticatedRequest = requestBuilder
                    .setHeader("Authorization", digestHeader)
                    .build();

            HttpResponse<String> finalResponse = httpClient.send(authenticatedRequest, HttpResponse.BodyHandlers.ofString());

            if (finalResponse.statusCode() != 200) {
                throw new IllegalStateException("CGI Request failed with HTTP " + finalResponse.statusCode());
            }

            return finalResponse.body();

        } catch (Exception e) {
            throw new RuntimeException("CGI " + method + " query failed: " + cgiEndpoint, e);
        }
    }

    /*
     * ============================================================
     * Public API
     * ============================================================
     */

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