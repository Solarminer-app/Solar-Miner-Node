package de.verdox.pv_miner.dashboard;

import de.verdox.pv_miner.globalconstants.GlobalConstantsService;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.MiningOS;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.miningpool.MiningCoin;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;

/** Native SHA-256 forecast based on the Node's cached Bitcoin chain data. */
@Component
public class BitcoinEarningsSource implements MiningEarningsSource {
    private final GlobalConstantsService constants;
    public BitcoinEarningsSource(GlobalConstantsService constants) { this.constants = constants; }
    @Override public boolean supports(MinerEntity<?> miner) { return miner.getOS() != MiningOS.AGENT; }

    @Override public List<MiningEarningsService.CoinEstimate> forecast(MinerEntity<?> miner, MinerStats stats) {
        double hashrate = Math.max(0, stats.terahashPerSecond()) * 1e12;
        if (hashrate <= 0) return List.of();
        double watts = Math.max(0, stats.approximatedPowerUsageWatts());
        double difficulty = constants.getTodayMiningDifficulty();
        double reward = constants.getTodayBlockSubsidy() / 100_000_000.0;
        MiningCoin coin = MiningCoin.BITCOIN;
        double usdPrice = constants.getCurrentCoinPrices().getOrDefault(coin.symbol().toLowerCase(java.util.Locale.ROOT), 0.0);
        double amount = difficulty > 0 && reward > 0 ? hashrate * 86400.0 / (difficulty * 4294967296.0) * reward : 0;
        boolean available = watts > 0 && difficulty > 0 && reward > 0 && usdPrice > 0 && constants.hasFreshBitcoinStats();
        return List.of(new MiningEarningsService.CoinEstimate(coin.key(), coin.symbol(), available,
                available ? "" : "Chain-, Preis- oder Leistungsdaten fehlen", amount, amount * usdPrice,
                watts, hashrate, Instant.now().toString()));
    }
}
