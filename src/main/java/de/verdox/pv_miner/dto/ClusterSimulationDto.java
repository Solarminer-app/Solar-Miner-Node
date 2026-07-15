package de.verdox.pv_miner.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ClusterSimulationDto(
        String sourceType,
        String sourceLabel,
        LocalDate simulatedDate,
        double clusterCapacityWatts,
        List<SimulationPointDto> points,
        SimulationSummaryDto summary
) {
    public record SimulationPointDto(
            Instant timestamp,
            double pvPowerKw,
            double loadPowerKw,
            double batterySocPercent,
            double minerPowerKw,
            double potentialSurplusKw,
            double targetPowerWatts,
            double allocatedPowerWatts,
            int activeMiners,
            String activeMode
    ) {
    }

    public record SimulationSummaryDto(
            double simulatedEnergyKwh,
            double pvPoweredEnergyKwh,
            double estimatedGridEnergyKwh,
            double peakTargetWatts,
            int modeChanges,
            long activeMinutes,
            String mostActiveMode
    ) {
    }
}
