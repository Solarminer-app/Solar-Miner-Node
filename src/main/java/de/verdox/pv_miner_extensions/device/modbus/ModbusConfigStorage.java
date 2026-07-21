package de.verdox.pv_miner_extensions.device.modbus;

import de.verdox.pv_miner.configfetcher.ConfigFetcherService;
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
    private final ConfigFetcherService configFetcherService;

    public ModbusConfigStorage(ConfigFetcherService configFetcherService) {
        this.configFetcherService = configFetcherService;
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
        return findConfigFile(name) != null;
    }

    public ModbusConfig loadConfig(String name) throws IOException {
        if (cache.containsKey(name)) {
            return cache.get(name);
        }

        File saveFile = findConfigFile(name);
        if (saveFile == null) {
            ModbusConfig bundled = configFetcherService.getModbusConfig(name).orElse(null);
            if (bundled != null) {
                cache.put(name, bundled);
                return bundled;
            }
            throw new NoSuchElementException("The modbus config "+name+" does not exist!");
        }
        LOGGER.info("Loading modbus config " + saveFile);
        JsonSerializerContext jsonSerializerContext = new JsonSerializerContext();
        SerializationElement element = jsonSerializerContext.readFromFile(saveFile);
        boolean legacy = Files.readString(saveFile.toPath()).contains("\"entries\"")
                && !Files.readString(saveFile.toPath()).contains("\"sections\"");
        ModbusConfig loaded = (legacy ? ModbusConfig.LEGACY_SERIALIZER : ModbusConfig.SERIALIZER).deserialize(element);
        cache.put(name, loaded);
        return loaded;
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
