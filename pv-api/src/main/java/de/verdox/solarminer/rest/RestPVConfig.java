package de.verdox.solarminer.rest;

import com.google.common.reflect.TypeToken;
import de.verdox.solarminer.SimpleConfig;
import de.verdox.vserializer.SerializableField;
import de.verdox.vserializer.generic.Serializer;
import de.verdox.vserializer.generic.SerializerBuilder;

import java.util.Map;
import java.util.NoSuchElementException;

public class RestPVConfig extends SimpleConfig<RestPVConfig.Entry<?>> {
    public static final Serializer<RestPVConfig> SERIALIZER = SERIALIZER("rest_config", RestPVConfig.class, Entry.SERIALIZER, RestPVConfig::new);

    public RestPVConfig(Map<String, Entry<?>> entries) {
        super(entries);
    }

    @Override
    public <T extends Entry<?>> T getEntryForId(String id) {
        if (!configEntries.containsKey(id)) {
            throw new NoSuchElementException("Rest config is missing an entry for the id " + id);
        }
        return super.getEntryForId(id);
    }

    public record Entry<T extends Number>(
            String urlExtension,
            RestHttpMethod httpMethod,
            RestResponseType responseType,
            String dataPath,
            float scaleFactor,
            String formula,
            RestParameterType<T> restParameterType
    ) {
        public static final Serializer<Entry<?>> SERIALIZER = SerializerBuilder.create("rest_config_entry", new TypeToken<Entry<?>>() {})
                .constructor(
                        new SerializableField<>("urlExtension", Serializer.Primitive.STRING, Entry::urlExtension),
                        new SerializableField<>("httpMethod", RestHttpMethod.SERIALIZER, Entry::httpMethod),
                        new SerializableField<>("responseType", RestResponseType.SERIALIZER, Entry::responseType),
                        new SerializableField<>("dataPath", Serializer.Primitive.STRING, Entry::dataPath),
                        new SerializableField<>("scaleFactor", Serializer.Primitive.FLOAT, Entry::scaleFactor),
                        new SerializableField<>("formula", Serializer.Primitive.STRING, Entry::formula),
                        new SerializableField<>("parameterType", RestParameterType.SERIALIZER, Entry::restParameterType),
                        Entry::new
                ).build();
    }
}
