package de.verdox.currencyrates.currencyrates.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Latest complete profitability input for one canonical mining coin. */
@Entity
@Table(name = "mining_network_snapshots")
public class MiningNetworkSnapshot {
    @Id
    @Column(name = "coin_key", length = 32, nullable = false)
    private String coin;

    @Column(length = 12, nullable = false)
    private String ticker;

    @Column(length = 32, nullable = false)
    private String algorithm;

    @Column(name = "network_hashrate_hps", nullable = false)
    private double networkHashrateHps;

    @Column(nullable = false)
    private double difficulty;

    @Column(name = "target_block_seconds", nullable = false)
    private double targetBlockSeconds;

    @Column(name = "block_reward", nullable = false)
    private double blockReward;

    @Column(name = "price_usd", nullable = false)
    private double priceUsd;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    @Column(name = "network_source", length = 255, nullable = false)
    private String networkSource;

    @Column(name = "price_source", length = 255, nullable = false)
    private String priceSource;

    @Column(name = "provider_stale", nullable = false)
    private boolean providerStale;

    protected MiningNetworkSnapshot() {
    }

    public MiningNetworkSnapshot(String coin, String ticker, String algorithm, double networkHashrateHps,
                                 double difficulty, double targetBlockSeconds, double blockReward,
                                 double priceUsd, Instant collectedAt, String networkSource,
                                 String priceSource, boolean providerStale) {
        this.coin = coin;
        this.ticker = ticker;
        this.algorithm = algorithm;
        this.networkHashrateHps = networkHashrateHps;
        this.difficulty = difficulty;
        this.targetBlockSeconds = targetBlockSeconds;
        this.blockReward = blockReward;
        this.priceUsd = priceUsd;
        this.collectedAt = collectedAt;
        this.networkSource = networkSource;
        this.priceSource = priceSource;
        this.providerStale = providerStale;
    }

    public String getCoin() { return coin; }
    public String getTicker() { return ticker; }
    public String getAlgorithm() { return algorithm; }
    public double getNetworkHashrateHps() { return networkHashrateHps; }
    public double getDifficulty() { return difficulty; }
    public double getTargetBlockSeconds() { return targetBlockSeconds; }
    public double getBlockReward() { return blockReward; }
    public double getPriceUsd() { return priceUsd; }
    public Instant getCollectedAt() { return collectedAt; }
    public String getNetworkSource() { return networkSource; }
    public String getPriceSource() { return priceSource; }
    public boolean isProviderStale() { return providerStale; }
}
