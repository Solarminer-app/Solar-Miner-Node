package de.verdox.pv_miner_extensions.agent;

import de.verdox.pv_miner.miner.Miner;
import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MiningOS;

public class AgentMiner extends Miner {
    private final MinerApiClient minerApiClient;
    private final AgentMinerEntity minerEntity;

    public AgentMiner(MinerApiClient minerApiClient, AgentMinerEntity minerEntity) {
        super(minerEntity);
        this.minerApiClient = minerApiClient;
        this.minerEntity = minerEntity;
    }


    @Override
    public boolean setPowerTarget(long targetInWatts) {
        return minerApiClient.setPowerTarget(os(), details(), targetInWatts);
    }

    @Override
    public boolean incrementPowerTarget(long incrementInWatts) {
        return minerApiClient.incrementPowerTarget(os(), details(), incrementInWatts);
    }

    @Override
    public boolean decrementPowerTarget(long decrementInWatts) {
        return minerApiClient.decrementPowerTarget(os(), details(), decrementInWatts);
    }

    @Override
    public boolean startMining() {
        return minerApiClient.startMining(os(), details());
    }

    @Override
    public boolean stopMining() {
        return minerApiClient.stopMining(os(), details());
    }

    @Override
    public boolean pauseMining() {
        return minerApiClient.pauseMining(os(), details());
    }

    @Override
    public boolean resumeMining() {
        return minerApiClient.resumeMining(os(), details());
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
