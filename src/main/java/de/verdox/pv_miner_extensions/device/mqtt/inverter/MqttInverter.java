package de.verdox.pv_miner_extensions.device.mqtt.inverter;

import de.verdox.pv_miner.pvsite.inverter.InverterDataDTO;
import de.verdox.pv_miner.pvsite.inverter.InverterEntity;
import de.verdox.pv_miner_extensions.device.mqtt.MqttEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity
public class MqttInverter extends InverterEntity implements MqttEntity<InverterDataDTO> {
    private String brokerUri;
    private String clientId;
    private String username;
    private String password;
    private String messageConfigName;
    private String sectionKey;
}
