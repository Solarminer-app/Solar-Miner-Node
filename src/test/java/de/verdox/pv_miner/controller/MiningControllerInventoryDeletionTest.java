package de.verdox.pv_miner.controller;

import de.verdox.pv_miner.discovery.DiscoveryService;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.entity.EntityService;
import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miningcontroller.MinerClusterService;
import de.verdox.pv_miner.miningpool.MiningPoolEntity;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MiningControllerInventoryDeletionTest {
    private final UUID siteId = UUID.randomUUID();
    private final UUID minerId = UUID.randomUUID();
    private final UUID poolId = UUID.randomUUID();
    private final PVSiteRepository siteRepository = mock(PVSiteRepository.class);
    private final EntityService entityService = mock(EntityService.class);
    private final PVSiteEntity site = mock(PVSiteEntity.class);
    private final MinerEntity<?> miner = mock(MinerEntity.class);
    private final MiningPoolEntity<?> pool = mock(MiningPoolEntity.class);
    private MiningController controller;

    @BeforeEach
    void setUp() {
        controller = new MiningController(
                siteRepository,
                mock(MinerClusterService.class),
                mock(EntityQueryService.class),
                entityService,
                mock(DiscoveryService.class),
                mock(MinerApiClient.class)
        );
        when(siteRepository.findById(siteId)).thenReturn(Optional.of(site));
        when(site.getMiners()).thenReturn(Set.of(miner));
        when(site.getConnectedMiningPools()).thenReturn(Set.of(pool));
        when(miner.getId()).thenReturn(minerId);
        when(pool.getId()).thenReturn(poolId);
    }

    @Test
    void deletesMinerOnlyWhenItBelongsToSite() {
        controller.deleteMiner(siteId, minerId);

        verify(entityService).delete(miner);
    }

    @Test
    void rejectsMinerFromAnotherSite() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.deleteMiner(siteId, UUID.randomUUID())
        );

        assertEquals(404, exception.getStatusCode().value());
        verify(entityService, never()).delete(miner);
    }

    @Test
    void deletesPoolOnlyWhenItBelongsToSite() {
        controller.deletePool(siteId, poolId);

        verify(entityService).delete(pool);
    }

    @Test
    void rejectsPoolFromAnotherSite() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.deletePool(siteId, UUID.randomUUID())
        );

        assertEquals(404, exception.getStatusCode().value());
        verify(entityService, never()).delete(pool);
    }
}
