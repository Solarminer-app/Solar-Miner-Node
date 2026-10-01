package de.verdox.pv_miner.core.miner.twentyoneenergy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Only documented stable fields are typed. Summary and temperature remain tolerant JSON trees. */
public final class TwentyOneEnergyDtos {
    private TwentyOneEnergyDtos() { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusResponse(boolean operational) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SystemStatusResponse(String model, String productId, String version, boolean isPaired) { }

    public record HeaterEnabledDto(boolean enabled, Boolean notifyApp) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PoolConfigDto(String poolUrl1, String username1, String poolUrl2, String username2) { }
}
