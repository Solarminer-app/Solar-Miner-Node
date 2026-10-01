CREATE TABLE mining_coin_daily_rewards (
    id UUID NOT NULL PRIMARY KEY,
    site_id UUID NOT NULL,
    coin VARCHAR(16) NOT NULL,
    payout_address VARCHAR(160) NOT NULL,
    reward_date DATE NOT NULL,
    amount DECIMAL(30, 18) NOT NULL,
    price_usd DECIMAL(30, 12) NULL,
    fetched_at TIMESTAMP(3) NOT NULL,
    CONSTRAINT uk_mining_coin_daily_reward UNIQUE (site_id, coin, payout_address, reward_date),
    CONSTRAINT fk_mining_coin_daily_reward_site FOREIGN KEY (site_id) REFERENCES pv_sites (id) ON DELETE CASCADE
);

CREATE INDEX idx_mining_coin_daily_reward_site_date ON mining_coin_daily_rewards (site_id, reward_date);
