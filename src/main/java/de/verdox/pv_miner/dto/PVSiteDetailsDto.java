package de.verdox.pv_miner.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PVSiteDetailsDto(
        UUID siteId,
        String name,
        String timeZone,
        LocalDate setupDate,
        MoneyDto pvCost,
        double totalPeakPowerKw,
        int totalPanels,
        int totalGroups,
        List<PanelGroupDto> panelGroups,
        List<MinerCostDto> miners,
        List<PriceDto> feedInTariffs,
        List<PriceDto> electricityPrices
) {
    public record PanelGroupDto(
            UUID id,
            String name,
            double latitude,
            double longitude,
            int panelCount,
            double powerPerPanelWatts,
            double peakPowerKw,
            double azimuthDegrees,
            double slopeDegrees
    ) {
    }

    public record MinerCostDto(UUID id, String name, String ipAddress, MoneyDto cost) {
    }

    public record PriceDto(LocalDate validFrom, MoneyDto price) {
    }
}
