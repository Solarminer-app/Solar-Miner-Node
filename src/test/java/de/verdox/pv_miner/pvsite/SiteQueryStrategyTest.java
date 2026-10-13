package de.verdox.pv_miner.pvsite;

import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.data.MinerStats;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SiteQueryStrategyTest {
    @Test
    void includesAssignedAndUnassignedMinerPowerInSiteStatistics() throws Throwable {
        EntityQueryService queries = mock(EntityQueryService.class);
        PVSiteEntity site = mock(PVSiteEntity.class);
        MinerEntity<?> assigned = mock(MinerEntity.class);
        MinerEntity<?> unassigned = mock(MinerEntity.class);
        when(assigned.getClusterName()).thenReturn("Primary");
        when(unassigned.getClusterName()).thenReturn(null);
        when(site.getMiners()).thenReturn(Set.of(assigned, unassigned));
        when(site.getBatteries()).thenReturn(Set.of());
        when(site.getInverters()).thenReturn(Set.of());
        when(site.getSmartMeters()).thenReturn(Set.of());
        when(queries.getLastResult(assigned, MinerStats.DEFAULT)).thenReturn(stats(700));
        when(queries.getLastResult(unassigned, MinerStats.DEFAULT)).thenReturn(stats(900));

        PVSiteDataDTO result = new SiteQueryStrategy().query(queries, site);

        assertEquals(1.6, result.getTotalMinerPowerKw(), 0.0001);
    }

    private MinerStats stats(long watts) {
        return new MinerStats(new MinerStats.MinerIdentity("uid", "mac", "model"), "Miner",
                MinerStats.MinerStatus.MINING, watts, 0, watts, watts, watts,
                0, 0, List.of(), List.of());
    }
}
