package de.verdox.pv_miner_extensions.device.rest.battery;

import de.verdox.pv_miner.pvsite.battery.BatteryDataDTO;
import de.verdox.pv_miner.pvsite.battery.BatteryEntity;
import de.verdox.pv_miner_extensions.device.rest.RestEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
public class RestBattery extends BatteryEntity implements RestEntity<BatteryDataDTO> {
    private String hostName;
    private int port;
    private String apiToken;
    private String restConfigName;
    private String sectionKey;
}
