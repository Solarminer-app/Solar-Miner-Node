package de.verdox.pv_miner.miner;

import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.miner.data.MinerStats;

public interface ASICMinerQueryStrategy<T extends MinerEntity<?>> extends EntityQueryService.Strategy<T, MinerStats> {
}
