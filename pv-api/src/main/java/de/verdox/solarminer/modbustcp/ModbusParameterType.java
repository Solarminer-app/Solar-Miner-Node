package de.verdox.solarminer.modbustcp;

import com.google.common.reflect.TypeToken;
import de.verdox.vserializer.SerializableField;
import de.verdox.vserializer.generic.Serializer;
import de.verdox.vserializer.generic.SerializerBuilder;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;

public record ModbusParameterType<T>(String identifier, Function<ByteBuffer,  T> parser) {
    private static final Map<String, ModbusParameterType<?>> entries = new HashMap<>();

    public static final ModbusParameterType<Short> UINT8 = register(new ModbusParameterType<>("uint8", buffer -> (short) (buffer.get() & 0xFF)));
    public static final ModbusParameterType<Integer> UINT16 = register(new ModbusParameterType<>("uint16", buffer -> buffer.getShort() & 0xFFFF));
    public static final ModbusParameterType<Short> INT16 = register(new ModbusParameterType<>("int16", ByteBuffer::getShort));
    public static final ModbusParameterType<Long> UINT32 = register(new ModbusParameterType<>("uint32", buffer -> buffer.getInt() & 0xFFFFFFFFL));
    public static final ModbusParameterType<Integer> INT32 = register(new ModbusParameterType<>("int32", ByteBuffer::getInt));
    public static final ModbusParameterType<BigInteger> ULONG64 = register(new ModbusParameterType<>("ulong64", buffer -> new BigInteger(1, ByteBuffer.allocate(8).putLong(buffer.getLong()).array())));
    public static final ModbusParameterType<Float> FLOAT32 = register(new ModbusParameterType<>("float32", ByteBuffer::getFloat));
    public static final ModbusParameterType<Double> DOUBLE64 = register(new ModbusParameterType<>("double64", ByteBuffer::getDouble));

    public static final ModbusParameterType<String> STRING = register(new ModbusParameterType<>("string", buffer -> {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8).trim();
    }));

    public static final Serializer<ModbusParameterType<?>> SERIALIZER = SerializerBuilder.create("modbus_type", new TypeToken<ModbusParameterType<?>>() {})
            .constructor(new SerializableField<>(Serializer.Primitive.STRING, ModbusParameterType::identifier), ModbusParameterType::findById)
            .build();

    private static <T> ModbusParameterType<T> register(ModbusParameterType<T> entry) {
        entries.put(entry.identifier().toLowerCase(Locale.ROOT), entry);
        return entry;
    }

    public static ModbusParameterType<?> findById(String identifier) {
        if (!entries.containsKey(identifier.toLowerCase(Locale.ROOT))) {
            throw new NoSuchElementException("No modbus parameter type known with id " + identifier.toLowerCase(Locale.ROOT));
        }
        return entries.get(identifier.toLowerCase(Locale.ROOT));
    }

    public static ModbusParameterType<?>[] values() {
        return entries.values().toArray(ModbusParameterType[]::new);
    }
}
