CREATE TABLE mining_targets (
    id UUID NOT NULL,
    site_id UUID NOT NULL,
    algorithm VARCHAR(32) NOT NULL,
    name VARCHAR(120) NOT NULL,
    stratum_url VARCHAR(255) NOT NULL,
    worker_prefix VARCHAR(160) NOT NULL,
    priority INT NOT NULL,
    enabled BOOLEAN NOT NULL,
    CONSTRAINT pk_mining_targets PRIMARY KEY (id),
    CONSTRAINT fk_mining_targets_site FOREIGN KEY (site_id) REFERENCES pv_sites (id) ON DELETE CASCADE
);

CREATE INDEX idx_mining_targets_site_algorithm ON mining_targets (site_id, algorithm, priority);

CREATE TABLE watched_wallets (
    id UUID NOT NULL,
    site_id UUID NOT NULL,
    label VARCHAR(120) NOT NULL,
    coin VARCHAR(16) NOT NULL,
    address VARCHAR(160) NOT NULL,
    CONSTRAINT pk_watched_wallets PRIMARY KEY (id),
    CONSTRAINT fk_watched_wallets_site FOREIGN KEY (site_id) REFERENCES pv_sites (id) ON DELETE CASCADE,
    CONSTRAINT uk_watched_wallets_site_address UNIQUE (site_id, address)
);
