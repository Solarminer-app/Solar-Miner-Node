package de.verdox.pv_miner.dto;

import java.time.LocalDate;
import java.util.List;

public final class ClusterConfigRequests {
    private ClusterConfigRequests() {
    }

    public record SaveClusterConfigRequest(
            String originalName,
            String name,
            List<ClusterConfigDto.OperatingModeDto> modes
    ) {
    }

    public record SimulateClusterConfigRequest(
            String clusterName,
            List<ClusterConfigDto.OperatingModeDto> modes,
            String sourceType,
            LocalDate historicalDate,
            String preset,
            Integer intervalMinutes
    ) {
    }
}
