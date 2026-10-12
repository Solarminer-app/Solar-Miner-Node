package de.verdox.pv_miner.core.miner.braiins.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.verdox.pv_miner.core.util.LanHttp;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public final class GraphQLExecutor {
    private static final int CONNECT_TIMEOUT_MS = (int) Duration.ofSeconds(10).toMillis();
    private static final int REQUEST_TIMEOUT_MS = (int) Duration.ofSeconds(15).toMillis();

    private final ObjectMapper mapper = new ObjectMapper();

    public LanHttp.Response executeRaw(String host, String query, Map<String, Object> variables, String sessionCookie) throws IOException {
        int port = 80;
        String endpoint = "http://" + host + ":" + port + "/graphql";
        ObjectNode body = mapper.createObjectNode();

        body.put("query", query);

        if (variables != null) {
            body.set("variables", mapper.valueToTree(variables));
        }

        Map<String, String> headers = new HashMap<>();
        if (sessionCookie != null && !sessionCookie.isBlank()) {
            headers.put("Cookie", sessionCookie);
        }

        return LanHttp.post(endpoint, mapper.writeValueAsString(body), "application/json",
                CONNECT_TIMEOUT_MS, REQUEST_TIMEOUT_MS, headers);
    }

    public JsonNode execute(String host, String query, Map<String, Object> variables, String sessionCookie) throws IOException {
        LanHttp.Response response = executeRaw(host, query, variables, sessionCookie);
        return mapper.readTree(response.body());
    }
}
