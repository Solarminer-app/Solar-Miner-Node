CREATE TABLE kryptex_pool_accounts (
    id UUID NOT NULL PRIMARY KEY,
    site_id UUID NOT NULL,
    coin VARCHAR(16) NOT NULL,
    label VARCHAR(120) NOT NULL,
    payout_address VARCHAR(160) NOT NULL,
    CONSTRAINT uk_kryptex_pool_account UNIQUE (site_id, coin, payout_address),
    CONSTRAINT fk_kryptex_pool_account_site FOREIGN KEY (site_id) REFERENCES pv_sites (id) ON DELETE CASCADE
);

CREATE INDEX idx_kryptex_pool_account_site ON kryptex_pool_accounts (site_id);
