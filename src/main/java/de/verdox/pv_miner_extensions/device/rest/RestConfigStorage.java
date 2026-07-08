package de.verdox.pv_miner_extensions.device.rest;

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
        try (Stream<Path> pathStream = Files.walk(storageFolder.toPath(), 1)) {
            return pathStream.map(FileNameUtils::getBaseName).skip(1).toList();
        }
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

        File saveFile = new File(storageFolder + "/" + name + ".json");
        if (!storageFolder.exists() || !storageFolder.isDirectory() || !saveFile.exists() || !saveFile.isFile()) {
            throw new NoSuchElementException("The rest config "+name+" does not exist!");
        }
        LOGGER.info("Loading REST config " + saveFile);
        JsonSerializerContext jsonSerializerContext = new JsonSerializerContext();
        SerializationElement element = jsonSerializerContext.readFromFile(saveFile);
        RestPVConfig loaded = RestPVConfig.SERIALIZER.deserialize(element);
        cache.put(name, loaded);
        return loaded;
    }

    public boolean doesConfigExistOnDisk(String selectedConfigName) {
        File saveFile = new File(storageFolder + "/" + selectedConfigName + ".json");
        return storageFolder.exists() && storageFolder.isDirectory() && saveFile.exists() && saveFile.isFile();
    }
}