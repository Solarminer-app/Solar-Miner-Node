package de.verdox.pv_miner.dashboard;

import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.data.MinerStats;
import java.util.List;

/** Device or chain-specific forecast source. Implementations are registered as Spring beans. */
public interface MiningEarningsSource {
    boolean supports(MinerEntity<?> miner);
    List<MiningEarningsService.CoinEstimate> forecast(MinerEntity<?> miner, MinerStats stats);
}
