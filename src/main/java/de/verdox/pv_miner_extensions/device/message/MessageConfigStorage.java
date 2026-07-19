package de.verdox.pv_miner_extensions.device.message;

import de.verdox.solarminer.rest.RestPVConfig;
import de.verdox.vserializer.generic.SerializationElement;
import de.verdox.vserializer.json.JsonSerializerContext;
import org.apache.commons.compress.utils.FileNameUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Service
public class MessageConfigStorage {
    public static final String MQTT = "mqtt";
    public static final String WEBSOCKET = "websocket";
    private final Map<String, RestPVConfig> cache = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    void initialize() {
        folder(MQTT).mkdirs();
        folder(WEBSOCKET).mkdirs();
    }

    public List<String> getSavedConfigs(String protocol) throws IOException {
        File folder = folder(protocol);
        if (!folder.isDirectory()) return List.of();
        try (Stream<Path> paths = Files.walk(folder.toPath(), 1)) {
            return paths.filter(Files::isRegularFile).map(FileNameUtils::getBaseName).sorted().toList();
        }
    }

    public void save(String protocol, String name, RestPVConfig config) throws IOException {
        validateName(name);
        File folder = folder(protocol);
        folder.mkdirs();
        File file = new File(folder, name + ".json");
        JsonSerializerContext context = new JsonSerializerContext();
        context.writeToFile(RestPVConfig.SERIALIZER.serialize(context, config), file);
        cache.put(key(protocol, name), config);
    }

    public RestPVConfig loadConfig(String protocol, String name) throws IOException {
        validateName(name);
        RestPVConfig cached = cache.get(key(protocol, name));
        if (cached != null) return cached;
        File file = new File(folder(protocol), name + ".json");
        if (!file.isFile()) throw new NoSuchElementException("The " + protocol + " config " + name + " does not exist");
        JsonSerializerContext context = new JsonSerializerContext();
        SerializationElement element = context.readFromFile(file);
        String json = Files.readString(file.toPath());
        RestPVConfig loaded = json.contains("\"authenticationType\"")
                ? RestPVConfig.SERIALIZER.deserialize(element) : RestPVConfig.V1_SERIALIZER.deserialize(element);
        cache.put(key(protocol, name), loaded);
        return loaded;
    }

    public boolean exists(String protocol, String name) {
        validateName(name);
        return new File(folder(protocol), name + ".json").isFile();
    }

    public boolean delete(String protocol, String name) {
        validateName(name);
        cache.remove(key(protocol, name));
        return new File(folder(protocol), name + ".json").delete();
    }

    private File folder(String protocol) {
        if (!MQTT.equals(protocol) && !WEBSOCKET.equals(protocol)) throw new IllegalArgumentException("Unsupported message protocol");
        return new File("./storage/" + protocol + "/");
    }

    private void validateName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9._ -]{1,120}")) throw new IllegalArgumentException("Invalid config name");
    }

    private String key(String protocol, String name) { return protocol + ":" + name; }
}
