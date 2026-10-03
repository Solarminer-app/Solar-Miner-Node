package de.verdox.solarminer.pcagent.pearl;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class GpuCoinMinerServiceTest {
    @Test
    void validatesWalletAndProxyRouteForBothAlgorithms() {
        String raven = "RHUC17zAVjNqXDtkqwLPRvQ2XgoRZsXeeG";
        String etc = "0x" + "a".repeat(40);
        assertDoesNotThrow(() -> GpuCoinMinerService.validate("ravencoin",
                new GpuCoinMinerService.Config("stratum+tcp://pool.example:3333",
                        "stratum+tcp://127.0.0.1:3336", raven, "pc", "NVIDIA:0")));
        assertDoesNotThrow(() -> GpuCoinMinerService.validate("ethereumclassic",
                new GpuCoinMinerService.Config("stratum+ssl://pool.example:5555",
                        "stratum+tcp://127.0.0.1:3337", etc, "pc", "NVIDIA:0,AMD:1")));
        assertThrows(IllegalArgumentException.class, () -> GpuCoinMinerService.validate("ravencoin",
                new GpuCoinMinerService.Config("stratum+tcp://pool.example:3333",
                        "stratum+tcp://127.0.0.1:3336", etc, "pc", "NVIDIA:0")));
        assertFalse(GpuCoinMinerService.validRavencoinAddress(raven.substring(0, raven.length() - 1) + "H"));
        assertThrows(IllegalArgumentException.class, () -> GpuCoinMinerService.validate("ethereumclassic",
                new GpuCoinMinerService.Config("stratum+tcp://pool.example:3333",
                        "stratum+tcp://127.0.0.1:3337", etc, "pc", "all")));
    }

    @Test
    void solarMinerHouseAddressesPassChainFormatChecks() {
        assertTrue(GpuCoinMinerService.validRavencoinAddress("RHaGK3iARQdKgZ6VPDP4N5chP3aVgUUfz7"));
        assertDoesNotThrow(() -> GpuCoinMinerService.validate("ethereumclassic",
                new GpuCoinMinerService.Config("stratum+tcp://etc.2miners.com:1010",
                        "stratum+tcp://127.0.0.1:3337",
                        "0x21211c699D409Ca3802D955caD80Ccc034004993", "solarminer", "NVIDIA:0")));
    }

    @Test
    void encodesPoolInWalletLoginUsedBySrbminer() {
        var config = new GpuCoinMinerService.Config("stratum+ssl://pool.example:5555",
                "stratum+tcp://127.0.0.1:3337", "0x" + "a".repeat(40), "pc", "NVIDIA:0");
        String login = GpuCoinMinerService.encodedLogin(config, "pc-n0");
        String[] fields = login.split("\\.", 4);
        assertEquals(config.wallet(), fields[0]);
        assertEquals("sm1", fields[1]);
        assertEquals(config.poolUrl(), new String(Base64.getUrlDecoder().decode(fields[2]), StandardCharsets.UTF_8));
        assertEquals("pc-n0", fields[3]);
    }
}
