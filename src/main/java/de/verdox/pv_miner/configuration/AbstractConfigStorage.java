package de.verdox.pv_miner.configuration;

import de.verdox.vserializer.generic.SerializationElement;
import de.verdox.vserializer.generic.Serializer;
import de.verdox.vserializer.json.JsonSerializerContext;
import lombok.Getter;
import org.apache.commons.compress.utils.FileNameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

public abstract class AbstractConfigStorage<T extends SimpleConfig<?>> {
    protected static final Logger LOGGER = Logger.getLogger(AbstractConfigStorage.class.getSimpleName());
    @Getter
    private final File storageFolder;
    private final Serializer<T> serializer;

    public AbstractConfigStorage(File storageFolder, Serializer<T> serializer) {
        this.storageFolder = storageFolder;
        this.serializer = serializer;
        initStorage();
    }

    protected List<String> getNameOfSavedFiles(File folder) throws IOException {
        if (!folder.isDirectory() || !folder.exists()) {
            return List.of();
        }
        try (Stream<Path> pathStream = Files.walk(folder.toPath(), 1)) {
            return pathStream.map(FileNameUtils::getBaseName).skip(1).toList();
        }
    }

    protected void save(File saveFile, T config) throws IOException {
        Path target = saveFile.toPath().toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("Config file has no parent directory: " + target);
        }
        Files.createDirectories(parent);
        JsonSerializerContext jsonSerializerContext = new JsonSerializerContext();
        SerializationElement element = serializer.serialize(jsonSerializerContext, config);
        Path temporaryFile = Files.createTempFile(parent, saveFile.getName() + ".", ".tmp");
        try {
            jsonSerializerContext.writeToFile(element, temporaryFile.toFile());
            try {
                Files.move(temporaryFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
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
            Files.createDirectories(storageFolder.toPath());
            if (!Files.isDirectory(storageFolder.toPath()) || !Files.isWritable(storageFolder.toPath())) {
                throw new IOException("Storage path is not a writable directory: " + storageFolder.getAbsolutePath());
            }
            LOGGER.info("Initialized storage at " + storageFolder.getAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize writable storage at " + storageFolder.getAbsolutePath(), e);
        }
    }
}
