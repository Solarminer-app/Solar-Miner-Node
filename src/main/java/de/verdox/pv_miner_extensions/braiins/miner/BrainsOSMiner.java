package de.verdox.pv_miner_extensions.braiins.miner;

import de.verdox.pv_miner.miner.Miner;
import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MiningOS;

public class BrainsOSMiner extends Miner {
    private final MinerApiClient minerApiClient;
    private final BraiinsOSAsicMinerEntity minerEntity;

    public BrainsOSMiner(MinerApiClient minerApiClient, BraiinsOSAsicMinerEntity minerEntity) {
        super(minerEntity);
        this.minerApiClient = minerApiClient;
        this.minerEntity = minerEntity;
    }

    @Override
    public boolean setPowerTarget(long targetInWatts) {
        return minerApiClient.setPowerTarget(MiningOS.BRAIINS, details(), targetInWatts);
    }

    @Override
    public boolean incrementPowerTarget(long incrementInWatts) {
        return minerApiClient.incrementPowerTarget(MiningOS.BRAIINS, details(), incrementInWatts);
    }

    @Override
    public boolean decrementPowerTarget(long decrementInWatts) {
        return minerApiClient.decrementPowerTarget(MiningOS.BRAIINS, details(), decrementInWatts);
    }

    @Override
    public boolean startMining() {
        return minerApiClient.startMining(MiningOS.BRAIINS, details());
    }

    @Override
    public boolean stopMining() {
        return minerApiClient.stopMining(MiningOS.BRAIINS, details());
    }

    @Override
    public boolean pauseMining() {
        return minerApiClient.pauseMining(MiningOS.BRAIINS, details());
    }

    @Override
    public boolean resumeMining() {
        return minerApiClient.resumeMining(MiningOS.BRAIINS, details());
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
