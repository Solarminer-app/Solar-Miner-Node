package de.verdox.pv_miner.core.miner.agent;

import com.sun.net.httpserver.HttpServer;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MinerAgentControllerConfigurationTest {
    @Test
    void preservesAgentShareCountersWhenReadingWorkerTelemetry() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/agent/external/status", exchange -> respond(exchange, """
                {"minerIdentity":{"minerUID":"agent","macAddress":"","minerModel":"PC"},"name":"agent","miningStatus":"MINING","powerTargetWatts":120,"minPowerTarget":0,"defaultPowerTarget":120,"maxPowerTarget":250,"approximatedPowerUsageWatts":120,"terahashPerSecond":0.000001,"temperatureCelsius":61,"pools":[],"workers":[{"miningStatus":"MINING","workerDisplayName":"GPU 0","currentAlgorithm":"KAWPOW","terahashPerSecond":0.000001,"temperatureCelsius":61,"powerTargetWatts":120,"minPowerTarget":0,"defaultPowerTarget":120,"maxPowerTarget":250,"approximatedPowerUsageWatts":120,"pools":[],"hardwareType":"GPU","hardwareModel":"Test","deviceId":"gpu-0","acceptedShares":42,"rejectedShares":3}]}
                """));
        server.createContext("/api/agent/external/power-control", exchange ->
                respond(exchange, "{\"externalControlEnabled\":true,\"dynamicPowerScalingEnabled\":false,\"currentTargetWatts\":120,\"minPowerWatts\":0,\"defaultPowerWatts\":120,\"maxPowerWatts\":250}"));
        server.createContext("/api/agent/external/telemetry", exchange -> respond(exchange, "{\"metrics\":{}}"));
        server.start();
        try {
            MinerDetails details = new MinerDetails(UUID.randomUUID(), "127.0.0.1", server.getAddress().getPort(), "", "");
            MinerStats stats = new MinerAgentController().queryStats("agent", details);

            assertNotNull(stats);
            assertEquals(1, stats.workers().size());
            assertEquals(42L, stats.workers().getFirst().acceptedShares());
            assertEquals(3L, stats.workers().getFirst().rejectedShares());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void configuresMoneroThroughStandaloneProxyWithoutExternalProxyDiscovery() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> configuration = new AtomicReference<>();
        AtomicReference<String> referral = new AtomicReference<>();
        server.createContext("/api/agent/external/proxy", exchange -> {
            assertEquals("GET", exchange.getRequestMethod());
            respond(exchange, "{\"mode\":\"standalone\",\"moneroUrl\":\"stratum+tcp://127.0.0.1:3335\"}");
        });
        server.createContext("/api/agent/external/referral", exchange -> {
            referral.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, "true");
        });
        server.createContext("/api/agent/external/monero/configuration", exchange -> {
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
