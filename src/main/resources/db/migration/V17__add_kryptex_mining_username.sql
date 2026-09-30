ALTER TABLE kryptex_pool_accounts
    ADD COLUMN mining_username VARCHAR(120) NOT NULL DEFAULT '',
    ADD COLUMN ownership_confirmed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE mining_targets
    ADD COLUMN stratum_username VARCHAR(160) NULL;
