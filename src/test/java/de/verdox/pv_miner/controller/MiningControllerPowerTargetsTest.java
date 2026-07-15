package de.verdox.pv_miner.controller;

import de.verdox.pv_miner.discovery.DiscoveryService;
import de.verdox.pv_miner.dto.MiningPageRequests.MinerPowerTargetRequest;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.entity.EntityService;
import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.MiningOS;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.miningcontroller.MinerClusterService;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MiningControllerPowerTargetsTest {
    private final UUID siteId = UUID.randomUUID();
    private final UUID minerId = UUID.randomUUID();
    private final PVSiteRepository siteRepository = mock(PVSiteRepository.class);
    private final EntityQueryService queryService = mock(EntityQueryService.class);
    private final EntityService entityService = mock(EntityService.class);
    private final PVSiteEntity site = mock(PVSiteEntity.class);
    private final MinerEntity<?> miner = mock(MinerEntity.class);
    private MiningController controller;

    @BeforeEach
    void setUp() {
        controller = new MiningController(
                siteRepository,
                mock(MinerClusterService.class),
                queryService,
                entityService,
                mock(DiscoveryService.class),
                mock(MinerApiClient.class)
        );
        when(siteRepository.findById(siteId)).thenReturn(Optional.of(site));
        when(site.getMiners()).thenReturn(Set.of(miner));
        when(miner.getId()).thenReturn(minerId);
        when(miner.getOS()).thenReturn(MiningOS.BRAIINS);
        when(queryService.getLastResult(miner, MinerStats.DEFAULT)).thenReturn(stats());
    }

    @Test
    void savesTargetsWithinCoreLimitsWithoutRiskAcknowledgement() {
        controller.updateMinerPowerTargets(siteId, minerId, new MinerPowerTargetRequest(1_000, 3_200, false));

        verify(miner).setMinPowerTarget(1_000);
        verify(miner).setMaxPowerTarget(3_200);
        verify(entityService).save(miner, site);
    }

    @Test
    void requiresExplicitAcknowledgementAboveElectricalThreshold() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.updateMinerPowerTargets(siteId, minerId, new MinerPowerTargetRequest(1_000, 3_300, false))
        );

        assertEquals(422, exception.getStatusCode().value());
        verify(entityService, never()).save(miner, site);
    }

    @Test
    void rejectsTargetsOutsideCoreHardwareLimits() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.updateMinerPowerTargets(siteId, minerId, new MinerPowerTargetRequest(700, 3_000, true))
        );

        assertEquals(400, exception.getStatusCode().value());
        verify(entityService, never()).save(miner, site);
    }

    private MinerStats stats() {
        return new MinerStats(
                new MinerStats.MinerIdentity("uid", "mac", "model"),
                "Miner",
                MinerStats.MinerStatus.MINING,
                2_500,
                800,
                3_000,
                4_000,
                2_400,
                100,
                70,
                List.of(),
                List.of()
        );
    }
}
