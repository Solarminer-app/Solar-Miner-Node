package de.verdox.pv_miner.core.miner.braiins.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public final class GraphQLExecutor {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final ObjectMapper mapper = new ObjectMapper();

    public HttpResponse<String> executeRaw(String host, int port, String query, Map<String, Object> variables, String sessionCookie) throws IOException, InterruptedException {
        URI endpoint = URI.create("http://" + host + ":" + port + "/graphql");
        ObjectNode body = mapper.createObjectNode();

        body.put("query", query);

        if (variables != null) {
            body.set("variables", mapper.valueToTree(variables));
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).header("Content-Type", "application/json");

        if (sessionCookie != null && !sessionCookie.isBlank()) {

            builder.header("Cookie", sessionCookie);
        }

        builder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    public JsonNode execute(String host, int port, String query, Map<String, Object> variables, String sessionCookie) throws IOException, InterruptedException {

        HttpResponse<String> response = executeRaw(host, port, query, variables, sessionCookie);

        return mapper.readTree(response.body());
    }
}