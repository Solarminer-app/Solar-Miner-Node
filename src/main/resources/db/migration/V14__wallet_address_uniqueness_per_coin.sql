ALTER TABLE watched_wallets ADD CONSTRAINT uk_watched_wallets_site_coin_address UNIQUE (site_id, coin, address);
ALTER TABLE watched_wallets DROP INDEX uk_watched_wallets_site_address;
