package de.verdox.pv_miner_extensions.device.mqtt.smartmeter;

import de.verdox.pv_miner.pvsite.smartmeter.SmartMeterDataDTO;
import de.verdox.pv_miner.pvsite.smartmeter.SmartMeterEntity;
import de.verdox.pv_miner_extensions.device.mqtt.MqttEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity
public class MqttSmartMeter extends SmartMeterEntity implements MqttEntity<SmartMeterDataDTO> {
    private String brokerUri;
    private String clientId;
    private String username;
    private String password;
    private String messageConfigName;
    private String sectionKey;
}
