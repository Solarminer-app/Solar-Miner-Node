package de.verdox.pv_miner.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MinerDetailsPageDto(
        UUID id,
        String name,
        String ipAddress,
        String operatingSystem,
        String model,
        String uid,
        String macAddress,
        String status,
        String clusterName,
        String configuredPool,
        LiveMinerStatsDto live,
        MinerHardwareDto hardware,
        MinerEfficiencyStrategyDto efficiencyStrategy,
        List<MinerPoolDto> pools,
        List<MinerWorkerDto> workers,
        MinerHistorySummaryDto historySummary,
        List<MinerHistoryPointDto> history
) {
    public record LiveMinerStatsDto(
            double hashrateThs,
            long powerUsageWatts,
            long powerTargetWatts,
            double temperatureCelsius,
            double efficiencyJTh
    ) {
    }

    public record MinerHardwareDto(
            long hardwareMinPowerWatts,
            long hardwareDefaultPowerWatts,
            long hardwareMaxPowerWatts,
            long configuredMinPowerWatts,
            long configuredMaxPowerWatts,
            Integer powerStepWatts,
            Integer minimumRunMinutes,
            Integer minimumIdleMinutes,
            Integer powerChangeLockMinutes
    ) {
    }

    public record MinerEfficiencyStrategyDto(
            Integer dispatchPriority,
            Double nominalEfficiencyJTh,
            Double effectiveEfficiencyJTh,
            String effectiveSource,
            Integer effectivePowerTargetBucketWatts,
            long effectiveSampleCount,
            List<MinerEfficiencyProfileDto> learnedProfiles
    ) {
    }

    public record MinerEfficiencyProfileDto(
            int powerTargetBucketWatts,
            double learnedEfficiencyJTh,
            long sampleCount,
            Double averageTemperatureCelsius,
            Instant lastObservedAt,
            boolean controllerReady
    ) {
    }

    public record MinerPoolDto(String url, String username) {
    }

    public record MinerWorkerDto(
            String name,
            String algorithm,
            String status,
            double hashrateThs,
            double temperatureCelsius,
            long powerUsageWatts
    ) {
    }

    public record MinerHistorySummaryDto(
            Instant from,
            Instant to,
            int dataPoints,
            double averageHashrateThs,
            double averagePowerWatts,
            double averageEfficiencyJTh,
            double maximumTemperatureCelsius,
            double estimatedEnergyKwh
    ) {
    }

    public record MinerHistoryPointDto(
            Instant timestamp,
            Double hashrateThs,
            Double powerUsageWatts,
            Double powerTargetWatts,
            Double temperatureCelsius,
            Double efficiencyJTh
    ) {
    }
}
