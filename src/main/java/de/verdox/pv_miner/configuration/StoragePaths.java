package de.verdox.pv_miner.configuration;

import java.io.File;
import java.nio.file.Path;

public final class StoragePaths {
    public static final String STORAGE_ROOT_PROPERTY = "solarminer.storage.root";
    public static final String STORAGE_ROOT_ENVIRONMENT_VARIABLE = "SOLARMINER_STORAGE_ROOT";

    private StoragePaths() {
    }

    public static File directory(String name) {
        return root().resolve(name).toFile();
    }

    public static Path root() {
        String configuredRoot = System.getProperty(STORAGE_ROOT_PROPERTY);
        if (configuredRoot == null || configuredRoot.isBlank()) {
            configuredRoot = System.getenv(STORAGE_ROOT_ENVIRONMENT_VARIABLE);
        }
        if (configuredRoot == null || configuredRoot.isBlank()) {
            configuredRoot = "./storage";
        }
        return Path.of(configuredRoot).toAbsolutePath().normalize();
    }
}
