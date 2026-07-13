package de.verdox.solarminer.rest;

import com.google.common.reflect.TypeToken;
import de.verdox.vserializer.SerializableField;
import de.verdox.vserializer.generic.Serializer;
import de.verdox.vserializer.generic.SerializerBuilder;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@Getter
public class RestPVConfig {
    public static final Serializer<Entry<?>> ENTRY_SERIALIZER = SerializerBuilder.create("rest_config_entry", new TypeToken<Entry<?>>() {})
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

    public static final Serializer<ConfigSection> SECTION_SERIALIZER = SerializerBuilder.create("rest_config_section", ConfigSection.class)
            .constructor(
                    new SerializableField<>("templateId", Serializer.Primitive.STRING, ConfigSection::getTemplateId),
                    new SerializableField<>("name", Serializer.Primitive.STRING, ConfigSection::getName),
                    new SerializableField<>("entries", Serializer.Map.create(Serializer.Primitive.STRING, ENTRY_SERIALIZER, HashMap::new), ConfigSection::getEntries),
                    ConfigSection::new
            ).build();

    public static final Serializer<RestPVConfig> SERIALIZER = SerializerBuilder.create("rest_config", RestPVConfig.class)
            .constructor(
                    new SerializableField<>("sections", Serializer.Map.create(Serializer.Primitive.STRING, SECTION_SERIALIZER, HashMap::new), RestPVConfig::getSections),
                    RestPVConfig::new
            ).build();


    private final Map<String, ConfigSection> sections;

    public RestPVConfig(Map<String, ConfigSection> sections) {
        this.sections = sections != null ? sections : new HashMap<>();
    }

    public boolean supportsTemplate(String templateId) {
        return sections.containsKey(templateId);
    }

    public ConfigSection getSection(String templateId) {
        return sections.get(templateId);
    }

    @Override
    public String toString() {
        return "RestPVConfig{" +
                "sections=" + sections +
                '}';
    }

    @Getter
    public static class ConfigSection {
        @Getter
        private final String templateId;
        @Getter
        private final String name;
        private final Map<String, Entry<?>> entries;

        public ConfigSection(String templateId, String name, Map<String, Entry<?>> entries) {
            this.templateId = templateId;
            this.name = name;
            this.entries = entries != null ? entries : new HashMap<>();
        }


        public <T extends Entry<?>> T getEntryForId(String id) {
            if (!entries.containsKey(id)) {
                throw new NoSuchElementException("REST ConfigSection is missing an entry for the id: " + id);
            }
            return (T) entries.get(id);
        }

        @Override
        public String toString() {
            return "ConfigSection{entries=" + entries + '}';
        }
    }

    public record Entry<T extends Number>(
            String urlExtension,
            RestHttpMethod httpMethod,
            RestResponseType responseType,
            String dataPath,
            float scaleFactor,
            String formula,
            RestParameterType<T> restParameterType
    ) {}
}