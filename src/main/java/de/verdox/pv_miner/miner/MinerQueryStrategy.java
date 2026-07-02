package de.verdox.pv_miner.miner;

import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.miner.data.MinerStats;

public class MinerQueryStrategy implements ASICMinerQueryStrategy<MinerEntity<?>> {

    @Override
    public MinerStats query(EntityQueryService entityQueryService, MinerEntity<?> miner) throws Throwable {
        return SpringContextHelper.getBean(MinerApiClient.class).getStats(miner.getOS(), miner.getDetails());
    }

    @Override
    public void ping(MinerEntity<?> miner) throws Throwable {
        SpringContextHelper.getBean(MinerApiClient.class).getStats(miner.getOS(), miner.getDetails());
    }
}
