package de.verdox.pv_miner.core.miner.twentyoneenergy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TwentyOneEnergyClientTest {
    @Test
    void buildsOnlyTheFixedLocalControlPath() {
        assertEquals("http://192.168.178.33:80/21control/", TwentyOneEnergyClient.baseUri("192.168.178.33", 80).toString());
    }

    @Test
    void refusesPublicTargetsAndInvalidPorts() {
        assertThrows(IllegalArgumentException.class, () -> TwentyOneEnergyClient.baseUri("8.8.8.8", 80));
        assertThrows(IllegalArgumentException.class, () -> TwentyOneEnergyClient.baseUri("192.168.1.4", 0));
    }
}
