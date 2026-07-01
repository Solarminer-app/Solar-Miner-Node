package de.verdox.pv_miner_extensions.inverter.modbustcp.config;

import com.google.common.reflect.TypeToken;
import de.verdox.pv_miner.configuration.SimpleConfig;
import de.verdox.vserializer.SerializableField;
import de.verdox.vserializer.generic.Serializer;
import de.verdox.vserializer.generic.SerializerBuilder;

import java.nio.ByteOrder;
import java.util.*;
import java.util.function.Function;

public class ModbusConfig extends SimpleConfig<ModbusConfig.Entry<?>> {
    public static final Serializer<ByteOrder> BYTE_ORDER_SERIALIZER = SerializerBuilder.createObjectToPrimitiveSerializer("byteorder", ByteOrder.class, Serializer.Primitive.STRING, ByteOrder::toString, s -> {
        if (s.toUpperCase(Locale.ROOT).equals(ByteOrder.LITTLE_ENDIAN.toString())) {
            return ByteOrder.LITTLE_ENDIAN;
        } else if (s.toUpperCase().equals(ByteOrder.BIG_ENDIAN.toString())) {
            return ByteOrder.BIG_ENDIAN;
        }
        throw new NoSuchElementException("Byte order " + s.toUpperCase(Locale.ROOT) + " not known");
    });

    public static final Serializer<Entry<?>> ENTRY_SERIALIZER = SerializerBuilder.create("modbus_config_entry", new TypeToken<Entry<?>>() {
            })
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

    public static final Serializer<Fingerprint> FINGERPRINT_SERIALIZER = SerializerBuilder.create("modbus_fingerprint", new TypeToken<Fingerprint>() {
            })
            .constructor(
                    new SerializableField<>("address", Serializer.Primitive.INTEGER, Fingerprint::address),
                    new SerializableField<>("size", Serializer.Primitive.INTEGER, Fingerprint::size),
                    new SerializableField<>("parameterType", ModbusParameterType.SERIALIZER, Fingerprint::parameterType),
                    new SerializableField<>("operationType", ModbusReadOperationType.SERIALIZER, Fingerprint::operationType),
                    new SerializableField<>("byteOrder", ModbusConfig.BYTE_ORDER_SERIALIZER, Fingerprint::byteOrder),
                    new SerializableField<>("expectedValue", Serializer.Primitive.STRING, Fingerprint::expectedValue),
                    Fingerprint::new
            ).build();
    private final Fingerprint fingerprint;


    public record Fingerprint(
            int address,
            int size,
            ModbusParameterType<?> parameterType,
            ModbusReadOperationType operationType,
            ByteOrder byteOrder,
            String expectedValue
    ) {
        public ModbusConfig.Entry<?> toDummyEntry() {
            return new ModbusConfig.Entry<>(address, size, 1.0f, "x", parameterType, operationType, byteOrder);
        }
    }

    public record Entry<T>(int startAddress, int size, float scaleFactor, String formula,
                                          ModbusParameterType<T> modbusParameterType,
                                          ModbusReadOperationType readOperationType,
                                          ByteOrder byteOrder) {
    }

    public static final Serializer<ModbusConfig> SERIALIZER = SerializerBuilder.create("modbus_config", ModbusConfig.class)
            .constructor(
                    new SerializableField<>("fingerprint", FINGERPRINT_SERIALIZER, ModbusConfig::getFingerprint),
                    new SerializableField<>("entries", Serializer.Map.create(Serializer.Primitive.STRING, ENTRY_SERIALIZER, HashMap::new), SimpleConfig::getConfigEntries),
                    ModbusConfig::new
            )
            .build();


    public ModbusConfig(Fingerprint fingerprint, Map<String, Entry<?>> entries) {
        super(entries);
        this.fingerprint = fingerprint;
    }

    public Fingerprint getFingerprint() {
        return fingerprint;
    }

    @Override
    public <T extends Entry<?>> T getEntryForId(String id) {
        if (!configEntries.containsKey(id)) {
            throw new NoSuchElementException("Modbus config is missing an entry for the id " + id);
        }
        return super.getEntryForId(id);
    }


    @Override
    public String toString() {
        return "ModbusConfig{" +
                "configEntries=" + configEntries +
                '}';
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
}
