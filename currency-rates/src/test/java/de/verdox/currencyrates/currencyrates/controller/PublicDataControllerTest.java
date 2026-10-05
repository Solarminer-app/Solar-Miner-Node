package de.verdox.currencyrates.currencyrates.controller;

import de.verdox.currencyrates.currencyrates.service.CoinGeckoPriceService;
import de.verdox.currencyrates.currencyrates.service.DataQueryService;
import de.verdox.currencyrates.currencyrates.service.MiningNetworkDataService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicDataControllerTest {
    @Test
    void publishesCoinKeyedMiningNetworkContractAndAliasLookup() throws Exception {
        MiningNetworkDataService networks = mock(MiningNetworkDataService.class);
        var snapshot = new MiningNetworkDataService.PublicSnapshot("ethereumclassic", "ETC", "ETCHash",
                true, false, 12, 140_334_070_841_501d, 1_964_747_158_816_436d,
                14d, 1.6384d, 8.89d, Instant.parse("2026-10-04T12:00:00Z"),
                "etc.2miners.com", "CoinGecko");
        when(networks.snapshots()).thenReturn(List.of(snapshot));
        when(networks.snapshot("ETC")).thenReturn(java.util.Optional.of(snapshot));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PublicDataController(
                mock(DataQueryService.class), mock(CoinGeckoPriceService.class), networks)).build();

        mvc.perform(get("/api/v1/public/mining-networks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].coin").value("ethereumclassic"))
                .andExpect(jsonPath("$[0].ticker").value("ETC"))
                .andExpect(jsonPath("$[0].blockReward").value(1.6384));
        mvc.perform(get("/api/v1/public/mining-networks/ETC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.networkSource").value("etc.2miners.com"));
    }
}
