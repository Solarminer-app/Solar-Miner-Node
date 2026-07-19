package de.verdox.solarminer.modbustcp;

import com.google.common.reflect.TypeToken;
import de.verdox.vserializer.SerializableField;
import de.verdox.vserializer.generic.Serializer;
import de.verdox.vserializer.generic.SerializerBuilder;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
    public static final ModbusParameterType<Long> INT64 = register(new ModbusParameterType<>("int64", ByteBuffer::getLong));

    public static final ModbusParameterType<BigInteger> ULONG64 = register(new ModbusParameterType<>("ulong64", buffer -> new BigInteger(1, ByteBuffer.allocate(8).putLong(buffer.getLong()).array())));
    public static final ModbusParameterType<BigInteger> UINT64 = register(new ModbusParameterType<>("uint64", buffer -> new BigInteger(1, ByteBuffer.allocate(8).putLong(buffer.getLong()).array())));

    public static final ModbusParameterType<Float> FLOAT32 = register(new ModbusParameterType<>("float32", ByteBuffer::getFloat));
    public static final ModbusParameterType<Double> DOUBLE64 = register(new ModbusParameterType<>("double64", ByteBuffer::getDouble));

    /*
     * A number of inverter register maps use the maximum unsigned value (or the minimum signed value)
     * as "not available". Keeping this distinction in the primitive decoder prevents those sentinels
     * from reaching statistics as implausibly large measurements. The identifiers intentionally match
     * the established EVCC/mbmd spelling so imported profiles stay readable.
     */
    public static final ModbusParameterType<Number> UINT16_NAN = register(new ModbusParameterType<>("uint16nan", buffer -> {
        int value = buffer.getShort() & 0xFFFF;
        return value == 0xFFFF ? null : value;
    }));
    public static final ModbusParameterType<Number> INT16_NAN = register(new ModbusParameterType<>("int16nan", buffer -> {
        short value = buffer.getShort();
        return value == Short.MIN_VALUE ? null : value;
    }));
    public static final ModbusParameterType<Number> UINT32_NAN = register(new ModbusParameterType<>("uint32nan", buffer -> {
        int raw = buffer.getInt();
        return raw == -1 ? null : raw & 0xFFFFFFFFL;
    }));
    public static final ModbusParameterType<Number> INT32_NAN = register(new ModbusParameterType<>("int32nan", buffer -> {
        int value = buffer.getInt();
        return value == Integer.MIN_VALUE ? null : value;
    }));
    public static final ModbusParameterType<Number> UINT64_NAN = register(new ModbusParameterType<>("uint64nan", buffer -> {
        long raw = buffer.getLong();
        return raw == -1L ? null : unsignedLong(raw);
    }));
    public static final ModbusParameterType<Number> INT64_NAN = register(new ModbusParameterType<>("int64nan", buffer -> {
        long value = buffer.getLong();
        return value == Long.MIN_VALUE ? null : value;
    }));

    public static final ModbusParameterType<Long> UINT32_SWAPPED = register(new ModbusParameterType<>("uint32s",
            buffer -> swapped(buffer, 2).getInt() & 0xFFFFFFFFL));
    public static final ModbusParameterType<Integer> INT32_SWAPPED = register(new ModbusParameterType<>("int32s",
            buffer -> swapped(buffer, 2).getInt()));
    public static final ModbusParameterType<Float> FLOAT32_SWAPPED = register(new ModbusParameterType<>("float32s",
            buffer -> swapped(buffer, 2).getFloat()));
    public static final ModbusParameterType<Number> FLOAT32_NAN_SWAPPED = register(new ModbusParameterType<>("float32nans", buffer -> {
        float value = swapped(buffer, 2).getFloat();
        return Float.isNaN(value) ? null : value;
    }));
    public static final ModbusParameterType<Number> UINT64_NAN_SWAPPED = register(new ModbusParameterType<>("uint64snan", buffer -> {
        long raw = swapped(buffer, 4).getLong();
        return raw == -1L ? null : unsignedLong(raw);
    }));

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

    private static BigInteger unsignedLong(long value) {
        return new BigInteger(1, ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }

    /** Reverses the order of 16-bit Modbus words while retaining byte order inside every word. */
    private static ByteBuffer swapped(ByteBuffer source, int wordCount) {
        byte[] input = new byte[wordCount * Short.BYTES];
        source.get(input);
        byte[] output = new byte[input.length];
        for (int word = 0; word < wordCount; word++) {
            int sourceOffset = (wordCount - word - 1) * Short.BYTES;
            int targetOffset = word * Short.BYTES;
            output[targetOffset] = input[sourceOffset];
            output[targetOffset + 1] = input[sourceOffset + 1];
        }
        return ByteBuffer.wrap(output).order(source.order() == null ? ByteOrder.BIG_ENDIAN : source.order());
    }
}
