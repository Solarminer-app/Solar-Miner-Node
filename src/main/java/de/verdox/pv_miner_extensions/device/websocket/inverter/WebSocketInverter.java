package de.verdox.pv_miner_extensions.device.websocket.inverter;

import de.verdox.pv_miner.pvsite.inverter.InverterDataDTO;
import de.verdox.pv_miner.pvsite.inverter.InverterEntity;
import de.verdox.pv_miner_extensions.device.websocket.WebSocketEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity
public class WebSocketInverter extends InverterEntity implements WebSocketEntity<InverterDataDTO> {
    private String url;
    private String apiToken;
    private String messageConfigName;
    private String sectionKey;
}
