package de.verdox.pv_miner_extensions.braiins.miner;

import de.verdox.pv_miner.miner.MinerEntity;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
public class BraiinsOSAsicMinerEntity extends MinerEntity<BrainsOSMiner> {
    private String username;
    private String password;
    private String host;
    private int port;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public String getIP() {
        return host;
    }
}
