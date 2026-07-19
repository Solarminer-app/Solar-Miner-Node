package de.verdox.pv_miner_extensions.device.mqtt;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class MqttMessageService implements AutoCloseable {
    private static final Duration MAX_AGE = Duration.ofSeconds(30);
    private final Map<ConnectionKey, Connection> connections = new ConcurrentHashMap<>();

    public String read(MqttEntity<?> entity, String topic) throws Exception {
        if (topic == null || topic.isBlank()) throw new IllegalArgumentException("MQTT topic is missing in profile entry");
        ConnectionKey key = new ConnectionKey(entity.getBrokerUri(), entity.getClientId(), entity.getUsername(), entity.getPassword());
        Connection connection = connections.computeIfAbsent(key, this::connectUnchecked);
        try {
            return connection.read(topic);
        } catch (Exception exception) {
            connections.remove(key, connection);
            connection.close();
            throw exception;
        }
    }

    public void ping(String brokerUri, String clientId, String username, String password) throws Exception {
        Connection connection = connect(new ConnectionKey(brokerUri, clientId, username, password));
        connection.close();
    }

    private Connection connectUnchecked(ConnectionKey key) {
        try { return connect(key); }
        catch (Exception exception) { throw new IllegalStateException("MQTT connection failed", exception); }
    }

    private Connection connect(ConnectionKey key) throws Exception {
        URI uri = normalizeUri(key.brokerUri());
        var builder = MqttClient.builder()
                .useMqttVersion3()
                .identifier(key.clientId() == null || key.clientId().isBlank()
                        ? "solarminer-" + Integer.toUnsignedString(key.hashCode(), 36) : key.clientId())
                .serverHost(uri.getHost())
                .serverPort(uri.getPort() > 0 ? uri.getPort() : (isTls(uri) ? 8883 : 1883));
        if (isTls(uri)) builder.sslWithDefaultConfig();
        Mqtt3AsyncClient client = builder.buildAsync();
        var connect = client.connectWith().cleanSession(false).keepAlive(30);
        if (key.username() != null && !key.username().isBlank()) {
            connect.simpleAuth().username(key.username())
                    .password((key.password() == null ? "" : key.password()).getBytes(StandardCharsets.UTF_8))
                    .applySimpleAuth();
        }
        connect.send().get(8, TimeUnit.SECONDS);
        return new Connection(client);
    }

    private URI normalizeUri(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("MQTT broker URI is required");
        return URI.create(value.contains("://") ? value : "mqtt://" + value);
    }

    private boolean isTls(URI uri) { return "mqtts".equalsIgnoreCase(uri.getScheme()) || "ssl".equalsIgnoreCase(uri.getScheme()); }

    @Override
    public void close() {
        connections.values().forEach(Connection::close);
        connections.clear();
    }

    private record ConnectionKey(String brokerUri, String clientId, String username, String password) {}
    private static final class Connection {
        private final Mqtt3AsyncClient client;
        private final Map<String, Message> latest = new ConcurrentHashMap<>();
        private final Map<String, CompletableFuture<String>> waiting = new ConcurrentHashMap<>();
        private final java.util.Set<String> subscribed = ConcurrentHashMap.newKeySet();

        private Connection(Mqtt3AsyncClient client) { this.client = client; }

        String read(String topic) throws Exception {
            Message cached = latest.get(topic);
            if (cached != null && cached.receivedAt().plus(MAX_AGE).isAfter(Instant.now())) return cached.payload();
            if (subscribed.add(topic)) {
                client.subscribeWith().topicFilter(topic).callback(publish -> {
                    String payload = new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8);
                    latest.put(topic, new Message(payload, Instant.now()));
                    CompletableFuture<String> future = waiting.remove(topic);
                    if (future != null) future.complete(payload);
                }).send().get(8, TimeUnit.SECONDS);
            }
            cached = latest.get(topic);
            if (cached != null) return cached.payload();
            CompletableFuture<String> future = new CompletableFuture<>();
            CompletableFuture<String> existing = waiting.putIfAbsent(topic, future);
            return (existing == null ? future : existing).get(8, TimeUnit.SECONDS);
        }

        void close() {
            try { client.disconnect().get(3, TimeUnit.SECONDS); } catch (Exception ignored) {}
        }
    }
    private record Message(String payload, Instant receivedAt) {}
}
