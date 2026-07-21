package de.verdox.pv_miner_extensions.device.mqtt.battery;

import de.verdox.pv_miner.pvsite.battery.BatteryDataDTO;
import de.verdox.pv_miner.pvsite.battery.BatteryEntity;
import de.verdox.pv_miner_extensions.device.mqtt.MqttEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity
public class MqttBattery extends BatteryEntity implements MqttEntity<BatteryDataDTO> {
    private String brokerUri;
    private String clientId;
    private String username;
    private String password;
    private String messageConfigName;
    private String sectionKey;
}
