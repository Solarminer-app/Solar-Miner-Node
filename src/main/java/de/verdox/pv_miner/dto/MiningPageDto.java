package de.verdox.pv_miner.dto;

import de.verdox.pv_miner.shared.dto.DevFeeOverviewDto;
import java.util.List;
import java.util.UUID;

public record MiningPageDto(
        String siteName,
        int totalClusters,
        int activeClusters,
        int totalMiners,
        double totalHashrateThs,
        List<ClusterDto> clusters,
        List<MinerDto> connectedMiners,
        List<MinerDto> unassignedMiners,
        List<PoolDto> connectedPools,
        DevFeeOverviewDto devFee
) {
    public record ClusterDto(
            String name,
            boolean running,
            List<MinerDto> miners
    ) {
    }

    public record MinerDto(
            UUID id,
            String name,
            String ipAddress,
            String model,
            String status,
            double hashrateThs,
            long powerWatts,
            double temperatureCelsius,
            String pool,
            long hardwareMinPowerWatts,
            long hardwareDefaultPowerWatts,
            long hardwareMaxPowerWatts,
            long configuredMinPowerWatts,
            long configuredMaxPowerWatts,
            boolean supportsDynamicPowerScaling,
            Integer powerStepWatts,
            Integer minimumRunMinutes,
            Integer minimumIdleMinutes,
            Integer powerChangeLockMinutes
    ) {
    }

    public record PoolDto(
            UUID id,
            String type,
            String name,
            String stratumUrl
    ) {
    }

    public record DiscoveredMinerDto(
            String model,
            String ipAddress,
            String operatingSystem
    ) {
    }
}
