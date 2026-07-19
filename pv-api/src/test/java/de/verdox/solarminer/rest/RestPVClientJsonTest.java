package de.verdox.solarminer.rest;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestPVClientJsonTest {
    @Test
    void readsObjectAndArrayPathsUsedByGeneratedProfiles() throws Exception {
        String json = """
                {
                  "power_io": -250,
                  "powers": [100, 200, 300],
                  "nested": {"channels": [{"power": 42.5}]}
                }
                """;

        assertEquals("-250", RestPVClient.extractFromJson(json, "$.power_io", "http://device/all"));
        assertEquals("200", RestPVClient.extractFromJson(json, "$.powers[1]", "http://device/all"));
        assertEquals("42.5", RestPVClient.extractFromJson(json, "$.nested.channels[0].power", "http://device/all"));
    }

    @Test
    void rejectsMissingOrMalformedArrayPaths() {
        String json = "{\"powers\":[100]}";
        assertThrows(IOException.class, () -> RestPVClient.extractFromJson(json, "$.powers[2]", "http://device/all"));
        assertThrows(IOException.class, () -> RestPVClient.extractFromJson(json, "$.powers[x]", "http://device/all"));
    }

    @Test
    void readsRootArraysAndQuotedObjectKeys() throws Exception {
        assertEquals("7", RestPVClient.extractFromJson("[[1,7]]", "$[0][1]", "test"));
        assertEquals("42", RestPVClient.extractFromJson("{\"1074\":{\"1\":42}}", "$[\"1074\"][\"1\"]", "test"));
    }

    @Test
    void extractsPlainTextValuesWithRegex() throws Exception {
        assertEquals("1234", RestPVClient.extractWithRegex("power=<b>1234</b>", "power=<b>(\\d+)</b>", "test"));
    }

    @Test
    void evaluatesSafeArithmeticAcrossJsonValues() throws Exception {
        String json = "{\"data\":{\"p1\":120.5,\"p2\":79.5}}";
        assertEquals("200.0", RestPVClient.extractFromJson(json, "expr:$.data.p1 + $.data.p2", "test"));
        assertThrows(IOException.class, () -> RestPVClient.extractFromJson(json, "expr:java.lang.Runtime", "test"));
    }
}
