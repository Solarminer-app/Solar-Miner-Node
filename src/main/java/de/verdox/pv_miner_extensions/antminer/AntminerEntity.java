package de.verdox.pv_miner_extensions.antminer;

import de.verdox.pv_miner.miner.MinerEntity;
import jakarta.persistence.Entity;

@Entity
public class AntminerEntity extends MinerEntity<Antminer> {
    private String host, username, password;
    private int port = 80;

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
}
