CREATE TABLE bitcoin_network_stats
(
    date               DATE   NOT NULL,
    mining_difficulty  BIGINT NOT NULL,
    hash_rate_ths      DOUBLE NOT NULL,
    price_in_dollar    DOUBLE NOT NULL,
    block_subsidy      INT    NOT NULL,
    average_tx_fee_24h INT    NOT NULL,
    PRIMARY KEY (date)
);

CREATE TABLE daily_usd_rates (
                                 date DATE NOT NULL,
                                 PRIMARY KEY (date)
);

CREATE TABLE currency_rate_entries (
                                       rate_date DATE NOT NULL,
                                       currency_code VARCHAR(10) NOT NULL,
                                       rate DECIMAL(24,12) NOT NULL,
                                       PRIMARY KEY (rate_date, currency_code),
                                       CONSTRAINT fk_currency_rates_date FOREIGN KEY (rate_date)
                                           REFERENCES daily_usd_rates (date) ON DELETE CASCADE
);