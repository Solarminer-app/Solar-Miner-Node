package de.verdox.solarminer.pcagent.controller;

import de.verdox.solarminer.pcagent.lowlevel.sensor.WindowsLhmBootstrapService;
import de.verdox.solarminer.pcagent.mining.MiningService;
import de.verdox.solarminer.pcagent.mining.EarningsForecastService;
import de.verdox.solarminer.pcagent.mining.PayoutDefaultsService;
import de.verdox.solarminer.pcagent.mining.ReferralConfigurationService;
import de.verdox.solarminer.pcagent.mining.FeeTransparencyService;
import de.verdox.solarminer.pcagent.mining.WalletBalanceService;
import de.verdox.solarminer.pcagent.mining.ProxyConfigurationService;
import de.verdox.solarminer.pcagent.mining.ProxyDiscoveryService;
import de.verdox.solarminer.pcagent.pearl.SrbDownloadService;
import de.verdox.solarminer.pcagent.pearl.LocalGpuPowerService;
import de.verdox.solarminer.pcagent.pearl.PearlMinerService;
import de.verdox.solarminer.pcagent.xmr.XmrConfigService;
import de.verdox.solarminer.pcagent.xmr.XmrMinerService;
import de.verdox.solarminer.pcagent.xmr.download.XmrDownloadService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class MiningControllerValidationTest {
    private final WindowsLhmBootstrapService sensors = mock(WindowsLhmBootstrapService.class);
    private final PearlMinerService pearl = mock(PearlMinerService.class);
    private final XmrConfigService xmrConfig = mock(XmrConfigService.class);
    private final ProxyConfigurationService proxy = mock(ProxyConfigurationService.class);
    private final PayoutDefaultsService payouts = mock(PayoutDefaultsService.class);

    MiningControllerValidationTest() {
        when(sensors.readyForAgent()).thenReturn(true);
    }

    private MockMvc controller() {
        return standaloneSetup(new MiningController(mock(MiningService.class), xmrConfig, pearl,
                mock(LocalGpuPowerService.class), mock(XmrMinerService.class), proxy, mock(SrbDownloadService.class),
                mock(XmrDownloadService.class), sensors, mock(ProxyDiscoveryService.class),
                mock(EarningsForecastService.class), payouts, mock(ReferralConfigurationService.class),
                mock(FeeTransparencyService.class), mock(WalletBalanceService.class))).build();
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

    @Test
    void emptyPearlWalletWithoutFeeBackendPayoutIsRefusedWithAReason() throws Exception {
        when(payouts.resolve("pearl")).thenReturn(Optional.empty());
        controller().perform(post("/api/agent/pearl/configuration").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"poolUrl":"","proxyUrl":"stratum+tcp://127.0.0.1:3334",
                                 "wallet":"","worker":"pc","devices":"all"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Standard-Auszahlungsziel")));
    }

    @Test
    void emptyPearlWalletStoresTheFeeBackendRouteTogether() throws Exception {
        String houseWallet = "prl1" + "q".repeat(30);
        when(payouts.resolve("pearl")).thenReturn(Optional.of(new PayoutDefaultsService.DefaultPayout("pearl",
                "solarminer-prl-pearlhash", "stratum+ssl://prl.kryptex.network:8048", houseWallet + "/solarminer", "")));
        when(proxy.matches("stratum+tcp://127.0.0.1:3334", "pearl")).thenReturn(true);
        controller().perform(post("/api/agent/pearl/configuration").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"poolUrl":"","proxyUrl":"stratum+tcp://127.0.0.1:3334",
                                 "wallet":"","worker":"pc","devices":"all"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));

        ArgumentCaptor<PearlMinerService.Config> saved = ArgumentCaptor.forClass(PearlMinerService.Config.class);
        verify(pearl).configure(saved.capture());
        assertEquals("stratum+ssl://prl.kryptex.network:8048", saved.getValue().poolUrl());
        assertEquals(houseWallet, saved.getValue().wallet());
        assertEquals("solarminer", saved.getValue().worker());
        verify(payouts).markDefault("pearl", true);
    }

    @Test
    void emptyMoneroWalletRoutesToTheFeeBackendPoolAndWorker() throws Exception {
        when(proxy.moneroUrl()).thenReturn("stratum+tcp://127.0.0.1:3335");
        when(payouts.resolve("monero")).thenReturn(Optional.of(new PayoutDefaultsService.DefaultPayout("monero",
                "solarminer-xmr-randomx", "stratum+tcp://xmr.kryptex.network:7029", "4HouseWallet.solarminer", "")));
        controller().perform(post("/api/agent/monero/configuration").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"poolUrl":"","wallet":"","worker":"pc"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));

        verify(xmrConfig).configureXmrig(eq(XmrDownloadService.CONFIG_PATH), eq("stratum+tcp://127.0.0.1:3335"),
                eq("stratum+tcp://xmr.kryptex.network:7029;4HouseWallet.solarminer;x"), eq(false));
        verify(payouts).markDefault("monero", true);
    }

    @Test
    void ownMoneroWalletStillKeepsItsOwnPoolAndWorker() throws Exception {
        when(proxy.moneroUrl()).thenReturn("stratum+tcp://127.0.0.1:3335");
        controller().perform(post("/api/agent/monero/configuration").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"poolUrl":"stratum+tcp://xmr-eu.kryptex.network:7029",
                                 "wallet":"4AdUndXHHZ6cfufTMvppY6JwXNouMBzSkbLYfpAV5Usx3skxNgYeYTRj5UzqtReoS44qo9mtmXCqY45DJ852K5Jv2684Rge",
                                 "worker":"pc"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));

        verify(xmrConfig).configureXmrig(any(), eq("stratum+tcp://127.0.0.1:3335"),
                eq("stratum+tcp://xmr-eu.kryptex.network:7029;4AdUndXHHZ6cfufTMvppY6JwXNouMBzSkbLYfpAV5Usx3skxNgYeYTRj5UzqtReoS44qo9mtmXCqY45DJ852K5Jv2684Rge.pc;x"),
                eq(false));
        verify(payouts).markDefault("monero", false);
    }
}
