package de.verdox.pv_miner_extensions.braiins.miner;

import de.verdox.pv_miner.miner.Miner;
import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MiningOS;

public class BrainsOSMiner extends Miner {
    private final BraiinsOSAsicMinerEntity minerEntity;

    public BrainsOSMiner(MinerApiClient minerApiClient, BraiinsOSAsicMinerEntity minerEntity) {
        super(minerApiClient, minerEntity);
        this.minerEntity = minerEntity;
    }
    @Override
    public MinerApiClient.MinerDetails details() {
        return new MinerApiClient.MinerDetails(minerEntity.getId(), minerEntity.getHost(), minerEntity.getPort(), minerEntity.getUsername(), minerEntity.getPassword());
    }

    @Override
    public MiningOS os() {
        return MiningOS.BRAIINS;
    }
}
