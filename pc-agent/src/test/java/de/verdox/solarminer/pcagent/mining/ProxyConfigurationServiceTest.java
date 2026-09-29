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

    private static ProxyConfigurationService proxy(Path file) {
        return new ProxyConfigurationService(new ObjectMapper(), new ManagedProxyService(false, "./lib/proxy.jar"),
                file.toString(), 3335, 3334, 8090, false);
    }
}
