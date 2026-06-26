package de.verdox.cgminerapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.cgminerapi.dto.CGMinerDTO;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class CGMinerClient implements Closeable {

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

    private final ObjectMapper objectMapper;
    private final String host;
    private final int port;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Creates a new CGMiner client using the default API port (4028).
     *
     * @param host the hostname or IP address of the CGMiner instance
     */
    public CGMinerClient(ObjectMapper objectMapper, String host) {
        this(objectMapper, host, 4028);
    }

    /**
     * Creates a new CGMiner client.
     *
     * @param host the hostname or IP address of the CGMiner instance
     * @param port the TCP port of the CGMiner API
     */
    public CGMinerClient(ObjectMapper objectMapper, String host, int port) {
        this.objectMapper =         objectMapper.configure(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false
        ).setSerializationInclusion(
                JsonInclude.Include.NON_NULL
        );
        this.host = host;
        this.port = port;


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
    public JsonNode executeRaw(String command) throws IOException {
        return executeRaw(command, null);
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
    public JsonNode executeRaw(String command, String parameter)
            throws IOException {

        Map<String, Object> request = new HashMap<>();
        request.put("command", command);

        if (parameter != null && !parameter.isBlank()) {
            request.put("parameter", parameter);
        }

        String json = mapper.writeValueAsString(request);
        System.out.println("Request: " + json);

        try (Socket socket = new Socket(host, port);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.flush();

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[4096];
            int read;

            while ((read = in.read(data)) != -1) {
                buffer.write(data, 0, read);

                if (in.available() == 0) {
                    break;
                }
            }

            String response = buffer.toString(StandardCharsets.UTF_8);
            return mapper.readTree(response);
        }
    }

    @SuppressWarnings("unchecked")
    public <R extends CGMinerDTO> R execute(
            CGMinerCommand<R> command
    ) throws IOException {

        JsonNode root = executeRaw(command.command(), null);

        Class<? extends CGMinerDTO> type =
                TYPES.get(command.responseSection());

        return (R) mapper.treeToValue(root, type);
    }

    @SuppressWarnings("unchecked")
    public <R extends CGMinerDTO> R execute(
            CGMinerRequest<R> request
    ) throws IOException {

        JsonNode root =
                executeRaw(
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