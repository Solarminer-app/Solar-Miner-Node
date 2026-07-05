package de.verdox.solarminer;

import de.verdox.vserializer.SerializableField;
import de.verdox.vserializer.generic.Serializer;
import de.verdox.vserializer.generic.SerializerBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public abstract class SimpleConfig<C> {
    protected final Map<String, C> configEntries = new HashMap<>();

    public SimpleConfig(Map<String, C> configEntries) {
        configEntries.forEach((s, c) -> {
            if(c == null) {
                return;
            }
            this.configEntries.put(s, c);
        });
    }

    public static <C, S extends SimpleConfig<C>> Serializer<S> SERIALIZER(String name, Class<S> type, Serializer<C> entrySerializer, Function<Map<String, C>, S> constructor) {
        Objects.requireNonNull(entrySerializer, "entrySerializer cannot be null");
        return SerializerBuilder.create(name, type)
                .constructor(
                        new SerializableField<>("entries", Serializer.Map.create(Serializer.Primitive.STRING, entrySerializer, HashMap::new), SimpleConfig::getConfigEntries),
                        constructor
                )
                .build();
    }

    public Map<String, C> getConfigEntries() {
        return configEntries;
    }

    public <T extends C> T getEntryForId(String id) {
        return (T) configEntries.getOrDefault(id, null);
    }
}
