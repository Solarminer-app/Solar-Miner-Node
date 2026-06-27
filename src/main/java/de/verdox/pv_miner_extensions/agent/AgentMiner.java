package de.verdox.pv_miner_extensions.agent;

import de.verdox.pv_miner.miner.Miner;
import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MiningOS;

public class AgentMiner extends Miner {
    private final AgentMinerEntity minerEntity;

    public AgentMiner(MinerApiClient minerApiClient, AgentMinerEntity minerEntity) {
        super(minerApiClient, minerEntity);
        this.minerEntity = minerEntity;
    }

    @Override
    public MinerApiClient.MinerDetails details() {
        return new MinerApiClient.MinerDetails(minerEntity.getId(), minerEntity.getIP(), minerEntity.getPort(), "", "");
    }

    @Override
    public MiningOS os() {
        return MiningOS.AGENT;
    }
}
