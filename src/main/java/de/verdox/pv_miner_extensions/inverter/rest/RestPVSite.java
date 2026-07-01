package de.verdox.pv_miner_extensions.inverter.rest;

import de.verdox.pv_miner.pvsite.PVSiteEntity;
import jakarta.persistence.Entity;

@Entity
public class RestPVSite extends PVSiteEntity {

    private String hostName;
    private int port;
    private String apiToken;
    private String restPVConfigName;

    public String getHostName() {
        return hostName;
    }

    public RestPVSite setHostName(String hostName) {
        this.hostName = hostName;
        return this;
    }

    public int getPort() {
        return port;
    }

    public RestPVSite setPort(int port) {
        this.port = port;
        return this;
    }

    public String getApiToken() {
        return apiToken;
    }

    public RestPVSite setApiToken(String apiToken) {
        this.apiToken = apiToken;
        return this;
    }

    public String getRestPVConfigName() {
        return restPVConfigName;
    }

    public RestPVSite setRestPVConfigName(String restPVConfigName) {
        this.restPVConfigName = restPVConfigName;
        return this;
    }
}
