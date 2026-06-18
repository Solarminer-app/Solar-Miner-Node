package de.verdox.pv_miner.miningpool;

import de.verdox.pv_miner.influx.InfluxEntityStrategy;

public interface MiningPoolInfluxStrategy<R extends MiningPoolData, Q extends MiningPoolEntity<R>> extends InfluxEntityStrategy<Q, R> {
}
