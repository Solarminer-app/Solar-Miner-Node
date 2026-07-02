package de.verdox.pv_miner_extensions.agent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.verdox.pv_miner.miner.MinerEntityController;
import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.MiningOS;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
public class AgentMinerEntity extends MinerEntity<MinerEntityController> {
    private String host;
    private int port;

    @Override
    public String getIP() {
        return host;
    }

    @Override
    public MiningOS getOS() {
        return MiningOS.AGENT;
    }

    @Override
    @JsonIgnore
    @Transient
    public MinerApiClient.MinerDetails getDetails() {
        return new MinerApiClient.MinerDetails(getId(), getHost(), getPort(), "", "");
    }
}
