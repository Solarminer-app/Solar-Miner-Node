ALTER TABLE miners
    ADD COLUMN efficiency_dispatch_priority INT NULL,
    ADD COLUMN nominal_efficiency_j_th DOUBLE NULL;

CREATE TABLE miner_efficiency_profiles
(
    id                          BIGINT AUTO_INCREMENT NOT NULL,
    miner_id                    UUID                  NOT NULL,
    power_target_bucket_watts   INT                   NOT NULL,
    learned_efficiency_j_th     DOUBLE                NOT NULL,
    sample_count                BIGINT                NOT NULL,
    average_temperature_celsius DOUBLE                NULL,
    last_observed_at            datetime(6)           NOT NULL,
    CONSTRAINT pk_miner_efficiency_profiles PRIMARY KEY (id),
    CONSTRAINT uk_miner_efficiency_profile_bucket UNIQUE (miner_id, power_target_bucket_watts),
    CONSTRAINT fk_miner_efficiency_profile_miner FOREIGN KEY (miner_id) REFERENCES miners (id) ON DELETE CASCADE
);

CREATE INDEX idx_miner_efficiency_profile_miner ON miner_efficiency_profiles (miner_id);
