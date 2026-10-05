CREATE TABLE mining_network_snapshots (
    coin_key VARCHAR(32) NOT NULL,
    ticker VARCHAR(12) NOT NULL,
    algorithm VARCHAR(32) NOT NULL,
    network_hashrate_hps DOUBLE NOT NULL,
    difficulty DOUBLE NOT NULL,
    target_block_seconds DOUBLE NOT NULL,
    block_reward DOUBLE NOT NULL,
    price_usd DOUBLE NOT NULL,
    collected_at TIMESTAMP(6) NOT NULL,
    network_source VARCHAR(255) NOT NULL,
    price_source VARCHAR(255) NOT NULL,
    provider_stale BOOLEAN NOT NULL,
    PRIMARY KEY (coin_key)
);
