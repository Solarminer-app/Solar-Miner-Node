package de.verdox.cgminerapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CGMinerClientTest {

    @Test
    void readsDelayedChunksUntilEof() throws Exception {
        try (FakeCgMiner server = FakeCgMiner.start(socket -> {
            readRequest(socket);
            socket.getOutputStream().write("{\"STATUS\":".getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            sleep(100);
            socket.getOutputStream().write("[{\"STATUS\":\"S\"}]}".getBytes(StandardCharsets.UTF_8));
        })) {
            JsonNode response = client(1_000).executeRaw("127.0.0.1", server.port(), "summary");

            assertEquals("S", response.path("STATUS").path(0).path("STATUS").asText());
            server.assertCompleted();
        }
    }

    @Test
    void stopsAtNulTerminatorWithoutWaitingForConnectionClose() throws Exception {
        try (FakeCgMiner server = FakeCgMiner.start(socket -> {
            readRequest(socket);
            socket.getOutputStream().write("{\"STATUS\":[]}\0ignored".getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            sleep(500);
        })) {
            JsonNode response = client(150).executeRaw("127.0.0.1", server.port(), "summary");

            assertTrue(response.path("STATUS").isArray());
            server.assertCompleted();
        }
    }

    @Test
    void readTimeoutIsReportedInsteadOfReturningPartialResponse() throws Exception {
        try (FakeCgMiner server = FakeCgMiner.start(socket -> {
            readRequest(socket);
            socket.getOutputStream().write("{\"STATUS\":".getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            sleep(500);
        })) {
            assertThrows(SocketTimeoutException.class,
                    () -> client(100).executeRaw("127.0.0.1", server.port(), "summary"));
            server.assertCompleted();
        }
    }

    @Test
    void malformedEofResponseFailsParsing() throws Exception {
        try (FakeCgMiner server = FakeCgMiner.start(socket -> {
            readRequest(socket);
            socket.getOutputStream().write("{\"STATUS\":".getBytes(StandardCharsets.UTF_8));
        })) {
            assertThrows(IOException.class,
                    () -> client(1_000).executeRaw("127.0.0.1", server.port(), "summary"));
            server.assertCompleted();
        }
    }

    @Test
    void doesNotMutateCallerMapperConfiguration() {
        ObjectMapper mapper = new ObjectMapper();
        JsonInclude.Value originalInclusion =
                mapper.getSerializationConfig().getDefaultPropertyInclusion();
        assertTrue(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));

        new CGMinerClient(mapper);

        assertTrue(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        assertEquals(originalInclusion,
                mapper.getSerializationConfig().getDefaultPropertyInclusion());
    }

    private static CGMinerClient client(int readTimeoutMillis) {
        return new CGMinerClient(new ObjectMapper(), 1_000, readTimeoutMillis);
    }

    private static void readRequest(Socket socket) throws IOException {
        byte[] request = new byte[256];
        assertTrue(socket.getInputStream().read(request) > 0);
    }

    private static void sleep(long millis) throws IOException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Fake server interrupted", exception);
        }
    }

    @FunctionalInterface
    private interface SocketHandler {
        void handle(Socket socket) throws Exception;
    }

    private static final class FakeCgMiner implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final CompletableFuture<Void> completed;

        private FakeCgMiner(ServerSocket serverSocket, CompletableFuture<Void> completed) {
            this.serverSocket = serverSocket;
            this.completed = completed;
        }

        static FakeCgMiner start(SocketHandler handler) throws IOException {
            ServerSocket serverSocket = new ServerSocket(0, 1);
            CompletableFuture<Void> completed = CompletableFuture.runAsync(() -> {
                try (Socket socket = serverSocket.accept()) {
                    handler.handle(socket);
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
            return new FakeCgMiner(serverSocket, completed);
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void assertCompleted() throws Exception {
            completed.get(2, TimeUnit.SECONDS);
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            assertCompleted();
        }
    }
}
