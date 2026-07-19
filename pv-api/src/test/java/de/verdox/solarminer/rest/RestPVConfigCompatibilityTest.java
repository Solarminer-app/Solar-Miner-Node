package de.verdox.solarminer.rest;

import de.verdox.vserializer.json.JsonSerializerContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RestPVConfigCompatibilityTest {

    @Test
    void readsComponentProfilesWrittenBeforeAuthenticationMetadata() {
        RestPVConfig original = new RestPVConfig(Map.of());
        JsonSerializerContext context = new JsonSerializerContext();
        var serialized = RestPVConfig.V1_SERIALIZER.serialize(context, original);

        RestPVConfig restored = RestPVConfig.V1_SERIALIZER.deserialize(serialized);

        assertEquals(RestAuthenticationType.BEARER, restored.getAuthenticationType());
        assertEquals(Map.of(), restored.getSections());
    }

    @Test
    void preservesAuthenticationInCurrentProfiles() {
        RestPVConfig original = new RestPVConfig(RestAuthenticationType.BASIC, Map.of());
        JsonSerializerContext context = new JsonSerializerContext();
        var serialized = RestPVConfig.SERIALIZER.serialize(context, original);

        RestPVConfig restored = RestPVConfig.SERIALIZER.deserialize(serialized);

        assertEquals(RestAuthenticationType.BASIC, restored.getAuthenticationType());
    }
}
