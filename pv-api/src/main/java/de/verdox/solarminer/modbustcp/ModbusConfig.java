package de.verdox.solarminer.modbustcp;

import com.google.common.reflect.TypeToken;
import de.verdox.solarminer.rest.RestPVConfig;
import de.verdox.vserializer.SerializableField;
import de.verdox.vserializer.generic.Serializer;
import de.verdox.vserializer.generic.SerializerBuilder;
import lombok.Getter;

import java.nio.ByteOrder;
import java.util.*;

@Getter
public class ModbusConfig {

    public static final Serializer<ByteOrder> BYTE_ORDER_SERIALIZER = SerializerBuilder.createObjectToPrimitiveSerializer("byteorder", ByteOrder.class, Serializer.Primitive.STRING, ByteOrder::toString, s -> {
        if (s.toUpperCase(Locale.ROOT).equals(ByteOrder.LITTLE_ENDIAN.toString())) {
            return ByteOrder.LITTLE_ENDIAN;
        } else if (s.toUpperCase().equals(ByteOrder.BIG_ENDIAN.toString())) {
            return ByteOrder.BIG_ENDIAN;
        }
        throw new NoSuchElementException("Byte order " + s.toUpperCase(Locale.ROOT) + " not known");
    });

    public static final Serializer<Entry<?>> ENTRY_SERIALIZER = SerializerBuilder.create("modbus_config_entry", new TypeToken<Entry<?>>() {})
            .constructor(
                    new SerializableField<>("startAddress", Serializer.Primitive.INTEGER, Entry::startAddress),
                    new SerializableField<>("size", Serializer.Primitive.INTEGER, Entry::size),
                    new SerializableField<>("scaleFactor", Serializer.Primitive.FLOAT, Entry::scaleFactor),
                    new SerializableField<>("formula", Serializer.Primitive.STRING, Entry::formula),
                    new SerializableField<>("parameterType", ModbusParameterType.SERIALIZER, Entry::modbusParameterType),
                    new SerializableField<>("operationType", ModbusReadOperationType.SERIALIZER, Entry::readOperationType),
                    new SerializableField<>("byteOrder", ModbusConfig.BYTE_ORDER_SERIALIZER, Entry::byteOrder),
                    Entry::new
            ).build();

    public static final Serializer<Fingerprint> FINGERPRINT_SERIALIZER = SerializerBuilder.create("modbus_fingerprint", new TypeToken<Fingerprint>() {})
            .constructor(
                    new SerializableField<>("address", Serializer.Primitive.INTEGER, Fingerprint::address),
                    new SerializableField<>("size", Serializer.Primitive.INTEGER, Fingerprint::size),
                    new SerializableField<>("parameterType", ModbusParameterType.SERIALIZER, Fingerprint::parameterType),
                    new SerializableField<>("operationType", ModbusReadOperationType.SERIALIZER, Fingerprint::operationType),
                    new SerializableField<>("byteOrder", ModbusConfig.BYTE_ORDER_SERIALIZER, Fingerprint::byteOrder),
                    new SerializableField<>("expectedValue", Serializer.Primitive.STRING, Fingerprint::expectedValue),
                    Fingerprint::new
            ).build();

    public static final Serializer<ConfigSection> SECTION_SERIALIZER = SerializerBuilder.create("modbus_config_section", ConfigSection.class)
            .constructor(
                    new SerializableField<>("templateId", Serializer.Primitive.STRING, ModbusConfig.ConfigSection::getTemplateId),
                    new SerializableField<>("name", Serializer.Primitive.STRING, ModbusConfig.ConfigSection::getName),
                    new SerializableField<>("entries", Serializer.Map.create(Serializer.Primitive.STRING, ENTRY_SERIALIZER, HashMap::new), ConfigSection::getEntries),
                    ConfigSection::new
            ).build();

    public static final Serializer<ModbusConfig> SERIALIZER = SerializerBuilder.create("modbus_config", ModbusConfig.class)
            .constructor(
                    new SerializableField<>("fingerprint", FINGERPRINT_SERIALIZER, ModbusConfig::getFingerprint),
                    new SerializableField<>("sections", Serializer.Map.create(Serializer.Primitive.STRING, SECTION_SERIALIZER, HashMap::new), ModbusConfig::getSections),
                    new SerializableField<>("addressOffset", Serializer.Primitive.INTEGER, ModbusConfig::getAddressOffset),
                    ModbusConfig::new
            ).build();

    /** Reads the single-section format used before component based PV sites. */
    public static final Serializer<ModbusConfig> LEGACY_SERIALIZER = SerializerBuilder.create("legacy_modbus_config", ModbusConfig.class)
            .constructor(
                    new SerializableField<>("fingerprint", FINGERPRINT_SERIALIZER, ModbusConfig::getFingerprint),
                    new SerializableField<>("entries", Serializer.Map.create(Serializer.Primitive.STRING, ENTRY_SERIALIZER, HashMap::new),
                            config -> config.getSections().getOrDefault(ModbusConfigCreatorTemplate.PV_SITE.id(),
                                    new ConfigSection(ModbusConfigCreatorTemplate.PV_SITE.id(), "Migrated PV site", Map.of())).getEntries()),
                    (fingerprint, entries) -> new ModbusConfig(fingerprint, Map.of(
                            ModbusConfigCreatorTemplate.PV_SITE.id(),
                            new ConfigSection(ModbusConfigCreatorTemplate.PV_SITE.id(), "Migrated PV site", entries)
                    ), 0)
            ).build();


    // --- Datenstruktur ---
    private final Fingerprint fingerprint;
    private final Map<String, ConfigSection> sections;
    private final int addressOffset;

    public ModbusConfig(Fingerprint fingerprint, Map<String, ConfigSection> sections, int addressOffset) {
        this.fingerprint = fingerprint;
        this.sections = sections != null ? sections : new HashMap<>();
        this.addressOffset = addressOffset;
    }

    public boolean supportsTemplate(String templateId) {
        return sections.containsKey(templateId);
    }

    public ConfigSection getSection(String templateId) {
        return sections.get(templateId);
    }

    @Override
    public String toString() {
        return "ModbusConfig{" +
                "fingerprint=" + fingerprint +
                ", sections=" + sections +
                '}';
    }


    @Getter
    public static class ConfigSection {
        @Getter
        private final String templateId;
        @Getter
        private final String name;
        private final Map<String, ModbusConfig.Entry<?>> entries;

        public ConfigSection(String templateId, String name, Map<String, ModbusConfig.Entry<?>> entries) {
            this.templateId = templateId;
            this.name = name;
            this.entries = entries != null ? entries : new HashMap<>();
        }

        public <T extends Entry<?>> T getEntryForId(String id) {
            if (!entries.containsKey(id)) {
                throw new NoSuchElementException("Modbus ConfigSection is missing an entry for the id: " + id);
            }
            return (T) entries.get(id);
        }

        @Override
        public String toString() {
            return "ConfigSection{entries=" + entries + '}';
        }
    }

    public record Fingerprint(
            int address, int size, ModbusParameterType<?> parameterType,
            ModbusReadOperationType operationType, ByteOrder byteOrder, String expectedValue
    ) {
        public Entry<?> toDummyEntry() {
            return new Entry<>(address, size, 1.0f, "x", parameterType, operationType, byteOrder);
        }
    }

    public record Entry<T>(
            int startAddress, int size, float scaleFactor, String formula,
            ModbusParameterType<T> modbusParameterType, ModbusReadOperationType readOperationType, ByteOrder byteOrder
    ) {}
}
