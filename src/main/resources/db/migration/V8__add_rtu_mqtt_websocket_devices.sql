CREATE TABLE IF NOT EXISTS modbus_rtu_inverter (
    id UUID NOT NULL, serial_port VARCHAR(255), baud_rate INT NOT NULL, data_bits INT NOT NULL,
    stop_bits INT NOT NULL, parity VARCHAR(16), slave_id INT NOT NULL,
    modbus_config_name VARCHAR(255), section_key VARCHAR(255),
    CONSTRAINT pk_modbus_rtu_inverter PRIMARY KEY (id),
    CONSTRAINT fk_modbus_rtu_inverter_id FOREIGN KEY (id) REFERENCES inverters (id)
);

CREATE TABLE IF NOT EXISTS modbus_rtu_battery (
    id UUID NOT NULL, serial_port VARCHAR(255), baud_rate INT NOT NULL, data_bits INT NOT NULL,
    stop_bits INT NOT NULL, parity VARCHAR(16), slave_id INT NOT NULL,
    modbus_config_name VARCHAR(255), section_key VARCHAR(255),
    CONSTRAINT pk_modbus_rtu_battery PRIMARY KEY (id),
    CONSTRAINT fk_modbus_rtu_battery_id FOREIGN KEY (id) REFERENCES batteries (id)
);

CREATE TABLE IF NOT EXISTS modbus_rtu_smart_meter (
    id UUID NOT NULL, serial_port VARCHAR(255), baud_rate INT NOT NULL, data_bits INT NOT NULL,
    stop_bits INT NOT NULL, parity VARCHAR(16), slave_id INT NOT NULL,
    modbus_config_name VARCHAR(255), section_key VARCHAR(255),
    CONSTRAINT pk_modbus_rtu_smart_meter PRIMARY KEY (id),
    CONSTRAINT fk_modbus_rtu_smart_meter_id FOREIGN KEY (id) REFERENCES smart_meters (id)
);

CREATE TABLE IF NOT EXISTS mqtt_inverter (
    id UUID NOT NULL, broker_uri VARCHAR(512), client_id VARCHAR(255), username VARCHAR(255), password VARCHAR(1024),
    message_config_name VARCHAR(255), section_key VARCHAR(255),
    CONSTRAINT pk_mqtt_inverter PRIMARY KEY (id),
    CONSTRAINT fk_mqtt_inverter_id FOREIGN KEY (id) REFERENCES inverters (id)
);

CREATE TABLE IF NOT EXISTS mqtt_battery (
    id UUID NOT NULL, broker_uri VARCHAR(512), client_id VARCHAR(255), username VARCHAR(255), password VARCHAR(1024),
    message_config_name VARCHAR(255), section_key VARCHAR(255),
    CONSTRAINT pk_mqtt_battery PRIMARY KEY (id),
    CONSTRAINT fk_mqtt_battery_id FOREIGN KEY (id) REFERENCES batteries (id)
);

CREATE TABLE IF NOT EXISTS mqtt_smart_meter (
    id UUID NOT NULL, broker_uri VARCHAR(512), client_id VARCHAR(255), username VARCHAR(255), password VARCHAR(1024),
    message_config_name VARCHAR(255), section_key VARCHAR(255),
    CONSTRAINT pk_mqtt_smart_meter PRIMARY KEY (id),
    CONSTRAINT fk_mqtt_smart_meter_id FOREIGN KEY (id) REFERENCES smart_meters (id)
);

CREATE TABLE IF NOT EXISTS web_socket_inverter (
    id UUID NOT NULL, url VARCHAR(1024), api_token VARCHAR(1024), message_config_name VARCHAR(255), section_key VARCHAR(255),
    CONSTRAINT pk_web_socket_inverter PRIMARY KEY (id),
    CONSTRAINT fk_web_socket_inverter_id FOREIGN KEY (id) REFERENCES inverters (id)
);

CREATE TABLE IF NOT EXISTS web_socket_battery (
    id UUID NOT NULL, url VARCHAR(1024), api_token VARCHAR(1024), message_config_name VARCHAR(255), section_key VARCHAR(255),
    CONSTRAINT pk_web_socket_battery PRIMARY KEY (id),
    CONSTRAINT fk_web_socket_battery_id FOREIGN KEY (id) REFERENCES batteries (id)
);

CREATE TABLE IF NOT EXISTS web_socket_smart_meter (
    id UUID NOT NULL, url VARCHAR(1024), api_token VARCHAR(1024), message_config_name VARCHAR(255), section_key VARCHAR(255),
    CONSTRAINT pk_web_socket_smart_meter PRIMARY KEY (id),
    CONSTRAINT fk_web_socket_smart_meter_id FOREIGN KEY (id) REFERENCES smart_meters (id)
);
