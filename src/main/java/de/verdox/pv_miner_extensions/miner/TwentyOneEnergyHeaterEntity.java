package de.verdox.pv_miner_extensions.miner;

import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.MinerEntityController;
import de.verdox.pv_miner.miner.MiningOS;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

/** Persistent local configuration only; telemetry and Wi-Fi/diagnostic data stay out of MariaDB. */
@Getter
@Setter
@Entity
public class TwentyOneEnergyHeaterEntity extends MinerEntity<MinerEntityController> {
    public enum IntegrationMode { MONITORING, CONTROL, MANAGED_MINING }

    private String host;
    private int port = 80;
    @Enumerated(EnumType.STRING)
    private IntegrationMode integrationMode = IntegrationMode.MONITORING;
    private String productId;
    private String apiVersion;
    private String firmwareVersion;

    @Override public String getIP() { return host; }
    @Override public MiningOS getOS() { return MiningOS.TWENTY_ONE_ENERGY; }
    @Override @Transient public MinerApiClient.MinerDetails getDetails() {
        // The 21control contract documents no credentials. Do not manufacture any.
        return new MinerApiClient.MinerDetails(getId(), host, port, "", "");
    }
}
