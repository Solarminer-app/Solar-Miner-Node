package de.verdox.pv_miner.miningpool;

import de.verdox.pv_miner.entity.EntityQueryService;

public interface MiningPoolQueryStrategy<Q extends MiningPoolData, T extends MiningPoolEntity<Q>> extends EntityQueryService.Strategy<T, Q> {
}
