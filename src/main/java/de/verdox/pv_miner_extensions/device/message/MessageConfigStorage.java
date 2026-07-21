package de.verdox.pv_miner_extensions.device.message;

import de.verdox.pv_miner.configfetcher.ConfigFetcherService;
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
    private final ConfigFetcherService configFetcherService;

    public MessageConfigStorage(ConfigFetcherService configFetcherService) {
        this.configFetcherService = configFetcherService;
    }

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
        File file = findConfigFile(protocol, name);
        if (file == null) {
            RestPVConfig bundled = configFetcherService.getMessagePVConfig(protocol, name).orElse(null);
            if (bundled != null) {
                cache.put(key(protocol, name), bundled);
                return bundled;
            }
            throw new NoSuchElementException("The " + protocol + " config " + name + " does not exist");
        }
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
        return findConfigFile(protocol, name) != null;
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

    private File findConfigFile(String protocol, String name) {
        File[] files = folder(protocol).listFiles(file -> file.isFile() && file.getName().endsWith(".json"));
        if (files == null) return null;
        String normalized = normalizeName(name);
        for (File file : files) {
            if (normalizeName(FileNameUtils.getBaseName(file.getName())).equals(normalized)) return file;
        }
        return null;
    }

    private String normalizeName(String value) {
        return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
