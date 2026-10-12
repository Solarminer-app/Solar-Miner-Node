package de.verdox.pv_miner.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import de.verdox.pv_miner.core.miner.MiningOS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The published Core image is a GraalVM native build where java.net.http.HttpClient is
 * unreliable; discovery must find a PC-Agent over the transport it actually ships on.
 * These tests run against a real loopback HTTP server so the transport itself is covered.
 */
class MinerDiscoveryServiceTest {
    private HttpServer server;
    private MinerDiscoveryService service;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        service = new MinerDiscoveryService(new ObjectMapper());
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    private void respond(int status, String body) {
        server.createContext("/api/agent/external/identity", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    private MinerDiscoveryService.DetectedMiner probe() {
        // The production path probes the fixed agent port 8084; the package-private seam
        // lets this test point the probe at the loopback server instead.
        return service.identifyMinerDetails("127.0.0.1", port);
    }

    @Test
    void findsPcAgentAndPrefersOperatorLabel() {
        respond(200, "{\"kind\":\"solarminer-pc-agent\",\"powerControlProtocolVersion\":1,\"model\":\"SolarMiner PC Agent\",\"name\":\"Ripping-PC\"}");
        MinerDiscoveryService.DetectedMiner detected = probe();
        assertNotNull(detected);
        assertEquals(MiningOS.AGENT, detected.os());
        assertEquals("Ripping-PC", detected.model());
    }

    @Test
    void fallsBackToProductModelWithoutLabel() {
        respond(200, "{\"kind\":\"solarminer-pc-agent\",\"powerControlProtocolVersion\":1,\"model\":\"SolarMiner PC Agent\",\"name\":null}");
        MinerDiscoveryService.DetectedMiner detected = probe();
        assertNotNull(detected);
        assertEquals("SolarMiner PC Agent", detected.model());
    }

    @Test
    void rejectsForeignIdentityKind() {
        respond(200, "{\"kind\":\"something-else\"}");
        assertNull(probe());
    }

    @Test
    void rejectsNon200Responses() {
        respond(403, "{\"message\":\"Externe Node-Steuerung wurde lokal deaktiviert\"}");
        assertNull(probe());
    }
}
