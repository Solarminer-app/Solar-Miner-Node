package de.verdox.pv_miner_extensions.agent;

import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.miner.ASICMinerQueryStrategy;
import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MiningOS;
import de.verdox.pv_miner.miner.data.MinerStats;

import java.util.UUID;

public class AgentMinerQueryStrategy implements ASICMinerQueryStrategy<AgentMinerEntity> {
    @Override
    public MinerStats query(EntityQueryService entityQueryService, AgentMinerEntity entity) {
        MinerApiClient minerApiClient = SpringContextHelper.getBean(MinerApiClient.class);
        MinerApiClient.MinerDetails minerDetails = new MinerApiClient.MinerDetails(entity.getId(), entity.getHost(), entity.getPort(), "", "");
        return minerApiClient.getStats(MiningOS.AGENT, minerDetails);
    }

    @Override
    public void ping(AgentMinerEntity entity) {
        MinerApiClient minerApiClient = SpringContextHelper.getBean(MinerApiClient.class);

        MinerApiClient.MinerDetails minerDetails = new MinerApiClient.MinerDetails(entity.getId(), entity.getHost(), entity.getPort(), "", "");
        minerApiClient.getStats(MiningOS.AGENT, minerDetails);
    }
}
