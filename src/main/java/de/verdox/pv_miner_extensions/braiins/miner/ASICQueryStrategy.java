package de.verdox.pv_miner_extensions.braiins.miner;

import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.miner.ASICMinerQueryStrategy;
import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MiningOS;
import de.verdox.pv_miner.miner.data.MinerStats;

import java.util.UUID;

public class ASICQueryStrategy implements ASICMinerQueryStrategy<BraiinsOSAsicMinerEntity> {
    @Override
    public MinerStats query(EntityQueryService entityQueryService, BraiinsOSAsicMinerEntity entity) {
        MinerApiClient minerApiClient = SpringContextHelper.getBean(MinerApiClient.class);

        MinerApiClient.MinerDetails minerDetails = new MinerApiClient.MinerDetails(UUID.randomUUID(), entity.getHost(), entity.getPort(), entity.getUsername(), entity.getPassword());

        return minerApiClient.getStats(MiningOS.BRAIINS, minerDetails).withMinPowerTarget(entity.getMinPowerTarget()).withMaxPowerTarget(entity.getMaxPowerTarget());
    }

    @Override
    public void ping(BraiinsOSAsicMinerEntity entity) {
        MinerApiClient minerApiClient = SpringContextHelper.getBean(MinerApiClient.class);

        MinerApiClient.MinerDetails minerDetails = new MinerApiClient.MinerDetails(UUID.randomUUID(), entity.getHost(), entity.getPort(), entity.getUsername(), entity.getPassword());
        minerApiClient.getStats(MiningOS.BRAIINS, minerDetails);
    }
}
