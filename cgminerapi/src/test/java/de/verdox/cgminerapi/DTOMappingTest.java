package de.verdox.cgminerapi;

import com.fasterxml.jackson.databind.JsonNode;
import de.verdox.cgminerapi.dto.CGMinerDTO;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DTOMappingTest
        extends AbstractMappingTest {

    static Stream<Arguments> resources() {

        return Arrays.stream(
                        StandardCommand.values()
                )
                .map(command ->
                        Arguments.of(
                                command.command()
                                        + ".json",
                                CGMinerClient.TYPES.get(command.responseSection())
                        ));
    }

    @ParameterizedTest
    @MethodSource("resources")
    void shouldDeserialize(
            String file,
            Class<? extends CGMinerDTO> type
    ) throws Exception {

        String json =
                resource(file);

        Object dto =
                mapper.readValue(
                        json,
                        type
                );

        assertNotNull(dto);
    }

    @ParameterizedTest
    @MethodSource("resources")
    void shouldRoundtrip(
            String file,
            Class<? extends CGMinerDTO> type
    ) throws Exception {

        String json =
                resource(file);

        Object dto =
                mapper.readValue(
                        json,
                        type
                );

        String serialized =
                mapper.writeValueAsString(
                        dto
                );

        JsonNode original =
                mapper.readTree(json);

        JsonNode recreated =
                mapper.readTree(serialized);

        assertEquals(
                original,
                recreated
        );
    }
}
