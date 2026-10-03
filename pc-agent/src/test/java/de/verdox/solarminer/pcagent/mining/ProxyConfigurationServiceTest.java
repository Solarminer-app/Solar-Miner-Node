package de.verdox.solarminer.pcagent.mining;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.solarminer.pcagent.xmr.XmrConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProxyConfigurationServiceTest {
    @TempDir Path directory;

    @Test
    void acceptsOnlyTheConfiguredSolarMinerProxyPortForEachCoin() {
        Path file = directory.resolve("proxy-host.txt");
        ProxyConfigurationService proxy = proxy(file);

        assertFalse(proxy.matches("stratum+tcp://node.lan:3335", "monero"));
        assertFalse(proxy.configure("stratum+tcp://node.lan:3335"));
        assertTrue(proxy.configure("node.lan"));
        assertTrue(proxy.matches("stratum+tcp://node.lan:3335", "monero"));
        assertTrue(proxy.matches("stratum+tcp://node.lan:3334", "pearl"));
        assertTrue(proxy.matches("stratum+tcp://node.lan:3336", "ravencoin"));
        assertTrue(proxy.matches("stratum+tcp://node.lan:3337", "ethereumclassic"));
        assertFalse(proxy.matches("stratum+tcp://node.lan:3334", "ravencoin"));
        assertFalse(proxy.matches("stratum+tcp://pool.example:3335", "monero"));
        assertFalse(proxy.matches("stratum+tcp://node.lan:3334", "monero"));
        assertFalse(proxy.matches("stratum+ssl://node.lan:3335", "monero"));
        assertFalse(proxy.matches("stratum+tcp://node.lan:3335/other", "monero"));

        ProxyConfigurationService reloaded = proxy(file);
        assertEquals("node.lan", reloaded.host());
        assertTrue(reloaded.matches("stratum+tcp://node.lan:3335", "monero"));
    }

    @Test
    void refusesAnXmrigConfigurationThatTargetsAPoolDirectly() {
        ProxyConfigurationService proxy = proxy(directory.resolve("proxy-host.txt"));
        assertTrue(proxy.configure("node.lan"));
        XmrConfigService config = new XmrConfigService(new ObjectMapper(), proxy);
        assertThrows(IllegalArgumentException.class, () -> config.configureXmrig(directory.resolve("config.json"),
                "stratum+tcp://pool.example:9200", "pool.example:9200;wallet;x", false));
    }

    @Test
    void externalModeOverridesAndPersistsTheLegacyStandaloneDefault() {
        Path file = directory.resolve("proxy-host.txt");
        ProxyConfigurationService proxy = proxy(file, true);
        assertTrue(proxy.standalone());

        assertTrue(proxy.setMode("external"));
        assertFalse(proxy.standalone());

        ProxyConfigurationService reloaded = proxy(file, true);
        assertFalse(reloaded.standalone());
    }

    private static ProxyConfigurationService proxy(Path file) {
        return proxy(file, false);
    }

    private static ProxyConfigurationService proxy(Path file, boolean standalone) {
        return new ProxyConfigurationService(new ObjectMapper(), new ManagedProxyService(false, "./lib/proxy.jar"),
                new ReferralConfigurationService(file.resolveSibling("referral-key.txt").toString()),
                file.toString(), 3335, 3334, 3336, 3337, 8090, file.resolveSibling("proxy-mode.txt").toString(), standalone);
    }
}
