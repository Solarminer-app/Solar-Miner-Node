CREATE TABLE braiins_pool_entity
(
    id         UUID         NOT NULL,
    auth_token VARCHAR(255) NULL,
    CONSTRAINT pk_braiinspoolentity PRIMARY KEY (id)
);

CREATE TABLE braiinsosasic_miner_entity
(
    id       UUID         NOT NULL,
    username VARCHAR(255) NULL,
    password VARCHAR(255) NULL,
    host     VARCHAR(255) NULL,
    port     INT          NOT NULL,
    CONSTRAINT pk_braiinsosasicminerentity PRIMARY KEY (id)
);

CREATE TABLE miners
(
    id                         UUID                                   NOT NULL,
    created_at                 datetime DEFAULT '2025-01-01 00:00:00' NOT NULL,
    parent_entity              UUID                                   NULL,
    name                       VARCHAR(255)                           NULL,
    purchase_date              date     DEFAULT '2000-01-01'          NOT NULL,
    current_mining_pool_target VARCHAR(255)                           NULL,
    min_power_target           BIGINT                                 NOT NULL,
    max_power_target           BIGINT                                 NOT NULL,
    cluster_name               VARCHAR(255)                           NULL,
    miner_cost_amount          DOUBLE                                 NULL,
    miner_cost_currency        VARCHAR(255)                           NULL,
    CONSTRAINT pk_miners PRIMARY KEY (id)
);

CREATE TABLE mining_pools
(
    id                   UUID                                   NOT NULL,
    created_at           datetime DEFAULT '2025-01-01 00:00:00' NOT NULL,
    user_name_of_account VARCHAR(255)                           NULL,
    parent_entity        UUID                                   NULL,
    CONSTRAINT pk_mining_pools PRIMARY KEY (id)
);

CREATE TABLE modbuspvsite
(
    id                 UUID         NOT NULL,
    ip_address         VARCHAR(255) NULL,
    port               INT          NOT NULL,
    slave_id           INT          NOT NULL,
    modbus_config_name VARCHAR(255) NULL,
    CONSTRAINT pk_modbuspvsite PRIMARY KEY (id)
);

CREATE TABLE nice_hash_pool_entity
(
    id      UUID         NOT NULL,
    api_key VARCHAR(255) NULL,
    secret  VARCHAR(255) NULL,
    org_id  VARCHAR(255) NULL,
    CONSTRAINT pk_nicehashpoolentity PRIMARY KEY (id)
);

CREATE TABLE pv_site_btc_sales
(
    pv_site_id          UUID         NOT NULL,
    sale_date           date         NOT NULL,
    amount_btc          DOUBLE       NOT NULL,
    fiat_value_amount   DOUBLE       NULL,
    fiat_value_currency VARCHAR(255) NULL
);

CREATE TABLE pv_site_electricity_prices
(
    pv_site_id    UUID         NOT NULL,
    valid_from    date         NOT NULL,
    amount        DOUBLE       NULL,
    currency_code VARCHAR(255) NULL
);

CREATE TABLE pv_site_feed_in_tariffs
(
    pv_site_id    UUID         NOT NULL,
    valid_from    date         NOT NULL,
    amount        DOUBLE       NULL,
    currency_code VARCHAR(255) NULL
);

CREATE TABLE pv_sites
(
    id                  UUID                                   NOT NULL,
    created_at          datetime DEFAULT '2025-01-01 00:00:00' NOT NULL,
    name                VARCHAR(255)                           NULL,
    battery_capacity_wh INT                                    NOT NULL,
    setup_date          date     DEFAULT '2000-01-01'          NOT NULL,
    pv_cost_amount      DOUBLE                                 NULL,
    pv_cost_currency    VARCHAR(255)                           NULL,
    CONSTRAINT pk_pv_sites PRIMARY KEY (id)
);

CREATE TABLE pvpanels
(
    id                       UUID         NOT NULL,
    parent_entity            UUID         NULL,
    group_name               VARCHAR(255) NULL,
    latitude_deg             DOUBLE       NOT NULL,
    longitude_deg            DOUBLE       NOT NULL,
    panel_height             INT          NOT NULL,
    panel_azimuth_degree     DOUBLE       NOT NULL,
    panel_slope_deg          DOUBLE       NOT NULL,
    power_per_panel_in_watts DOUBLE       NOT NULL,
    amount_of_panels         INT          NOT NULL,
    CONSTRAINT pk_pvpanels PRIMARY KEY (id)
);

CREATE TABLE restpvsite
(
    id                UUID         NOT NULL,
    host_name         VARCHAR(255) NULL,
    port              INT          NOT NULL,
    api_token         VARCHAR(255) NULL,
    restpvconfig_name VARCHAR(255) NULL,
    CONSTRAINT pk_restpvsite PRIMARY KEY (id)
);

CREATE TABLE smart_fox_entity
(
    id       UUID         NOT NULL,
    ipv4host VARCHAR(255) NULL,
    CONSTRAINT pk_smartfoxentity PRIMARY KEY (id)
);

ALTER TABLE braiinsosasic_miner_entity
    ADD CONSTRAINT FK_BRAIINSOSASICMINERENTITY_ON_ID FOREIGN KEY (id) REFERENCES miners (id);

ALTER TABLE braiins_pool_entity
    ADD CONSTRAINT FK_BRAIINSPOOLENTITY_ON_ID FOREIGN KEY (id) REFERENCES mining_pools (id);

ALTER TABLE miners
    ADD CONSTRAINT FK_MINERS_ON_PARENTENTITY FOREIGN KEY (parent_entity) REFERENCES pv_sites (id);

ALTER TABLE mining_pools
    ADD CONSTRAINT FK_MINING_POOLS_ON_PARENTENTITY FOREIGN KEY (parent_entity) REFERENCES pv_sites (id);

ALTER TABLE modbuspvsite
    ADD CONSTRAINT FK_MODBUSPVSITE_ON_ID FOREIGN KEY (id) REFERENCES pv_sites (id);

ALTER TABLE nice_hash_pool_entity
    ADD CONSTRAINT FK_NICEHASHPOOLENTITY_ON_ID FOREIGN KEY (id) REFERENCES mining_pools (id);

ALTER TABLE pvpanels
    ADD CONSTRAINT FK_PVPANELS_ON_PARENTENTITY FOREIGN KEY (parent_entity) REFERENCES pv_sites (id);

ALTER TABLE restpvsite
    ADD CONSTRAINT FK_RESTPVSITE_ON_ID FOREIGN KEY (id) REFERENCES pv_sites (id);

ALTER TABLE smart_fox_entity
    ADD CONSTRAINT FK_SMARTFOXENTITY_ON_ID FOREIGN KEY (id) REFERENCES pv_sites (id);

ALTER TABLE pv_site_btc_sales
    ADD CONSTRAINT fk_pv_site_btc_sales_on_p_v_site_entity FOREIGN KEY (pv_site_id) REFERENCES pv_sites (id);

ALTER TABLE pv_site_electricity_prices
    ADD CONSTRAINT fk_pv_site_electricity_prices_on_p_v_site_entity FOREIGN KEY (pv_site_id) REFERENCES pv_sites (id);

ALTER TABLE pv_site_feed_in_tariffs
    ADD CONSTRAINT fk_pv_site_feed_in_tariffs_on_p_v_site_entity FOREIGN KEY (pv_site_id) REFERENCES pv_sites (id);