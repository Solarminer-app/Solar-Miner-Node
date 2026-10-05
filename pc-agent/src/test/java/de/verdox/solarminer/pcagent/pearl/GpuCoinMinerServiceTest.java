package de.verdox.solarminer.pcagent.pearl;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

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

    @Test
    void startsDagAlgorithmsThroughSrbminersGpuOnlyParameter() {
        var config = new GpuCoinMinerService.Config("stratum+tcp://pool.example:5555",
                "stratum+tcp://127.0.0.1:3336", "RHUC17zAVjNqXDtkqwLPRvQ2XgoRZsXeeG",
                "pc", "NVIDIA:0");

        List<String> command = GpuCoinMinerService.buildCommand(
                Path.of("SRBMiner-MULTI"), "ravencoin", config, "encoded-login", 13000, 0);

        assertEquals("--algorithm-gpu", command.get(2));
        assertEquals("kawpow", command.get(3));
        assertFalse(command.contains("--algorithm"));

        List<String> etcCommand = GpuCoinMinerService.buildCommand(
                Path.of("SRBMiner-MULTI"), "ethereumclassic", config, "encoded-login", 14000, 0);
        assertEquals("--algorithm-gpu", etcCommand.get(2));
        assertEquals("etchash", etcCommand.get(3));
        assertFalse(etcCommand.contains("--algorithm"));
    }

    @Test
    void keepsAnActiveKawpowWorkerHealthyWhenTheCurrentJobIsOlder() {
        assertTrue(GpuCoinMinerService.hasUsablePoolJob(true, false, 31_000_000));
        assertTrue(GpuCoinMinerService.hasUsablePoolJob(true, true, 0));
        assertFalse(GpuCoinMinerService.hasUsablePoolJob(false, false, 31_000_000));
    }
}
