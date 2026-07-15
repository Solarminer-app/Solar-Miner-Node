package de.verdox.pv_miner.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SetupRequests {
    private SetupRequests() {
    }

    public record ProviderSelection(String providerId, Map<String, String> values) {
        public ProviderSelection {
            values = values == null ? Map.of() : Map.copyOf(values);
        }
    }

    public record ProviderValidationRequest(Map<String, String> values) {
        public ProviderValidationRequest {
            values = values == null ? Map.of() : Map.copyOf(values);
        }
    }

    public record PanelGroupInput(
            String name,
            double latitude,
            double longitude,
            int panelCount,
            double powerPerPanelWatts,
            double azimuthDegrees,
            double slopeDegrees
    ) {
    }

    public record CreateSetupRequest(
            String name,
            LocalDate setupDate,
            String timeZone,
            String currency,
            double pvCost,
            double electricityPrice,
            double feedInTariff,
            int batteryCapacityWh,
            ProviderSelection pvSource,
            List<PanelGroupInput> panelGroups,
            List<ProviderSelection> miningPools
    ) {
        public CreateSetupRequest {
            panelGroups = panelGroups == null ? List.of() : List.copyOf(panelGroups);
            miningPools = miningPools == null ? List.of() : List.copyOf(miningPools);
        }
    }

    public record ProviderValidationDto(boolean valid, String message) {
    }

    public record SetupCreatedDto(UUID siteId) {
    }
}
