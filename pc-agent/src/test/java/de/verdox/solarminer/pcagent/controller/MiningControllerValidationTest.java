package de.verdox.solarminer.pcagent.controller;

import de.verdox.solarminer.pcagent.lowlevel.sensor.WindowsLhmBootstrapService;
import de.verdox.solarminer.pcagent.mining.MiningService;
import de.verdox.solarminer.pcagent.mining.EarningsForecastService;
import de.verdox.solarminer.pcagent.mining.ProxyConfigurationService;
import de.verdox.solarminer.pcagent.mining.ProxyDiscoveryService;
import de.verdox.solarminer.pcagent.pearl.SrbDownloadService;
import de.verdox.solarminer.pcagent.pearl.LocalGpuPowerService;
import de.verdox.solarminer.pcagent.pearl.PearlMinerService;
import de.verdox.solarminer.pcagent.xmr.XmrConfigService;
import de.verdox.solarminer.pcagent.xmr.XmrMinerService;
import de.verdox.solarminer.pcagent.xmr.download.XmrDownloadService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class MiningControllerValidationTest {
    private MockMvc controller() {
        WindowsLhmBootstrapService sensors = mock(WindowsLhmBootstrapService.class);
        when(sensors.readyForAgent()).thenReturn(true);
        MiningController controller = new MiningController(mock(MiningService.class), mock(XmrConfigService.class),
                mock(PearlMinerService.class), mock(LocalGpuPowerService.class), mock(XmrMinerService.class),
                mock(ProxyConfigurationService.class), mock(SrbDownloadService.class), mock(XmrDownloadService.class),
                sensors, mock(ProxyDiscoveryService.class), mock(EarningsForecastService.class));
        return standaloneSetup(controller).build();
    }

    @Test
    void invalidPearlWalletReturnsReadableBadRequest() throws Exception {
        controller().perform(post("/api/agent/pearl/configuration").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"poolUrl":"stratum+ssl://prl.kryptex.network:8048",
                                 "proxyUrl":"stratum+tcp://127.0.0.1:3334",
                                 "wallet":"invalid","worker":"pc","devices":"all"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A Pearl prl1 payout address is required"));
    }

    @Test
    void missingPearlProxyReturnsReadableBadRequest() throws Exception {
        String wallet = "prl1" + "q".repeat(30);
        controller().perform(post("/api/agent/pearl/configuration").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"poolUrl":"stratum+ssl://prl.kryptex.network:8048",
                                 "proxyUrl":"stratum+tcp://127.0.0.1:3334",
                                 "wallet":"%s","worker":"pc","devices":"all"}
                                """.formatted(wallet)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("SolarMiner-Proxy")));
    }
}
