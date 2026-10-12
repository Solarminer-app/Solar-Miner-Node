package de.verdox.pv_miner.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Shared LAN HTTP transport for every client that ships inside the published Core image.
 *
 * The published Core image is a GraalVM native build (bootBuildImage + GraalVM buildtools).
 * The JDK {@code java.net.http.HttpClient} is unreliable there: probes silently open no
 * socket and burn their full timeout (this broke PC-Agent LAN discovery on every deployed
 * node), while {@link HttpURLConnection} — the transport the working RestClient/RestTemplate
 * paths use — is dependable in the native image. All miner-facing HTTP must therefore go
 * through this helper instead of java.net.http.
 */
public final class LanHttp {

    public record Response(int statusCode, Map<String, List<String>> headers, String body) {

        /** Case-insensitive single header value; null when absent. */
        public String header(String name) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)
                        && !entry.getValue().isEmpty()) {
                    return entry.getValue().get(0);
                }
            }
            return null;
        }
    }

    private LanHttp() {
    }

    public static Response get(String url, int connectTimeoutMs, int readTimeoutMs, Map<String, String> headers) throws IOException {
        return exchange("GET", url, null, null, connectTimeoutMs, readTimeoutMs, headers);
    }

    public static Response post(String url, String body, String contentType, int connectTimeoutMs, int readTimeoutMs, Map<String, String> headers) throws IOException {
        return exchange("POST", url, body, contentType, connectTimeoutMs, readTimeoutMs, headers);
    }

    private static Response exchange(String method, String url, String body, String contentType,
                                     int connectTimeoutMs, int readTimeoutMs, Map<String, String> headers) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setRequestMethod(method);
            connection.setInstanceFollowRedirects(false);
            if (headers != null) {
                headers.forEach(connection::setRequestProperty);
            }
            if (body != null) {
                connection.setDoOutput(true);
                if (contentType != null) connection.setRequestProperty("Content-Type", contentType);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }

            int status = connection.getResponseCode();
            Map<String, List<String>> responseHeaders = connection.getHeaderFields();
            String responseBody = readBody(connection, status);
            return new Response(status, responseHeaders == null ? Map.of() : responseHeaders, responseBody);
        } finally {
            connection.disconnect();
        }
    }

    private static String readBody(HttpURLConnection connection, int status) throws IOException {
        try (InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
            if (stream == null) return "";
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
