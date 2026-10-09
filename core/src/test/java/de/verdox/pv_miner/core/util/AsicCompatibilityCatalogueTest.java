package de.verdox.pv_miner.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsicCompatibilityCatalogueTest {
    @Test
    void catalogueIsTheExplicitSupportDeclarationForEveryKnownAntminer() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/asics/supported-compatibility.json")) {
            assertNotNull(input, "canonical ASIC compatibility catalogue must be packaged");
            JsonNode root = new ObjectMapper().readTree(input);
            assertEquals(1, root.path("schemaVersion").asInt());
            assertEquals("Solar-Miner-Node/core", root.path("owner").asText());
            assertEquals("SHA-256", root.path("algorithm").asText());

            JsonNode profiles = root.path("firmwareProfiles");
            assertTrue(profiles.path("BITMAIN_STOCK").path("capabilities").toString().contains("POOL_ROUTING"));
            assertTrue(profiles.path("BRAIINS_OS").path("capabilities").toString().contains("DYNAMIC_POWER_SCALING"));

            Set<String> catalogueModels = new HashSet<>();
            for (JsonNode group : root.path("modelGroups")) {
                assertTrue(group.path("models").isArray() && !group.path("models").isEmpty());
                assertTrue(group.path("firmwareProfileIds").isArray() && !group.path("firmwareProfileIds").isEmpty());
                for (JsonNode profileId : group.path("firmwareProfileIds")) {
                    assertTrue(profiles.has(profileId.asText()), "unknown firmware profile " + profileId.asText());
                }
                for (JsonNode model : group.path("models")) {
                    assertTrue(catalogueModels.add(model.asText().toUpperCase()), "duplicate model " + model.asText());
                }
            }

            Set<String> knownAntminers = AsicMinerSpec.all().stream()
                    .map(AsicMinerSpec::model)
                    .map(String::toUpperCase)
                    .filter(model -> model.startsWith("ANTMINER") || model.startsWith("ANTROUTER"))
                    .collect(Collectors.toSet());
            assertEquals(knownAntminers, catalogueModels,
                    "adding or removing an Antminer requires the canonical compatibility catalogue in the same change");
        }
    }
}
