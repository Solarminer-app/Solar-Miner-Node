package de.verdox.pv_miner.entity;

import de.verdox.pv_miner.miner.MinerRepository;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner_extensions.miner.AgentMinerEntity;
import de.verdox.pv_miner_extensions.miner.AntminerEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class EntityQueryServiceAgentNameTest {
    private final MinerRepository repository = mock(MinerRepository.class);
    private final EntityQueryService service = new EntityQueryService(repository);

    @Test
    void persistsAChangedAgentNameOnlyOnce() {
        AgentMinerEntity agent = new AgentMinerEntity();
        agent.setName("Old workstation");
        MinerStats stats = stats("Renamed workstation");

        service.synchronizeAgentName(agent, stats);
        service.synchronizeAgentName(agent, stats);

        assertEquals("Renamed workstation", agent.getName());
        verify(repository, times(1)).save(agent);
    }

    @Test
    void ignoresDefaultInvalidAndNonAgentNames() {
        AgentMinerEntity agent = new AgentMinerEntity();
        agent.setName("Workstation");
        AntminerEntity asic = new AntminerEntity();
        asic.setName("User ASIC name");

        service.synchronizeAgentName(agent, stats("-"));
        service.synchronizeAgentName(agent, stats("invalid\nname"));
        service.synchronizeAgentName(asic, stats("Reported ASIC name"));

        assertEquals("Workstation", agent.getName());
        assertEquals("User ASIC name", asic.getName());
        verify(repository, never()).save(agent);
        verify(repository, never()).save(asic);
    }

    private MinerStats stats(String name) {
        return new MinerStats(new MinerStats.MinerIdentity("uid", "mac", "SolarMiner-PC-Agent"), name,
                MinerStats.MinerStatus.STOPPED, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of());
    }
}
