CREATE TABLE twenty_one_energy_heater_entity
(
    id               UUID         NOT NULL,
    host             VARCHAR(255) NULL,
    port             INT          NOT NULL DEFAULT 80,
    integration_mode VARCHAR(32)  NOT NULL DEFAULT 'MONITORING',
    product_id       VARCHAR(255) NULL,
    api_version      VARCHAR(255) NULL,
    firmware_version VARCHAR(255) NULL,
    CONSTRAINT pk_twenty_one_energy_heater_entity PRIMARY KEY (id),
    CONSTRAINT fk_twenty_one_energy_heater_entity_on_id FOREIGN KEY (id) REFERENCES miners (id)
);
