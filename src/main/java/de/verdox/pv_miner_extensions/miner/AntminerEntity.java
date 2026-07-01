package de.verdox.pv_miner_extensions.miner;

import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.MinerEntityController;
import de.verdox.pv_miner.miner.MiningOS;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
public class AntminerEntity extends MinerEntity<MinerEntityController> {
    private String host, username, password;
    private int port = 80;

    @Override
    public String getIP() {
        return host;
    }

    @Override
    public MiningOS getOS() {
        return MiningOS.ANTMINER_STOCK_OS;
    }

    @Override
    @Transient
    public MinerApiClient.MinerDetails getDetails() {
        return new MinerApiClient.MinerDetails(getId(), getHost(), getPort(), getUsername(), getPassword());
    }

}
