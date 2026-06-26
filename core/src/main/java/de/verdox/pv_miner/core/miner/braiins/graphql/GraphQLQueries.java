package de.verdox.pv_miner.core.miner.braiins.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class GraphQLQueries {

    private static final String ROOT = "graphql/braiins/";

    private GraphQLQueries() {
    }

    public static String load(String path) {
        String resource = ROOT + path;
        try (InputStream in = GraphQLQueries.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing GraphQL resource: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
