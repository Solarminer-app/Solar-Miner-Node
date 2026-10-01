ALTER TABLE mining_targets ADD COLUMN coin VARCHAR(16) NOT NULL DEFAULT 'bitcoin';
ALTER TABLE mining_targets ADD COLUMN payout_coin VARCHAR(16) NOT NULL DEFAULT 'bitcoin';
ALTER TABLE mining_targets ADD COLUMN payout_address VARCHAR(160) NULL;
UPDATE watched_wallets SET coin = 'bitcoin' WHERE coin = 'BTC';
CREATE INDEX idx_mining_targets_site_coin_priority ON mining_targets (site_id, coin, priority);
