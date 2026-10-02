package de.verdox.cgminerapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.cgminerapi.dto.CGMinerDTO;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public class CGMinerClient implements Closeable {

    private static final Logger LOGGER = Logger.getLogger(CGMinerClient.class.getName());
    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 10_000;

    public static final Map<ResponseSection,
            Class<? extends CGMinerDTO>> TYPES =
            Map.of(
                    ResponseSection.VERSION,
                    CGMinerDTO.Version.class,

                    ResponseSection.CONFIG,
                    CGMinerDTO.Config.class,

                    ResponseSection.SUMMARY,
                    CGMinerDTO.Summary.class,

                    ResponseSection.POOLS,
                    CGMinerDTO.Pools.class,

                    ResponseSection.DEVS,
                    CGMinerDTO.Devs.class,

                    ResponseSection.NOTIFY,
                    CGMinerDTO.Notify.class,

                    ResponseSection.STATS,
                    CGMinerDTO.Stats.class,

                    ResponseSection.STATUS,
                    CGMinerDTO.Status.class
            );

    private final ObjectMapper mapper;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    /**
     * Creates a new CGMiner client.
     */
    public CGMinerClient(ObjectMapper objectMapper) {
        this(objectMapper, DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_READ_TIMEOUT_MILLIS);
    }

    /**
     * Creates a client with explicit transport timeouts.
     *
     * <p>The supplied mapper is copied so that CGMiner-specific settings do not
     * leak into other application JSON handling.</p>
     */
    public CGMinerClient(ObjectMapper objectMapper, int connectTimeoutMillis, int readTimeoutMillis) {
        if (connectTimeoutMillis <= 0) {
            throw new IllegalArgumentException("connectTimeoutMillis must be positive");
        }
        if (readTimeoutMillis <= 0) {
            throw new IllegalArgumentException("readTimeoutMillis must be positive");
        }

        this.mapper = Objects.requireNonNull(objectMapper, "objectMapper").copy().configure(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false
        ).setSerializationInclusion(
                JsonInclude.Include.NON_NULL
        );
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    /**
     * Executes a CGMiner API command without parameters.
     *
     * <p>Examples of commands are {@code summary}, {@code devs}, and
     * {@code pools}.</p>
     *
     * @param command the command to execute
     * @return the parsed JSON response returned by CGMiner
     * @throws IOException if the request cannot be sent or the response
     *                     cannot be read
     */
    public JsonNode executeRaw(String host, int port, String command) throws IOException {
        return executeRaw(host, port, command, null);
    }

    /**
     * Executes a CGMiner API command with an optional parameter.
     *
     * <p>Example:</p>
     *
     * <pre>{@code
     * execute("ascset", "0,enabled");
     * }</pre>
     *
     * @param command   the command to execute
     * @param parameter an optional command parameter, may be {@code null}
     * @return the parsed JSON response returned by CGMiner
     * @throws IOException if the request cannot be sent or the response
     *                     cannot be read
     */
    public JsonNode executeRaw(String host, int port, String command, String parameter)
            throws IOException {

        Map<String, Object> request = new HashMap<>();
        request.put("command", command);

        if (parameter != null && !parameter.isBlank()) {
            request.put("parameter", parameter);
        }

        String json = mapper.writeValueAsString(request);
        LOGGER.fine(() -> "Sending CGMiner API request; parameter present: " + request.containsKey("parameter"));

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
            socket.setSoTimeout(readTimeoutMillis);

            try (OutputStream out = socket.getOutputStream();
                 InputStream in = socket.getInputStream()) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
                out.flush();

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] data = new byte[4096];
                int read;
                boolean complete = false;

                while (!complete && (read = in.read(data)) != -1) {
                    int frameLength = read;
                    for (int index = 0; index < read; index++) {
                        if (data[index] == 0) {
                            frameLength = index;
                            complete = true;
                            break;
                        }
                    }
                    buffer.write(data, 0, frameLength);
                }

                String response = buffer.toString(StandardCharsets.UTF_8);
                return mapper.readTree(response);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <R extends CGMinerDTO> R execute(
            String host, int port,
            CGMinerCommand<R> command
    ) throws IOException {

        JsonNode root = executeRaw(host, port, command.command(), null);

        Class<? extends CGMinerDTO> type =
                TYPES.get(command.responseSection());

        return (R) mapper.treeToValue(root, type);
    }

    @SuppressWarnings("unchecked")
    public <R extends CGMinerDTO> R execute(
            String host, int port,
            CGMinerRequest<R> request
    ) throws IOException {

        JsonNode root =
                executeRaw(
                        host, port,
                        request.command().command(),
                        request.parameter()
                );

        Class<? extends CGMinerDTO> type =
                TYPES.get(request.command().responseSection());

        return (R) mapper.treeToValue(root, type);
    }

    @Override
    public void close() {
        // No persistent connection is maintained.
    }
}
