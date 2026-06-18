create table if not exists pv_sites
(
    id                              uuid                                    not null
        primary key,
    created_at                      timestamp default '2025-01-01 00:00:00' not null,
    setup_date                      timestamp default '2025-01-01 00:00:00' not null,
    battery_capacity_wh             int       default 0                     not null,
    export_revenue_per_kwh_amount   double    default 0                     null,
    export_revenue_per_kwh_currency varchar(255)                            null,
    pv_cost_amount                  double    default 0                     null,
    pv_cost_currency                varchar(255)                            null,
    name                            varchar(255)                            null
);

create table if not exists miners
(
    id                     uuid                                    not null
        primary key,
    created_at             timestamp default '2025-01-01 00:00:00' not null,
    purchase_date          timestamp default '2025-01-01 00:00:00' not null,
    controller_config_name varchar(255)                            null,
    name                   varchar(255)                            null,
    parent_entity          uuid                                    null,
    miner_cost_amount      double                                  null,
    miner_cost_currency    varchar(255)                            null,
    constraint FKj9jk7gcjqmu7kdglgv942ttwj
        foreign key (parent_entity) references pv_sites (id)
);

create table if not exists mining_pools
(
    id            uuid                                    not null
        primary key,
    created_at    timestamp default '2025-01-01 00:00:00' not null,
    parent_entity uuid                                    null,
    constraint FK4ij5qco8mkjt5v4pne0hhx4sf
        foreign key (parent_entity) references pv_sites (id)
);

create table if not exists braiins_pool_entity
(
    id         uuid         not null
        primary key,
    auth_token varchar(255) null,
    constraint FK945eghlfu6m4r7lllmc4bwh4j
        foreign key (id) references mining_pools (id)
);

create table if not exists braiinsosasic_miner_entity
(
    id       uuid         not null
        primary key,
    host     varchar(255) null,
    password varchar(255) null,
    port     int          not null,
    username varchar(255) null
);

create table if not exists fox_cloud_site_entity
(
    id          uuid           not null
        primary key,
    name        varchar(255)   null,
    api_token   varchar(255)   null,
    invertersns varbinary(255) null
);

create table if not exists modbuspvsite
(
    id                 uuid         not null
        primary key,
    name               varchar(255) null,
    ip_address         varchar(255) null,
    modbus_config_name varchar(255) null,
    port               int          not null,
    slave_id           int          not null
);

create table if not exists nice_hash_pool_entity
(
    id            uuid         not null
        primary key,
    parent_entity uuid         null,
    api_key       varchar(255) null,
    org_id        varchar(255) null,
    secret        varchar(255) null
);

create table if not exists pv_panels
(
    id                       uuid   not null
        primary key,
    amount_of_panels         int    not null,
    latitude_deg             double not null,
    longitude_deg            double not null,
    panel_azimuth_degree     double not null,
    panel_height             int    not null,
    panel_slope_deg          double not null,
    power_per_panel_in_watts double not null,
    parent_entity            uuid   null,
    constraint FK73hxsy02sypbir4bl3tgbrw5i
        foreign key (parent_entity) references pv_sites (id)
);

create table if not exists smart_fox_entity
(
    id       uuid         not null
        primary key,
    name     varchar(255) null,
    ipv4host varchar(255) null
);

CREATE TABLE daily_usd_rates
(
    date DATE NOT NULL,
    CONSTRAINT pk_daily_usd_rates PRIMARY KEY (date)
);

CREATE TABLE currency_rate_entries
(
    rate_date     DATE        NOT NULL,
    currency_code VARCHAR(10) NOT NULL,
    rate          DECIMAL(24, 12),
    CONSTRAINT pk_currency_rate_entries PRIMARY KEY (rate_date, currency_code),
    CONSTRAINT fk_currency_rate_entries_on_daily_usd_rates
        FOREIGN KEY (rate_date) REFERENCES daily_usd_rates (date) ON DELETE CASCADE
);

CREATE TABLE bitcoin_network_stats
(
    date               DATE             NOT NULL,
    mining_difficulty  BIGINT           NOT NULL,
    hash_rate_ths      DOUBLE PRECISION NOT NULL, -- Bei MySQL/MariaDB alternativ einfach "DOUBLE"
    price_in_dollar    DOUBLE PRECISION NOT NULL,
    block_subsidy      INTEGER          NOT NULL,
    average_tx_fee_24h INTEGER          NOT NULL,
    CONSTRAINT pk_bitcoin_network_stats PRIMARY KEY (date)
);

