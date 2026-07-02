package de.verdox.pv_miner_extensions.inverter.rest;

import de.verdox.pv_miner.pvsite.PVSiteEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class RestPVSite extends PVSiteEntity {
    private String hostName;
    private int port;
    private String apiToken;
    private String restPVConfigName;
}
