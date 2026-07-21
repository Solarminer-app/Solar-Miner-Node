package de.verdox.solarminer;

import de.verdox.solarminer.modbustcp.ModbusConfig;
import de.verdox.solarminer.modbustcp.ModbusConfigCreatorTemplate;
import de.verdox.solarminer.rest.RestConfigCreatorTemplate;
import de.verdox.solarminer.rest.RestPVConfig;
import de.verdox.vserializer.json.JsonSerializerContext;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvccGeneratedProfilesTest {

    @Test
    void generatedProfilesMatchTheSolarMinerSchemas() throws Exception {
        Path root = findGeneratedProfileRoot();
        List<Path> modbusTcpProfiles = jsonFiles(root.resolve("modbus"));
        List<Path> modbusRtuProfiles = jsonFiles(root.resolve("modbus-rtu"));
        List<Path> restProfiles = jsonFiles(root.resolve("rest"));
        List<Path> mqttProfiles = jsonFiles(root.resolve("mqtt"));

        assertFalse(modbusTcpProfiles.isEmpty(), "Expected generated Modbus TCP profiles");
        assertFalse(modbusRtuProfiles.isEmpty(), "Expected generated Modbus RTU profiles");
        assertFalse(restProfiles.isEmpty(), "Expected generated REST profiles");
        assertFalse(mqttProfiles.isEmpty(), "Expected generated MQTT profiles");

        for (Path profile : Stream.concat(modbusTcpProfiles.stream(), modbusRtuProfiles.stream()).toList()) {
            JsonSerializerContext context = new JsonSerializerContext();
            ModbusConfig config = ModbusConfig.SERIALIZER.deserialize(context.readFromFile(profile.toFile()));
            assertNull(config.getFingerprint(), "Generated EVCC profiles must not fabricate a fingerprint: " + profile);
            assertFalse(config.getSections().isEmpty(), "Profile has no device sections: " + profile);
            config.getSections().values().forEach(section -> {
                ModbusConfigCreatorTemplate template = ModbusConfigCreatorTemplate.byId(section.getTemplateId());
                template.requiredFields().forEach(field -> assertTrue(
                        section.getEntries().containsKey(field.field()),
                        () -> profile + " is missing " + field.field()));
            });
        }

        for (Path profile : Stream.concat(restProfiles.stream(), mqttProfiles.stream()).toList()) {
            JsonSerializerContext context = new JsonSerializerContext();
            RestPVConfig config = RestPVConfig.SERIALIZER.deserialize(context.readFromFile(profile.toFile()));
            assertFalse(config.getSections().isEmpty(), "Profile has no device sections: " + profile);
            config.getSections().values().forEach(section -> {
                RestConfigCreatorTemplate template = RestConfigCreatorTemplate.getAll().stream()
                        .filter(candidate -> candidate.id().equals(section.getTemplateId()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Unknown template " + section.getTemplateId()));
                template.requiredFields().forEach(field -> assertTrue(
                        section.getEntries().containsKey(field.field()),
                        () -> profile + " is missing " + field.field()));
            });
        }
    }

    private Path findGeneratedProfileRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("device-profiles/bundled");
            if (Files.isDirectory(candidate)) return candidate;
            current = current.getParent();
        }
        throw new AssertionError("Could not find device-profiles/bundled");
    }

    private List<Path> jsonFiles(Path directory) throws IOException {
        try (var stream = Files.list(directory)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList();
        }
    }
}
