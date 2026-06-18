package de.verdox.pv_miner.configuration;

import de.verdox.vserializer.generic.SerializationElement;
import de.verdox.vserializer.generic.Serializer;
import de.verdox.vserializer.json.JsonSerializerContext;
import org.apache.commons.compress.utils.FileNameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public abstract class AbstractConfigStorage<T extends SimpleConfig<?>> {
    protected static final Logger LOGGER = Logger.getLogger(AbstractConfigStorage.class.getSimpleName());
    private final File storageFolder;
    private final Serializer<T> serializer;

    public AbstractConfigStorage(File storageFolder, Serializer<T> serializer) {
        this.storageFolder = storageFolder;
        this.serializer = serializer;
        initStorage();
    }

    public File getStorageFolder() {
        return storageFolder;
    }

    protected List<String> getNameOfSavedFiles(File folder) throws IOException {
        if (!folder.isDirectory() || !folder.exists()) {
            return List.of("");
        }
        try (Stream<Path> pathStream = Files.walk(folder.toPath(), 1)) {
            return pathStream.map(FileNameUtils::getBaseName).skip(1).toList();
        }
    }

    protected void save(File saveFile, T config) throws IOException {
        saveFile.getParentFile().mkdirs();
        JsonSerializerContext jsonSerializerContext = new JsonSerializerContext();
        SerializationElement element = serializer.serialize(jsonSerializerContext, config);
        jsonSerializerContext.writeToFile(element, saveFile);
        LOGGER.info("Saved config " + saveFile);
    }

    protected T load(File saveFile) throws IOException {
        if (!saveFile.exists() || !saveFile.isFile()) {
            return createDefaultValue();
        }
        LOGGER.info("Loading config " + saveFile);
        JsonSerializerContext jsonSerializerContext = new JsonSerializerContext();
        SerializationElement element = jsonSerializerContext.readFromFile(saveFile);
        return serializer.deserialize(element);
    }

    protected boolean delete(File saveFile) {
        if (!saveFile.exists() || !saveFile.isFile()) {
            return false;
        }
        LOGGER.info("Deleted config " + saveFile);
        return saveFile.delete();
    }

    protected abstract T createDefaultValue();

    private void initStorage() {
        LOGGER.info("Initialize storage at " + storageFolder.getAbsolutePath());
        try {
            if (!storageFolder.mkdirs() && !storageFolder.isDirectory()) {
                LOGGER.warning("Could not initialize storage at " + storageFolder.getAbsolutePath()+". Maybe the folder already exists? Well then everything is ok :)");
            }
            else {
                LOGGER.warning("Initialized storage at " + storageFolder.getAbsolutePath());
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not initialize storage at " + storageFolder.getAbsolutePath(), e);
        }
    }

    /**
     * Used in UI to access the repository
     *
     * @param <T> the config type
     * @param <C> the config storage type
     */
    public interface UIConfigStorageAccessor<T extends SimpleConfig<?>, C extends AbstractConfigStorage<T>> {
        Stream<String> loadAvailableConfigNames(C storage);

        T loadFromStorage(C storage, String name) throws IOException;

        boolean delete(C storage, String name) throws IOException;

        void save(C storage, String name, T config) throws IOException;
    }
}
