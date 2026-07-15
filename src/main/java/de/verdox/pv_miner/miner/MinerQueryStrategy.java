package de.verdox.pv_miner.miner;

import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.miner.data.MinerStats;

public class MinerQueryStrategy implements EntityQueryService.Strategy<MinerEntity<?>, MinerStats> {

    @Override
    public MinerStats query(EntityQueryService entityQueryService, MinerEntity<?> miner) throws Throwable {
        String referralCode = miner.getParentEntity() == null ? null : miner.getParentEntity().getReferralCode();
        return SpringContextHelper.getBean(MinerApiClient.class).getStats(miner.getOS(), miner.getDetails(), referralCode);
    }

    @Override
    public void ping(MinerEntity<?> miner) throws Throwable {
        String referralCode = miner.getParentEntity() == null ? null : miner.getParentEntity().getReferralCode();
        SpringContextHelper.getBean(MinerApiClient.class).getStats(miner.getOS(), miner.getDetails(), referralCode);
    }
}
