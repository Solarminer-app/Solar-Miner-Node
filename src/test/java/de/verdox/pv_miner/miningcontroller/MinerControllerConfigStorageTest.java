package de.verdox.pv_miner.miningcontroller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinerControllerConfigStorageTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsNestedStorageAndTheStandardConfigOnFirstStart() throws Exception {
        Path storageDirectory = temporaryDirectory.resolve("not-created-yet").resolve("miner");

        MinerControllerConfigStorage storage = new MinerControllerConfigStorage(storageDirectory.toFile());

        assertTrue(Files.isDirectory(storageDirectory));
        assertTrue(Files.isRegularFile(storageDirectory.resolve("Standard.json")));
        assertEquals(java.util.List.of("Standard"), storage.getNameOfSavedConfigs());
    }

    @Test
    void doesNotOverwriteAnExistingStandardConfigDuringRestart() throws Exception {
        Path storageDirectory = temporaryDirectory.resolve("miner");
        Files.createDirectories(storageDirectory);
        Path standardConfig = storageDirectory.resolve("Standard.json");
        Files.writeString(standardConfig, "existing-config-must-survive");

        new MinerControllerConfigStorage(storageDirectory.toFile());

        assertEquals("existing-config-must-survive", Files.readString(standardConfig));
    }
}
