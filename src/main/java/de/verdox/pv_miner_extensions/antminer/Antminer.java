package de.verdox.pv_miner_extensions.antminer;

import de.verdox.pv_miner.miner.Miner;
import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MiningOS;

public class Antminer extends Miner {
    private final AntminerEntity minerEntity;

    public Antminer(MinerApiClient minerApiClient, AntminerEntity minerEntity) {
        super(minerApiClient, minerEntity);
        this.minerEntity = minerEntity;
    }

    @Override
    public MinerApiClient.MinerDetails details() {
        return new MinerApiClient.MinerDetails(minerEntity.getId(), minerEntity.getIP(), minerEntity.getPort(), minerEntity.getUsername(), minerEntity.getPassword());
    }

    @Override
    public MiningOS os() {
        return MiningOS.ANTMINER_STOCK_OS;
    }
}
