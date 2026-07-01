package de.verdox.pv_miner.miner;

import de.verdox.pv_miner.entity.EntityController;

public class MinerEntityController extends EntityController {
    private final MinerApiClient minerApiClient;
    private final MinerEntity<?> miner;

    public MinerEntityController(MinerApiClient minerApiClient, MinerEntity<?> miner) {
        super(miner);
        this.minerApiClient = minerApiClient;
        this.miner = miner;
    }

    public boolean setPowerTarget(long targetInWatts) {
        return minerApiClient.setPowerTarget(miner.getOS(), details(), targetInWatts);
    }

    public boolean incrementPowerTarget(long incrementInWatts) {
        return minerApiClient.incrementPowerTarget(miner.getOS(), details(), incrementInWatts);
    }

    public boolean decrementPowerTarget(long decrementInWatts) {
        return minerApiClient.decrementPowerTarget(miner.getOS(), details(), decrementInWatts);
    }

    public boolean startMining() {
        return minerApiClient.startMining(miner.getOS(), details());
    }

    public boolean stopMining() {
        return minerApiClient.stopMining(miner.getOS(), details());
    }

    public boolean pauseMining() {
        return minerApiClient.pauseMining(miner.getOS(), details());
    }

    public boolean resumeMining() {
        return minerApiClient.resumeMining(miner.getOS(), details());
    }

    public final MinerApiClient.MinerDetails details() {
        return miner.getDetails();
    }

    public final MiningOS os() {
        return miner.getOS();
    }
}
