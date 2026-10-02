package de.verdox.currencyrates.currencyrates.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdkHttpTextClientTest {
    @Test
    void rejectsNonSuccessHttpResponses() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(503);
        when(httpClient.send(
                any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()
        )).thenReturn(response);
        JdkHttpTextClient client = new JdkHttpTextClient(httpClient, Duration.ofSeconds(1));

        assertThrows(IOException.class, () -> client.get(URI.create("https://provider.test/unavailable")));
    }

    @Test
    void preservesRequestTimeoutFailures() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(), any())).thenThrow(new HttpTimeoutException("timed out"));
        JdkHttpTextClient client = new JdkHttpTextClient(httpClient, Duration.ofMillis(25));

        assertThrows(HttpTimeoutException.class, () -> client.get(URI.create("https://provider.test/slow")));
    }
}
