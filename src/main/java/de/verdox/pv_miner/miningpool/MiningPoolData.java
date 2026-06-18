package de.verdox.pv_miner.miningpool;

import de.verdox.pv_miner.influx.QueryResult;

import java.util.Map;

public interface MiningPoolData extends QueryResult {
    MiningPoolData DEFAULT = new MiningPoolData() {};

    default boolean containsWorkerWithName(String workerName) {
        return false;
    }

    default double calculateSatoshiRewardToday(String workerName) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    default double calculateSatoshiRewardToday() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    default Map<Long, Double> getHistoricalPaidRewardsSatoshis() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    default String getDefaultWorkerName() {
        return null;
    }
}
