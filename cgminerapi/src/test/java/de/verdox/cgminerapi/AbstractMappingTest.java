package de.verdox.cgminerapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;

abstract class AbstractMappingTest {

    protected final ObjectMapper mapper =
            new ObjectMapper()
                    .configure(
                            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                            false
                    )
                    .setSerializationInclusion(
                            JsonInclude.Include.NON_NULL
                    );

    protected String resource(String name)
            throws IOException {

        try (InputStream in =
                     getClass()
                             .getClassLoader()
                             .getResourceAsStream(
                                     "cgminer/" + name
                             )) {

            assertNotNull(
                    in,
                    "Missing resource: " + name
            );

            return new String(
                    in.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }
}
