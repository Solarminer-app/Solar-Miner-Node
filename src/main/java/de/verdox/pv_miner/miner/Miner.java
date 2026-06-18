package de.verdox.pv_miner.miner;

import de.verdox.pv_miner.entity.ControllableEntity;
import de.verdox.pv_miner.entity.EntityController;

public abstract class Miner extends EntityController {
    public Miner(ControllableEntity<?> controllableEntity) {
        super(controllableEntity);
    }

    public abstract boolean setPowerTarget(long targetInWatts);

    @Deprecated
    public abstract boolean incrementPowerTarget(long incrementInWatts);

    @Deprecated
    public abstract boolean decrementPowerTarget(long decrementInWatts);

    public abstract boolean startMining();

    public abstract boolean stopMining();

    public abstract boolean pauseMining();

    public abstract boolean resumeMining();

    public abstract MinerApiClient.MinerDetails details();
    public abstract MiningOS os();
}
