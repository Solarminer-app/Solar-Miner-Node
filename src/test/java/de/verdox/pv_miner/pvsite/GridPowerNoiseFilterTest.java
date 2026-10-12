package de.verdox.pv_miner.pvsite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GridPowerNoiseFilterTest {

    @Test
    void builderAppliesDeadbandToSignedAndDirectionalGridValues() {
        PVSiteDataDTO signedNoise = PVSiteDataDTO.builder().gridPower(0.03).build();
        PVSiteDataDTO threshold = PVSiteDataDTO.builder().importPowerKw(0.05).exportPowerKw(0.05).build();
        PVSiteDataDTO realImport = PVSiteDataDTO.builder().gridPower(0.051).build();

        assertEquals(0, signedNoise.getImportPowerKw());
        assertEquals(0, signedNoise.getExportPowerKw());
        assertEquals(0, threshold.getImportPowerKw());
        assertEquals(0, threshold.getExportPowerKw());
        assertEquals(0.051, realImport.getImportPowerKw());
    }
}
