package de.verdox.pv_miner.core.miner.agent;

import com.sun.net.httpserver.HttpServer;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MinerAgentControllerConfigurationTest {
    @Test
    void configuresMoneroThroughStandaloneProxyWithoutExternalProxyDiscovery() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> configuration = new AtomicReference<>();
        AtomicReference<String> referral = new AtomicReference<>();
        server.createContext("/api/agent/proxy", exchange -> {
            assertEquals("GET", exchange.getRequestMethod());
            respond(exchange, "{\"mode\":\"standalone\",\"moneroUrl\":\"stratum+tcp://127.0.0.1:3335\"}");
        });
        server.createContext("/api/agent/referral", exchange -> {
            referral.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, "true");
        });
        server.createContext("/api/agent/monero/configuration", exchange -> {
            configuration.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, "true");
        });
        server.start();
        try {
            MinerDetails details = new MinerDetails(UUID.randomUUID(), "127.0.0.1", server.getAddress().getPort(), "", "");
            boolean accepted = new MinerAgentController().configureMonero(details,
                    "stratum+tcp://xmr.example:3333", null, "wallet", "cpu-12345678", "friend");
            assertTrue(accepted);
            assertTrue(configuration.get().contains("\"poolUrl\":\"stratum+tcp://xmr.example:3333\""));
            assertTrue(configuration.get().contains("\"worker\":\"cpu-12345678\""));
            assertEquals("key=friend", referral.get());
        } finally {
            server.stop(0);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
