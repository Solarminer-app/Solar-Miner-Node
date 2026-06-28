package de.verdox.cgminerapi.dump;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import de.verdox.cgminerapi.CGMinerClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

public final class CGMinerApiDumper {

    private final CGMinerClient client;
    private final ObjectMapper mapper;
    private final String host;
    private final int port;

    public CGMinerApiDumper(
            String host,
            int port
    ) {
        this.host = host;
        this.port = port;
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
        this.client = new CGMinerClient(mapper);
    }

    public void dump(
            Path directory,
            Collection<DumpSpec> specs
    ) throws IOException {

        Files.createDirectories(directory);

        for (DumpSpec spec : specs) {

            var command = spec.command();

            try {
                System.out.println(
                        "Executing "
                                + command.command()
                                + (
                                spec.parameter() == null
                                        ? ""
                                        : "|" + spec.parameter()
                        )
                );

                JsonNode response =
                        client.executeRaw(
                                host, port,
                                command.command(),
                                spec.parameter()
                        );

                Path file =
                        directory.resolve(
                                fileName(spec)
                        );

                mapper.writeValue(
                        file.toFile(),
                        response
                );

                System.out.println(
                        "Written "
                                + file.getFileName()
                );
            }
            catch (Exception e) {
                System.err.println(
                        "Failed "
                                + command.command()
                                + ": "
                                + e.getMessage()
                );
            }
        }
    }

    private static String fileName(
            DumpSpec spec
    ) {
        String name =
                spec.command().command();

        if (spec.parameter() == null
                || spec.parameter().isBlank()) {
            return name + ".json";
        }

        String parameter =
                spec.parameter()
                        .replace(',', '_')
                        .replace(':', '_')
                        .replace('/', '_')
                        .replace('\\', '_')
                        .replace('|', '_');

        return name
                + "-"
                + parameter
                + ".json";
    }
}
