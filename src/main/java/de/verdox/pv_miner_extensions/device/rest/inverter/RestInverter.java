package de.verdox.pv_miner_extensions.device.rest.inverter;

import de.verdox.pv_miner.pvsite.inverter.InverterDataDTO;
import de.verdox.pv_miner.pvsite.inverter.InverterEntity;
import de.verdox.pv_miner_extensions.device.rest.RestEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
public class RestInverter extends InverterEntity implements RestEntity<InverterDataDTO> {
    private String hostName;
    private int port;
    private String apiToken;
    private String restConfigName;
    private String sectionKey;
}
