package de.verdox.pv_miner_extensions.device.modbus;

import de.verdox.solarminer.modbustcp.ModbusConfig;
import de.verdox.solarminer.modbustcp.ModbusConfigCreatorTemplate;
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
    private final Map<String, ModbusConfig> cache = new HashMap<>();

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

    public List<String> getSavedConfigs() throws IOException {
        if (!storageFolder.isDirectory() || !storageFolder.exists()) {
            return List.of("");
        }
        try (Stream<Path> pathStream = Files.walk(storageFolder.toPath(), 1)) {
            return pathStream.map(FileNameUtils::getBaseName).skip(1).toList();
        }
    }

    public void save(String nameOfConfig, ModbusConfig modbusConfig) throws IOException {
        File saveFile = new File(storageFolder + "/" + nameOfConfig + ".json");
        storageFolder.mkdirs();
        JsonSerializerContext jsonSerializerContext = new JsonSerializerContext();
        SerializationElement element = ModbusConfig.SERIALIZER.serialize(jsonSerializerContext, modbusConfig);
        jsonSerializerContext.writeToFile(element, saveFile);
        LOGGER.info("Saved modbus config " + saveFile);
        cache.put(nameOfConfig, modbusConfig);
    }

    public boolean delete(String nameOfConfig) throws IOException {
        cache.remove(nameOfConfig);
        File saveFile = new File(storageFolder + "/" + nameOfConfig + ".json");
        if (!storageFolder.exists() || !storageFolder.isDirectory() || !saveFile.exists() || !saveFile.isFile()) {
            return false;
        }
        LOGGER.info("Deleted modbus config " + saveFile);
        return saveFile.delete();
    }

    public boolean doesConfigExistOnDisk(String name) {
        File saveFile = new File(storageFolder + "/" + name + ".json");
        return storageFolder.exists() && storageFolder.isDirectory() && saveFile.exists() && saveFile.isFile();
    }

    public ModbusConfig loadConfig(String name) throws IOException {
        if (cache.containsKey(name)) {
            return cache.get(name);
        }

        File saveFile = new File(storageFolder + "/" + name + ".json");
        if (!storageFolder.exists() || !storageFolder.isDirectory() || !saveFile.exists() || !saveFile.isFile()) {
            throw new NoSuchElementException("The modbus config "+name+" does not exist!");
        }
        LOGGER.info("Loading modbus config " + saveFile);
        JsonSerializerContext jsonSerializerContext = new JsonSerializerContext();
        SerializationElement element = jsonSerializerContext.readFromFile(saveFile);
        ModbusConfig loaded = ModbusConfig.SERIALIZER.deserialize(element);
        cache.put(name, loaded);
        return loaded;
    }
}