package de.verdox.pv_miner_extensions.device.websocket.battery;

import de.verdox.pv_miner.pvsite.battery.BatteryDataDTO;
import de.verdox.pv_miner.pvsite.battery.BatteryEntity;
import de.verdox.pv_miner_extensions.device.websocket.WebSocketEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity
public class WebSocketBattery extends BatteryEntity implements WebSocketEntity<BatteryDataDTO> {
    private String url;
    private String apiToken;
    private String messageConfigName;
    private String sectionKey;
}
