package de.verdox.pv_miner_extensions.inverter.rest.config;

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
    private final Map<RestConfigCreatorTemplate, Map<String, RestPVConfig>> cache = new HashMap<>();

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

    public List<String> getSavedConfigs(RestConfigCreatorTemplate restConfigCreatorTemplate) throws IOException {
        File folderOfTemplate = getFolderOfTemplate(restConfigCreatorTemplate);
        if (!folderOfTemplate.isDirectory() || !folderOfTemplate.exists()) {
            return List.of("");
        }
        try (Stream<Path> pathStream = Files.walk(folderOfTemplate.toPath(), 1)) {
            return pathStream.map(FileNameUtils::getBaseName).skip(1).toList();
        }
    }

    public void save(RestConfigCreatorTemplate templateType, String nameOfConfig, RestPVConfig restConfig) throws IOException {
        File folderOfTemplate = getFolderOfTemplate(templateType);
        File saveFile = new File(folderOfTemplate + "/" + nameOfConfig + ".json");
        folderOfTemplate.mkdirs();
        JsonSerializerContext jsonSerializerContext = new JsonSerializerContext();
        SerializationElement element = RestPVConfig.SERIALIZER.serialize(jsonSerializerContext, restConfig);
        jsonSerializerContext.writeToFile(element, saveFile);
        LOGGER.info("Saved REST config " + saveFile);
        cache.computeIfAbsent(templateType, template -> new HashMap<>()).put(nameOfConfig, restConfig);
    }

    public boolean delete(RestConfigCreatorTemplate templateType, String nameOfConfig) throws IOException {
        if (cache.containsKey(templateType)) {
            cache.get(templateType).remove(nameOfConfig);
        }

        File folderOfTemplate = getFolderOfTemplate(templateType);
        File saveFile = new File(folderOfTemplate + "/" + nameOfConfig + ".json");
        if (!folderOfTemplate.exists() || !folderOfTemplate.isDirectory() || !saveFile.exists() || !saveFile.isFile()) {
            return false;
        }
        LOGGER.info("Deleted REST config " + saveFile);
        return saveFile.delete();
    }

    public RestPVConfig loadConfig(RestConfigCreatorTemplate templateType, String name) throws IOException {
        if (cache.containsKey(templateType) && cache.get(templateType).containsKey(name)) {
            return cache.get(templateType).get(name);
        }

        File folderOfTemplate = getFolderOfTemplate(templateType);
        File saveFile = new File(folderOfTemplate + "/" + name + ".json");
        if (!folderOfTemplate.exists() || !folderOfTemplate.isDirectory() || !saveFile.exists() || !saveFile.isFile()) {
            throw new NoSuchElementException("The rest config "+name+" does not exist!");
        }
        LOGGER.info("Loading REST config " + saveFile);
        JsonSerializerContext jsonSerializerContext = new JsonSerializerContext();
        SerializationElement element = jsonSerializerContext.readFromFile(saveFile);
        RestPVConfig loaded = RestPVConfig.SERIALIZER.deserialize(element);
        cache.computeIfAbsent(templateType, template -> new HashMap<>()).put(name, loaded);
        return loaded;
    }

    public boolean doesConfigExistOnDisk(RestConfigCreatorTemplate templateType, String selectedConfigName) {
        File folderOfTemplate = getFolderOfTemplate(templateType);
        File saveFile = new File(folderOfTemplate + "/" + selectedConfigName + ".json");
        return folderOfTemplate.exists() && folderOfTemplate.isDirectory() && saveFile.exists() && saveFile.isFile();
    }

    private File getFolderOfTemplate(RestConfigCreatorTemplate templateType) {
        return new File(storageFolder + "/" + templateType.id());
    }
}