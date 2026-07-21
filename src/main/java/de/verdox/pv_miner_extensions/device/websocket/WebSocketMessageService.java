package de.verdox.pv_miner_extensions.device.websocket;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

@Service
public class WebSocketMessageService {
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public String read(WebSocketEntity<?> entity) throws Exception {
        CompletableFuture<String> firstMessage = new CompletableFuture<>();
        StringBuilder buffer = new StringBuilder();
        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override public void onOpen(WebSocket webSocket) { webSocket.request(1); }
            @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                buffer.append(data);
                if (last) firstMessage.complete(buffer.toString());
                else webSocket.request(1);
                return null;
            }
            @Override public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                firstMessage.completeExceptionally(new IllegalStateException("Binary WebSocket messages are not supported"));
                return null;
            }
            @Override public void onError(WebSocket webSocket, Throwable error) { firstMessage.completeExceptionally(error); }
        };
        var builder = httpClient.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5));
        if (entity.getApiToken() != null && !entity.getApiToken().isBlank()) {
            builder.header("Authorization", "Bearer " + entity.getApiToken());
        }
        WebSocket socket = builder.buildAsync(URI.create(entity.getUrl()), listener).get(8, TimeUnit.SECONDS);
        try { return firstMessage.get(8, TimeUnit.SECONDS); }
        finally { socket.sendClose(WebSocket.NORMAL_CLOSURE, "read complete"); }
    }

    public void ping(String url, String token) throws Exception {
        var builder = httpClient.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5));
        if (token != null && !token.isBlank()) builder.header("Authorization", "Bearer " + token);
        WebSocket socket = builder.buildAsync(URI.create(url), new WebSocket.Listener() {})
                .get(8, TimeUnit.SECONDS);
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "connection test").get(3, TimeUnit.SECONDS);
    }
}
