package de.verdox.pv_miner.controller;

import de.verdox.pv_miner.discovery.DiscoveryService;
import de.verdox.pv_miner.dto.MiningPageDto;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.entity.EntityService;
import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.miningcontroller.MinerClusterService;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MiningControllerLiveSnapshotTest {
    private final UUID siteId = UUID.randomUUID();
    private final UUID minerId = UUID.randomUUID();
    private final PVSiteRepository siteRepository = mock(PVSiteRepository.class);
    private final EntityQueryService queryService = mock(EntityQueryService.class);
    private final PVSiteEntity site = mock(PVSiteEntity.class);
    private final MinerEntity<?> miner = mock(MinerEntity.class);
    private MiningController controller;

    @BeforeEach
    void setUp() {
        controller = new MiningController(
                siteRepository,
                mock(MinerClusterService.class),
                queryService,
                mock(EntityService.class),
                mock(DiscoveryService.class),
                mock(MinerApiClient.class)
        );
        when(siteRepository.findById(siteId)).thenReturn(Optional.of(site));
        when(site.getMiners()).thenReturn(Set.of(miner));
        when(miner.getId()).thenReturn(minerId);
    }

    @Test
    void returnsTheLatestCachedMinerValuesWithoutAllowingHttpCaching() {
        when(queryService.getLastResult(miner, MinerStats.DEFAULT))
                .thenReturn(stats(90, 2_400, 71), stats(105, 2_650, 74));

        ResponseEntity<MiningPageDto.LiveSnapshotDto> first = controller.getMiningLiveSnapshot(siteId);
        ResponseEntity<MiningPageDto.LiveSnapshotDto> second = controller.getMiningLiveSnapshot(siteId);

        assertNotNull(first.getBody());
        assertNotNull(second.getBody());
        assertEquals(90, first.getBody().totalHashrateThs(), 0.001);
        assertEquals(105, second.getBody().totalHashrateThs(), 0.001);
        assertEquals(2_650, second.getBody().miners().getFirst().powerWatts());
        assertEquals(74, second.getBody().miners().getFirst().temperatureCelsius(), 0.001);
        assertTrue(first.getHeaders().getCacheControl().contains("no-store"));
    }

    private MinerStats stats(double hashrate, long power, double temperature) {
        return new MinerStats(
                new MinerStats.MinerIdentity("uid", "mac", "model"), "Miner",
                MinerStats.MinerStatus.MINING, 2_500, 800, 3_000, 4_000,
                power, hashrate, temperature, List.of(), List.of()
        );
    }
}
