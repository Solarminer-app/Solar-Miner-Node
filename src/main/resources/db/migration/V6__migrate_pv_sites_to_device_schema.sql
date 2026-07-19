ALTER TABLE pvpanels ADD COLUMN IF NOT EXISTS pv_site_id UUID NULL;
UPDATE pvpanels SET pv_site_id = parent_entity WHERE pv_site_id IS NULL;

CREATE TABLE IF NOT EXISTS inverters (
    id UUID NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    name VARCHAR(255) NULL,
    pv_site_id UUID NOT NULL,
    max_ac_output_power_w INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_inverters PRIMARY KEY (id),
    CONSTRAINT fk_inverters_pv_site FOREIGN KEY (pv_site_id) REFERENCES pv_sites (id)
);

CREATE TABLE IF NOT EXISTS batteries (
    id UUID NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    name VARCHAR(255) NULL,
    pv_site_id UUID NOT NULL,
    nominal_capacity_wh INT NOT NULL DEFAULT 0,
    max_charge_power_w INT NOT NULL DEFAULT 0,
    max_discharge_power_w INT NOT NULL DEFAULT 0,
    CONSTRAINT pk_batteries PRIMARY KEY (id),
    CONSTRAINT fk_batteries_pv_site FOREIGN KEY (pv_site_id) REFERENCES pv_sites (id)
);

CREATE TABLE IF NOT EXISTS smart_meters (
    id UUID NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    name VARCHAR(255) NULL,
    pv_site_id UUID NOT NULL,
    is_grid_meter BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_smart_meters PRIMARY KEY (id),
    CONSTRAINT fk_smart_meters_pv_site FOREIGN KEY (pv_site_id) REFERENCES pv_sites (id)
);

CREATE TABLE IF NOT EXISTS modbus_inverter (
    id UUID NOT NULL, ip_address VARCHAR(255), port INT NOT NULL, slave_id INT NOT NULL,
    modbus_config_name VARCHAR(255), section_key VARCHAR(255),
    CONSTRAINT pk_modbus_inverter PRIMARY KEY (id),
    CONSTRAINT fk_modbus_inverter_id FOREIGN KEY (id) REFERENCES inverters (id)
);
CREATE TABLE IF NOT EXISTS modbus_battery (
    id UUID NOT NULL, ip_address VARCHAR(255), port INT NOT NULL, slave_id INT NOT NULL,
    modbus_config_name VARCHAR(255), section_key VARCHAR(255),
    CONSTRAINT pk_modbus_battery PRIMARY KEY (id),
    CONSTRAINT fk_modbus_battery_id FOREIGN KEY (id) REFERENCES batteries (id)
);
CREATE TABLE IF NOT EXISTS modbus_smart_meter (
    id UUID NOT NULL, ip_address VARCHAR(255), port INT NOT NULL, slave_id INT NOT NULL,
    modbus_config_name VARCHAR(255), section_key VARCHAR(255),
    CONSTRAINT pk_modbus_smart_meter PRIMARY KEY (id),
    CONSTRAINT fk_modbus_smart_meter_id FOREIGN KEY (id) REFERENCES smart_meters (id)
);

CREATE TABLE IF NOT EXISTS rest_inverter (
    id UUID NOT NULL, host_name VARCHAR(255), port INT NOT NULL, api_token VARCHAR(255),
    rest_config_name VARCHAR(255), section_key VARCHAR(255),
    CONSTRAINT pk_rest_inverter PRIMARY KEY (id),
    CONSTRAINT fk_rest_inverter_id FOREIGN KEY (id) REFERENCES inverters (id)
);
CREATE TABLE IF NOT EXISTS rest_battery (
    id UUID NOT NULL, host_name VARCHAR(255), port INT NOT NULL, api_token VARCHAR(255),
    rest_config_name VARCHAR(255), section_key VARCHAR(255),
    CONSTRAINT pk_rest_battery PRIMARY KEY (id),
    CONSTRAINT fk_rest_battery_id FOREIGN KEY (id) REFERENCES batteries (id)
);
CREATE TABLE IF NOT EXISTS rest_smart_meter (
    id UUID NOT NULL, host_name VARCHAR(255), port INT NOT NULL, api_token VARCHAR(255),
    rest_config_name VARCHAR(255), section_key VARCHAR(255),
    CONSTRAINT pk_rest_smart_meter PRIMARY KEY (id),
    CONSTRAINT fk_rest_smart_meter_id FOREIGN KEY (id) REFERENCES smart_meters (id)
);

CREATE TEMPORARY TABLE migration_modbus_devices AS
SELECT UUID() device_id, id site_id, ip_address, port, slave_id, modbus_config_name, 'INVERTER' device_type FROM modbuspvsite
UNION ALL SELECT UUID(), id, ip_address, port, slave_id, modbus_config_name, 'BATTERY' FROM modbuspvsite
UNION ALL SELECT UUID(), id, ip_address, port, slave_id, modbus_config_name, 'SMART_METER' FROM modbuspvsite;

INSERT INTO inverters (id, created_at, name, pv_site_id, max_ac_output_power_w, is_active)
SELECT device_id, CURRENT_TIMESTAMP, CONCAT('Migrated inverter (', ip_address, ')'), site_id, 0, TRUE FROM migration_modbus_devices WHERE device_type = 'INVERTER';
INSERT INTO modbus_inverter SELECT device_id, ip_address, port, slave_id, modbus_config_name, 'pvsite' FROM migration_modbus_devices WHERE device_type = 'INVERTER';
INSERT INTO batteries (id, created_at, name, pv_site_id, nominal_capacity_wh, max_charge_power_w, max_discharge_power_w)
SELECT d.device_id, CURRENT_TIMESTAMP, CONCAT('Migrated battery (', d.ip_address, ')'), d.site_id, p.battery_capacity_wh, 0, 0 FROM migration_modbus_devices d JOIN pv_sites p ON p.id = d.site_id WHERE d.device_type = 'BATTERY';
INSERT INTO modbus_battery SELECT device_id, ip_address, port, slave_id, modbus_config_name, 'pvsite' FROM migration_modbus_devices WHERE device_type = 'BATTERY';
INSERT INTO smart_meters (id, created_at, name, pv_site_id, is_grid_meter)
SELECT device_id, CURRENT_TIMESTAMP, CONCAT('Migrated grid meter (', ip_address, ')'), site_id, TRUE FROM migration_modbus_devices WHERE device_type = 'SMART_METER';
INSERT INTO modbus_smart_meter SELECT device_id, ip_address, port, slave_id, modbus_config_name, 'pvsite' FROM migration_modbus_devices WHERE device_type = 'SMART_METER';

CREATE TEMPORARY TABLE migration_rest_devices AS
SELECT UUID() device_id, id site_id, host_name, port, api_token, restpvconfig_name, 'INVERTER' device_type FROM restpvsite
UNION ALL SELECT UUID(), id, host_name, port, api_token, restpvconfig_name, 'BATTERY' FROM restpvsite
UNION ALL SELECT UUID(), id, host_name, port, api_token, restpvconfig_name, 'SMART_METER' FROM restpvsite;

INSERT INTO inverters SELECT device_id, CURRENT_TIMESTAMP, CONCAT('Migrated inverter (', host_name, ')'), site_id, 0, TRUE FROM migration_rest_devices WHERE device_type = 'INVERTER';
INSERT INTO rest_inverter SELECT device_id, host_name, port, api_token, restpvconfig_name, 'ha_pvsite' FROM migration_rest_devices WHERE device_type = 'INVERTER';
INSERT INTO batteries SELECT d.device_id, CURRENT_TIMESTAMP, CONCAT('Migrated battery (', d.host_name, ')'), d.site_id, p.battery_capacity_wh, 0, 0 FROM migration_rest_devices d JOIN pv_sites p ON p.id = d.site_id WHERE d.device_type = 'BATTERY';
INSERT INTO rest_battery SELECT device_id, host_name, port, api_token, restpvconfig_name, 'ha_pvsite' FROM migration_rest_devices WHERE device_type = 'BATTERY';
INSERT INTO smart_meters SELECT device_id, CURRENT_TIMESTAMP, CONCAT('Migrated grid meter (', host_name, ')'), site_id, TRUE FROM migration_rest_devices WHERE device_type = 'SMART_METER';
INSERT INTO rest_smart_meter SELECT device_id, host_name, port, api_token, restpvconfig_name, 'ha_pvsite' FROM migration_rest_devices WHERE device_type = 'SMART_METER';

DELETE FROM modbuspvsite;
DELETE FROM restpvsite;
