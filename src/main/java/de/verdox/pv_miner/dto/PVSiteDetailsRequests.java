package de.verdox.pv_miner.dto;

import java.time.LocalDate;

public final class PVSiteDetailsRequests {
    private PVSiteDetailsRequests() {
    }

    public record SiteConfigurationRequest(
            LocalDate setupDate,
            String timeZone,
            double pvCost,
            String currency
    ) {
    }

    public record PanelGroupRequest(
            String name,
            double latitude,
            double longitude,
            int panelCount,
            double powerPerPanelWatts,
            double azimuthDegrees,
            double slopeDegrees
    ) {
    }

    public record PriceRequest(LocalDate validFrom, double amount, String currency) {
    }

    public record MinerCostRequest(double amount, String currency) {
    }
}
