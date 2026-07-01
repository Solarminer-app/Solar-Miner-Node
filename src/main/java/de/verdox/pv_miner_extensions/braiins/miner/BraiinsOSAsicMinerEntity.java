package de.verdox.pv_miner_extensions.braiins.miner;

import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.MiningOS;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
public class BraiinsOSAsicMinerEntity extends MinerEntity<BrainsOSMiner> {
    private String username;
    private String password;
    private String host;
    private int port;

    @Override
    public String getIP() {
        return host;
    }

    @Override
    @Transient
    public MiningOS getOS() {
        return MiningOS.BRAIINS;
    }

    @Override
    @Transient
    public MinerApiClient.MinerDetails getDetails() {
        return new MinerApiClient.MinerDetails(getId(), getHost(), getPort(), getUsername(), getPassword());
    }
}
