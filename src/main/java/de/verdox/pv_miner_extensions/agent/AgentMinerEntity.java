package de.verdox.pv_miner_extensions.agent;

import de.verdox.pv_miner.miner.MinerEntity;
import jakarta.persistence.Entity;

@Entity
public class AgentMinerEntity extends MinerEntity<AgentMiner> {
    private String host;
    private int port;

    public String getHost() {
        return host;
    }

    @Override
    public String getIP() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
