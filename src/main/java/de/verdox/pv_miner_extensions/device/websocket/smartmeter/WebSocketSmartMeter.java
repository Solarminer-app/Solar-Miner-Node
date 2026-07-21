package de.verdox.pv_miner_extensions.device.websocket.smartmeter;

import de.verdox.pv_miner.pvsite.smartmeter.SmartMeterDataDTO;
import de.verdox.pv_miner.pvsite.smartmeter.SmartMeterEntity;
import de.verdox.pv_miner_extensions.device.websocket.WebSocketEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity
public class WebSocketSmartMeter extends SmartMeterEntity implements WebSocketEntity<SmartMeterDataDTO> {
    private String url;
    private String apiToken;
    private String messageConfigName;
    private String sectionKey;
}
