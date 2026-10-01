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
        double watts = Math.max(0, stats.approximatedPowerUsageWatts());
        double difficulty = constants.getTodayMiningDifficulty();
        double reward = constants.getTodayBlockSubsidy() / 100_000_000.0;
        MiningCoin coin = MiningCoin.BITCOIN;
        double usdPrice = constants.getCurrentCoinPrices().getOrDefault(coin.symbol().toLowerCase(java.util.Locale.ROOT), 0.0);
        double amount = difficulty > 0 && reward > 0 ? hashrate * 86400.0 / (difficulty * 4294967296.0) * reward : 0;
        List<String> diagnostics = new java.util.ArrayList<>();
        if (hashrate <= 0) diagnostics.add("BTC-Hashrate fehlt oder ist 0 H/s");
        if (watts <= 0) diagnostics.add("ASIC-Leistungsmessung fehlt oder ist 0 W");
        if (difficulty <= 0 || reward <= 0) diagnostics.add("Bitcoin-Schwierigkeit oder Blocksubsidy fehlt");
        if (usdPrice <= 0) diagnostics.add("BTC/USD-Kurs fehlt");
        if (!constants.hasFreshCoinPrices()) diagnostics.add("Coin-Preise sind veraltet");
        if (!constants.hasFreshBitcoinStats()) diagnostics.add("Bitcoin-Chain-Daten sind veraltet");
        boolean available = diagnostics.isEmpty();
        return List.of(new MiningEarningsService.CoinEstimate(coin.key(), coin.symbol(), available,
                available ? "" : String.join("; ", diagnostics), amount, amount * usdPrice,
                watts, hashrate, Instant.now().toString(),
                List.of("Solar-Miner-Node Miner-Statistik", "Solar-Miner-Node GlobalConstantsService (Bitcoin-Chain, BTC/USD)"),
                List.copyOf(diagnostics)));
    }
}
