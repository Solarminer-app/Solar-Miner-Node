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
    void createsBothDefaultConfigsOnFirstStart() throws Exception {
        Path storageDirectory = temporaryDirectory.resolve("not-created-yet").resolve("miner");

        MinerControllerConfigStorage storage = new MinerControllerConfigStorage(storageDirectory.toFile());

        assertTrue(Files.isDirectory(storageDirectory));
        assertTrue(Files.isRegularFile(storageDirectory.resolve("Standard.json")));
        assertTrue(Files.isRegularFile(storageDirectory.resolve("Economic.json")));
        assertEquals(java.util.Set.of("Standard", "Economic"), java.util.Set.copyOf(storage.getNameOfSavedConfigs()));
        var reloaded = new MinerControllerConfigStorage(storageDirectory.toFile());
        var economicMode = reloaded.get("Economic").getConfigEntries().values().iterator().next();
        assertEquals(de.verdox.pv_miner.miningcontroller.dsl.ControllerDSL.ControllerActionType.ECONOMIC_DISPATCH,
                economicMode.actions().getFirst().controllerActionType());
        assertEquals(de.verdox.pv_miner.miningcontroller.dsl.ControllerDSL.MinerDistributionStrategy.ECONOMIC_PERCENT,
                economicMode.actions().getFirst().strategy());
        assertEquals(10.0, ((de.verdox.pv_miner.miningcontroller.dsl.ControllerDSL.ValueExpression.EconomicThreshold)
                economicMode.actions().getFirst().valueExpression()).gridBatteryFloorPercent());
    }

    @Test
    void doesNotOverwriteAnExistingStandardConfigDuringRestart() throws Exception {
        Path storageDirectory = temporaryDirectory.resolve("miner");
        Files.createDirectories(storageDirectory);
        Path standardConfig = storageDirectory.resolve("Standard.json");
        Files.writeString(standardConfig, "existing-config-must-survive");

        new MinerControllerConfigStorage(storageDirectory.toFile());

        assertEquals("existing-config-must-survive", Files.readString(standardConfig));
        assertTrue(Files.isRegularFile(storageDirectory.resolve("Economic.json")));
    }
}
