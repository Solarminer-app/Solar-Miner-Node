package de.verdox.pv_miner_extensions.inverter.modbustcp;

import de.verdox.pv_miner.pvsite.PVSiteEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
public class ModbusPVSite extends PVSiteEntity {
    private String ipAddress;
    private int port;
    private int slaveId;
    private String modbusConfigName;
}
