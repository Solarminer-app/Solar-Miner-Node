package de.verdox.currencyrates.currencyrates.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class JdkHttpTextClient implements HttpTextClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public JdkHttpTextClient() {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(), REQUEST_TIMEOUT);
    }

    JdkHttpTextClient(HttpClient httpClient) {
        this(httpClient, REQUEST_TIMEOUT);
    }

    JdkHttpTextClient(HttpClient httpClient, Duration requestTimeout) {
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public String get(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GET " + uri + " returned HTTP " + response.statusCode());
        }
        return response.body();
    }
}
