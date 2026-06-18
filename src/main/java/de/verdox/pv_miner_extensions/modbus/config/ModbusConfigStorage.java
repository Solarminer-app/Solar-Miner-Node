package de.verdox.pv_miner_extensions.modbus.config;

import de.verdox.vserializer.generic.SerializationElement;
import de.verdox.vserializer.json.JsonSerializerContext;
import org.apache.commons.compress.utils.FileNameUtils;
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
public class ModbusConfigStorage {
    private static final Logger LOGGER = Logger.getLogger(ModbusConfigStorage.class.getSimpleName());
    private final File storageFolder = new File("./storage/modbus/");
    private final Map<ModbusConfigCreatorTemplate, Map<String, ModbusConfig>> cache = new HashMap<>();

    public ModbusConfigStorage() {
        initStorage();
    }

    private void initStorage() {
        LOGGER.info("Initialize storage at " + storageFolder.getAbsolutePath());
        try {
            if (!storageFolder.mkdirs() && !storageFolder.isDirectory()) {
                LOGGER.warning("Could not initialize storage at " + storageFolder.getAbsolutePath());
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not initialize storage at " + storageFolder.getAbsolutePath(), e);
        }
    }

    public List<String> getSavedConfigs(ModbusConfigCreatorTemplate modbusConfigCreatorTemplate) throws IOException {
        File folderOfTemplate = getFolderOfTemplate(modbusConfigCreatorTemplate);
        if (!folderOfTemplate.isDirectory() || !folderOfTemplate.exists()) {
            return List.of("");
        }
        try (Stream<Path> pathStream = Files.walk(folderOfTemplate.toPath(), 1)) {
            return pathStream.map(FileNameUtils::getBaseName).skip(1).toList();
        }
    }

    public void save(ModbusConfigCreatorTemplate templateType, String nameOfConfig, ModbusConfig modbusConfig) throws IOException {
        File folderOfTemplate = getFolderOfTemplate(templateType);
        File saveFile = new File(folderOfTemplate + "/" + nameOfConfig + ".json");
        folderOfTemplate.mkdirs();
        JsonSerializerContext jsonSerializerContext = new JsonSerializerContext();
        SerializationElement element = ModbusConfig.SERIALIZER.serialize(jsonSerializerContext, modbusConfig);
        jsonSerializerContext.writeToFile(element, saveFile);
        LOGGER.info("Saved modbus config " + saveFile);
        cache.computeIfAbsent(templateType, template -> new HashMap<>()).put(nameOfConfig, modbusConfig);
    }

    public boolean delete(ModbusConfigCreatorTemplate templateType, String nameOfConfig) throws IOException {
        if (cache.containsKey(templateType)) {
            cache.get(templateType).remove(nameOfConfig);
        }

        File folderOfTemplate = getFolderOfTemplate(templateType);
        File saveFile = new File(folderOfTemplate + "/" + nameOfConfig + ".json");
        if (!folderOfTemplate.exists() || !folderOfTemplate.isDirectory() || !saveFile.exists() || !saveFile.isFile()) {
            return false;
        }
        LOGGER.info("Deleted modbus config " + saveFile);
        return saveFile.delete();
    }

    public boolean doesConfigExistOnDisk(ModbusConfigCreatorTemplate templateType, String name) {
        File folderOfTemplate = getFolderOfTemplate(templateType);
        File saveFile = new File(folderOfTemplate + "/" + name + ".json");
        return folderOfTemplate.exists() && folderOfTemplate.isDirectory() && saveFile.exists() && saveFile.isFile();
    }

    public ModbusConfig loadConfig(ModbusConfigCreatorTemplate templateType, String name) throws IOException {
        if (cache.containsKey(templateType) && cache.get(templateType).containsKey(name)) {
            return cache.get(templateType).get(name);
        }

        File folderOfTemplate = getFolderOfTemplate(templateType);
        File saveFile = new File(folderOfTemplate + "/" + name + ".json");
        if (!folderOfTemplate.exists() || !folderOfTemplate.isDirectory() || !saveFile.exists() || !saveFile.isFile()) {
            throw new NoSuchElementException("The modbus config "+name+" does not exist!");
        }
        LOGGER.info("Loading modbus config " + saveFile);
        JsonSerializerContext jsonSerializerContext = new JsonSerializerContext();
        SerializationElement element = jsonSerializerContext.readFromFile(saveFile);
        ModbusConfig loaded = ModbusConfig.SERIALIZER.deserialize(element);
        cache.computeIfAbsent(templateType, template -> new HashMap<>()).put(name, loaded);
        return loaded;
    }

    private File getFolderOfTemplate(ModbusConfigCreatorTemplate templateType) {
        return new File(storageFolder + "/" + templateType.id());
    }
}
