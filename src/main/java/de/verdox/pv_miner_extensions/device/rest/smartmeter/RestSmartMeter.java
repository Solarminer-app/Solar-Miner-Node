package de.verdox.pv_miner_extensions.device.rest.smartmeter;

import de.verdox.pv_miner.pvsite.smartmeter.SmartMeterDataDTO;
import de.verdox.pv_miner.pvsite.smartmeter.SmartMeterEntity;
import de.verdox.pv_miner_extensions.device.rest.RestEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
public class RestSmartMeter extends SmartMeterEntity implements RestEntity<SmartMeterDataDTO> {
    private String hostName;
    private int port;
    private String apiToken;
    private String restConfigName;
    private String sectionKey;
}
