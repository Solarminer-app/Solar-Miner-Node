package de.verdox.pv_miner_extensions.antminer;

import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.miner.ASICMinerQueryStrategy;
import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MiningOS;
import de.verdox.pv_miner.miner.data.MinerStats;

public class AntminerQueryStrategy implements ASICMinerQueryStrategy<AntminerEntity> {
    @Override
    public MinerStats query(EntityQueryService entityQueryService, AntminerEntity entity) {
        MinerApiClient minerApiClient = SpringContextHelper.getBean(MinerApiClient.class);
        MinerApiClient.MinerDetails minerDetails = new MinerApiClient.MinerDetails(entity.getId(), entity.getHost(), entity.getPort(), entity.getUsername(), entity.getPassword());
        return minerApiClient.getStats(MiningOS.ANTMINER_STOCK_OS, minerDetails);
    }

    @Override
    public void ping(AntminerEntity entity) {
        MinerApiClient minerApiClient = SpringContextHelper.getBean(MinerApiClient.class);

        MinerApiClient.MinerDetails minerDetails = new MinerApiClient.MinerDetails(entity.getId(), entity.getHost(), entity.getPort(), entity.getUsername(), entity.getPassword());
        minerApiClient.getStats(MiningOS.ANTMINER_STOCK_OS, minerDetails);
    }
}
