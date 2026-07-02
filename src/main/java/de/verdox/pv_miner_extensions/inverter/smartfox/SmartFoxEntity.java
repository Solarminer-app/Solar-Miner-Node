package de.verdox.pv_miner_extensions.inverter.smartfox;

import de.verdox.pv_miner.pvsite.PVSiteEntity;
import jakarta.persistence.Entity;

@Entity
@Deprecated
public class SmartFoxEntity extends PVSiteEntity {

    private String ipv4Host;

    public String getIpv4Host() {
        return ipv4Host;
    }

    public void setIpv4Host(String ipv4Host) {
        this.ipv4Host = ipv4Host;
    }


}
