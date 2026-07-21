package de.verdox.pv_miner_extensions.device.rest;

import de.verdox.pv_miner.configfetcher.ConfigFetcherService;
import de.verdox.solarminer.rest.RestConfigCreatorTemplate;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

@Service
public class RestConfigStorage {
    private static final Logger LOGGER = Logger.getLogger(RestConfigStorage.class.getSimpleName());
    private final File storageFolder = new File("./storage/rest/");
    private final Map<String, RestPVConfig> cache = new HashMap<>();
    private final ConfigFetcherService configFetcherService;

    public RestConfigStorage(ConfigFetcherService configFetcherService) {
        this.configFetcherService = configFetcherService;
    }

    @EventListener(ApplicationReadyEvent.class)
    private void initStorage() {
        LOGGER.info("Initialize REST storage at " + storageFolder.getAbsolutePath());
        try {
            if (!storageFolder.mkdirs() && !storageFolder.isDirectory()) {
                LOGGER.warning("Could not initialize REST storage at " + storageFolder.getAbsolutePath());
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not initialize REST storage at " + storageFolder.getAbsolutePath(), e);
        }
    }

    public List<String> getSavedConfigs() throws IOException {
        if (!storageFolder.isDirectory() || !storageFolder.exists()) {
            return List.of("");
        }
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        try (Stream<Path> pathStream = Files.walk(storageFolder.toPath(), 1)) {
            pathStream.filter(Files::isRegularFile).map(FileNameUtils::getBaseName).forEach(names::add);
        }
        Path legacyFolder = storageFolder.toPath().resolve("pvsite");
        if (Files.isDirectory(legacyFolder)) {
            try (Stream<Path> pathStream = Files.walk(legacyFolder, 1)) {
                pathStream.filter(Files::isRegularFile).map(FileNameUtils::getBaseName).forEach(names::add);
            }
        }
        return List.copyOf(names);
    }

    public void save(String nameOfConfig, RestPVConfig restConfig) throws IOException {
        File saveFile = new File(storageFolder + "/" + nameOfConfig + ".json");
        storageFolder.mkdirs();
        JsonSerializerContext jsonSerializerContext = new JsonSerializerContext();
        SerializationElement element = RestPVConfig.SERIALIZER.serialize(jsonSerializerContext, restConfig);
        jsonSerializerContext.writeToFile(element, saveFile);
        LOGGER.info("Saved REST config " + saveFile);
        cache.put(nameOfConfig, restConfig);
    }

    public boolean delete(String nameOfConfig) throws IOException {
        cache.remove(nameOfConfig);
        File saveFile = new File(storageFolder + "/" + nameOfConfig + ".json");
        if (!storageFolder.exists() || !storageFolder.isDirectory() || !saveFile.exists() || !saveFile.isFile()) {
            return false;
        }
        LOGGER.info("Deleted REST config " + saveFile);
        return saveFile.delete();
    }

    public RestPVConfig loadConfig(String name) throws IOException {
        if (cache.containsKey(name)) {
            return cache.get(name);
        }

        File saveFile = findConfigFile(name);
        if (saveFile == null) {
            RestPVConfig bundled = configFetcherService.getRestPVConfig(name).orElse(null);
            if (bundled != null) {
                cache.put(name, bundled);
                return bundled;
            }
            throw new NoSuchElementException("The rest config "+name+" does not exist!");
        }
        LOGGER.info("Loading REST config " + saveFile);
        JsonSerializerContext jsonSerializerContext = new JsonSerializerContext();
        SerializationElement element = jsonSerializerContext.readFromFile(saveFile);
        String json = Files.readString(saveFile.toPath());
        RestPVConfig loaded;
        if (json.contains("\"entries\"") && !json.contains("\"sections\"")) {
            loaded = RestPVConfig.LEGACY_SERIALIZER.deserialize(element);
        } else if (json.contains("\"authenticationType\"")) {
            loaded = RestPVConfig.SERIALIZER.deserialize(element);
        } else {
            loaded = RestPVConfig.V1_SERIALIZER.deserialize(element);
        }
        cache.put(name, loaded);
        return loaded;
    }

    public boolean doesConfigExistOnDisk(String selectedConfigName) {
        return findConfigFile(selectedConfigName) != null;
    }

    private File findConfigFile(String name) {
        if (name == null || !storageFolder.isDirectory()) return null;
        String normalized = normalizeName(name);
        for (File folder : List.of(storageFolder, new File(storageFolder, "pvsite"))) {
            File[] files = folder.listFiles(file -> file.isFile() && file.getName().endsWith(".json"));
            if (files == null) continue;
            for (File file : files) {
                if (normalizeName(FileNameUtils.getBaseName(file.getName())).equals(normalized)) return file;
            }
        }
        return null;
    }

    private String normalizeName(String value) {
        return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
