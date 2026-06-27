package de.verdox.pv_miner.miner;

import de.verdox.pv_miner.entity.ControllableEntity;
import de.verdox.pv_miner.entity.EntityController;

public abstract class Miner extends EntityController {
    private final MinerApiClient minerApiClient;

    public Miner(MinerApiClient minerApiClient, ControllableEntity<?> controllableEntity) {
        super(controllableEntity);
        this.minerApiClient = minerApiClient;
    }

    public boolean setPowerTarget(long targetInWatts) {
        return minerApiClient.setPowerTarget(MiningOS.BRAIINS, details(), targetInWatts);
    }

    public boolean incrementPowerTarget(long incrementInWatts) {
        return minerApiClient.incrementPowerTarget(MiningOS.BRAIINS, details(), incrementInWatts);
    }

    public boolean decrementPowerTarget(long decrementInWatts) {
        return minerApiClient.decrementPowerTarget(MiningOS.BRAIINS, details(), decrementInWatts);
    }

    public boolean startMining() {
        return minerApiClient.startMining(MiningOS.BRAIINS, details());
    }

    public boolean stopMining() {
        return minerApiClient.stopMining(MiningOS.BRAIINS, details());
    }

    public boolean pauseMining() {
        return minerApiClient.pauseMining(MiningOS.BRAIINS, details());
    }

    public boolean resumeMining() {
        return minerApiClient.resumeMining(MiningOS.BRAIINS, details());
    }

    public abstract MinerApiClient.MinerDetails details();

    public abstract MiningOS os();
}
