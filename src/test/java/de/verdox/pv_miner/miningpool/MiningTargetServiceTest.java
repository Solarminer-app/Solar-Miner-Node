package de.verdox.pv_miner.miningpool;

import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.MinerRepository;
import de.verdox.pv_miner.miner.MiningOS;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import de.verdox.pv_miner_extensions.miner.AntminerEntity;
import de.verdox.pv_miner_extensions.miner.AgentMinerEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

class MiningTargetServiceTest {
    @Test
    void sendsIndependentCoinTargetsToAgentAndKeepsGpuSelection() {
        UUID siteId = UUID.randomUUID();
        PVSiteRepository sites = mock(PVSiteRepository.class);
        MiningTargetRepository targets = mock(MiningTargetRepository.class);
        MinerApiClient api = mock(MinerApiClient.class);
        PVSiteEntity site = mock(PVSiteEntity.class);
        AgentMinerEntity agent = new AgentMinerEntity();
        agent.setId(UUID.fromString("12345678-0000-4000-8000-000000000001"));
        agent.setHost("192.0.2.10");
        agent.setPort(8084);
        when(site.getMiners()).thenReturn(Set.of(agent));
        when(sites.findById(siteId)).thenReturn(Optional.of(site));
        MiningTargetEntity monero = target(siteId, 1, "stratum+tcp://xmr.example:3333");
        monero.setCoin("monero");
        monero.setAlgorithm("RandomX");
        monero.setWorkerPrefix("cpu");
        monero.setPayoutAddress("xmr-wallet");
        MiningTargetEntity pearl = target(siteId, 1, "stratum+ssl://prl.example:8048");
        pearl.setCoin("pearl");
        pearl.setAlgorithm("PearlHash");
        pearl.setWorkerPrefix("gpu");
        pearl.setPayoutAddress("prl-wallet");
        when(targets.findBySiteIdOrderByPriorityAsc(siteId)).thenReturn(List.of(monero, pearl));
        when(api.getAgentCoinConfigurations(agent.getDetails())).thenReturn(new MinerApiClient.AgentCoinConfigurations(
                null, new MinerApiClient.CoinRoute("old-pool", "old-wallet", "old-worker", "NVIDIA:1")));
        when(api.configureMonero(eq(agent.getDetails()), anyString(), anyString(), anyString(), isNull())).thenReturn(true);
        when(api.configurePearl(eq(agent.getDetails()), anyString(), anyString(), anyString(), anyString(), isNull())).thenReturn(true);

        new MiningTargetService(targets, sites, mock(MinerRepository.class), api, mock(EntityQueryService.class))
                .apply(siteId, true);

        verify(api).configureMonero(agent.getDetails(), monero.getStratumUrl(), "xmr-wallet", "cpu-12345678", null);
        verify(api).configurePearl(agent.getDetails(), pearl.getStratumUrl(), "prl-wallet", "gpu-12345678", "NVIDIA:1", null);
        verify(api, never()).setMiningPoolTarget(any(), any(), anyString(), anyString(), any(), any());
    }

    @Test
    void doesNotRouteMoneroOrPearlTargetsToBitcoinAsic() {
        UUID siteId = UUID.randomUUID();
        PVSiteRepository sites = mock(PVSiteRepository.class);
        MiningTargetRepository targets = mock(MiningTargetRepository.class);
        MinerRepository miners = mock(MinerRepository.class);
        MinerApiClient api = mock(MinerApiClient.class);
        EntityQueryService queries = mock(EntityQueryService.class);
        PVSiteEntity site = mock(PVSiteEntity.class);
        AntminerEntity asic = new AntminerEntity();
        asic.setId(UUID.randomUUID());
        when(site.getMiners()).thenReturn(Set.of(asic));
        when(sites.findById(siteId)).thenReturn(Optional.of(site));
        MiningTargetEntity monero = target(siteId, 1, "stratum+tcp://xmr.example:3333");
        monero.setCoin("monero");
        monero.setAlgorithm("RandomX");
        MiningTargetEntity pearl = target(siteId, 1, "stratum+tcp://prl.example:7048");
        pearl.setCoin("pearl");
        pearl.setAlgorithm("PearlHash");
        when(targets.findBySiteIdOrderByPriorityAsc(siteId)).thenReturn(List.of(monero, pearl));

        new MiningTargetService(targets, sites, miners, api, queries).apply(siteId, true);

        verify(api, never()).setMiningPoolTarget(any(), any(), anyString(), anyString(), any(), any());
    }

    @Test
    void triesFallbackForAsicAndLeavesAgentAlone() {
        UUID siteId = UUID.randomUUID();
        PVSiteRepository sites = mock(PVSiteRepository.class);
        MiningTargetRepository targets = mock(MiningTargetRepository.class);
        MinerRepository miners = mock(MinerRepository.class);
        MinerApiClient api = mock(MinerApiClient.class);
        EntityQueryService queries = mock(EntityQueryService.class);
        PVSiteEntity site = mock(PVSiteEntity.class);
        AntminerEntity asic = new AntminerEntity();
        asic.setId(UUID.randomUUID());
        asic.setHost("192.0.2.10");
        MinerEntity<?> agent = mock(MinerEntity.class);
        when(agent.getOS()).thenReturn(MiningOS.AGENT);
        when(site.getMiners()).thenReturn(Set.of(asic, agent));
        when(sites.findById(siteId)).thenReturn(Optional.of(site));

        MiningTargetEntity primary = target(siteId, 1, "stratum+tcp://primary.example:3333");
        MiningTargetEntity fallback = target(siteId, 2, "stratum+tcp://backup.example:3333");
        when(targets.findBySiteIdOrderByPriorityAsc(siteId)).thenReturn(List.of(primary, fallback));
        when(queries.getLastResult(asic, MinerStats.DEFAULT)).thenReturn(MinerStats.DEFAULT);
        when(api.setMiningPoolTarget(eq(MiningOS.ANTMINER_STOCK_OS), any(), anyString(), anyString(),
                any(MinerStats.MinerIdentity.class), isNull())).thenReturn(false, true);

        new MiningTargetService(targets, sites, miners, api, queries).apply(siteId, false);

        assertEquals(fallback.getStratumUrl(), asic.getCurrentMiningPoolTarget());
        verify(api, times(2)).setMiningPoolTarget(eq(MiningOS.ANTMINER_STOCK_OS), any(), anyString(), anyString(),
                any(MinerStats.MinerIdentity.class), isNull());
        verify(miners).save(asic);
    }

    private static MiningTargetEntity target(UUID siteId, int priority, String url) {
        MiningTargetEntity target = new MiningTargetEntity();
        target.setSiteId(siteId);
        target.setAlgorithm("SHA256");
        target.setPriority(priority);
        target.setStratumUrl(url);
        target.setWorkerPrefix("account.");
        target.setEnabled(true);
        return target;
    }
}
