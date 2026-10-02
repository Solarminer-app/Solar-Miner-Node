package de.verdox.pv_miner.discovery;

import com.sun.net.httpserver.HttpServer;
import de.verdox.pv_miner.configfetcher.ConfigFetcherService;
import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.solarminer.rest.*;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DiscoveryRestProbeTest {
    @Test
    void readsOnceAndRequiresMatchingPayloadWithoutSendingPostRequests() throws Exception {
        var calls = new AtomicInteger();
        var status = new AtomicInteger(200);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/status", exchange -> {
            calls.incrementAndGet();
            byte[] body = "{\"power\":42}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var service = new DiscoveryService(mock(MinerApiClient.class), mock(ConfigFetcherService.class));
            var found = new ArrayList<DiscoveryService.DiscoveredRestDevice>();
            int port = server.getAddress().getPort();
            String url = "http://127.0.0.1:" + port;
            var entry = new RestPVConfig.Entry<>("/status", RestHttpMethod.GET, RestResponseType.JSON,
                    "power", 1, "x", RestParameterType.DOUBLE);
            assertTrue(service.executeRestProbe(url, entry, "127.0.0.1", port, "real", found::add));
            assertEquals(1, calls.get());
            assertEquals(1, found.size());
            var wrong = new RestPVConfig.Entry<>("/status", RestHttpMethod.GET, RestResponseType.JSON,
                    "missing", 1, "x", RestParameterType.DOUBLE);
            assertFalse(service.executeRestProbe(url, wrong, "127.0.0.1", port, "wrong", found::add));
            status.set(401);
            assertFalse(service.executeRestProbe(url, entry, "127.0.0.1", port, "protected", found::add));
            status.set(403);
            assertFalse(service.executeRestProbe(url, entry, "127.0.0.1", port, "protected", found::add));
            int callsBeforePost = calls.get();
            var post = new RestPVConfig.Entry<>("/status", RestHttpMethod.POST, RestResponseType.JSON,
                    "power", 1, "x", RestParameterType.DOUBLE);
            assertFalse(service.executeRestProbe(url, post, "127.0.0.1", port, "write", found::add));
            assertEquals(callsBeforePost, calls.get());
            assertEquals(1, found.size());
        } finally { server.stop(0); }
    }
}
