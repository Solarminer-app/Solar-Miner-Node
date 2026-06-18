package de.verdox.pv_miner_extensions.restpv.config;

import com.google.common.reflect.TypeToken;
import de.verdox.vserializer.SerializableField;
import de.verdox.vserializer.generic.Serializer;
import de.verdox.vserializer.generic.SerializerBuilder;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;

public record RestParameterType<T extends Number>(String identifier, Function<Object, T> parser) {
    private static final Map<String, RestParameterType<?>> entries = new HashMap<>();

    public static final RestParameterType<Integer> INT = register(new RestParameterType<>("int", obj -> Integer.parseInt(obj.toString())));
    public static final RestParameterType<Long> LONG = register(new RestParameterType<>("long", obj -> Long.parseLong(obj.toString())));
    public static final RestParameterType<Float> FLOAT = register(new RestParameterType<>("float", obj -> Float.parseFloat(obj.toString())));
    public static final RestParameterType<Double> DOUBLE = register(new RestParameterType<>("double", obj -> Double.parseDouble(obj.toString())));

    public static final Serializer<RestParameterType<?>> SERIALIZER = SerializerBuilder.create("rest_parameter_type", new TypeToken<RestParameterType<?>>() {})
            .constructor(new SerializableField<RestParameterType<?>, String>(Serializer.Primitive.STRING, RestParameterType::identifier), RestParameterType::findById)
            .build();

    private static <T extends Number> RestParameterType<T> register(RestParameterType<T> entry) {
        entries.put(entry.identifier().toLowerCase(Locale.ROOT), entry);
        return entry;
    }

    public static RestParameterType<?> findById(String identifier) {
        if (!entries.containsKey(identifier.toLowerCase(Locale.ROOT))) {
            throw new NoSuchElementException("No rest parameter type known with id " + identifier.toLowerCase(Locale.ROOT));
        }
        return entries.get(identifier.toLowerCase(Locale.ROOT));
    }
}