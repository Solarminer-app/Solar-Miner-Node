package de.verdox.pv_miner.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SetupRequests {
    private SetupRequests() {
    }

    public record ProviderSelection(String providerId, Map<String, String> values, List<String> selectedSectionKeys) {
        public ProviderSelection {
            values = values == null ? Map.of() : Map.copyOf(values);
            selectedSectionKeys = selectedSectionKeys == null ? List.of() : List.copyOf(selectedSectionKeys);
        }

        public ProviderSelection(String providerId, Map<String, String> values) {
            this(providerId, values, List.of());
        }
    }

    public record PvDeviceSectionDto(String sectionKey, String templateId, String name, String deviceType) {
    }

    public record PvDeviceProfileDto(String providerId, String profileName, List<PvDeviceSectionDto> sections) {
    }

    public record PvDiscoveryRequest(String providerId, String subnetPrefix, Integer port, Integer slaveId) {
    }

    public record DiscoveredPvDeviceDto(
            String providerId,
            String host,
            int port,
            int slaveId,
            String profileName,
            boolean requiresAuth,
            List<PvDeviceSectionDto> sections
    ) {
    }

    public record PvDevicePreviewRequest(String providerId, Map<String, String> values) {
        public PvDevicePreviewRequest {
            values = values == null ? Map.of() : Map.copyOf(values);
        }
    }

    public record PvDevicePreviewDto(List<PvDeviceSectionPreviewDto> sections) {
        public PvDevicePreviewDto {
            sections = sections == null ? List.of() : List.copyOf(sections);
        }
    }

    public record PvDeviceSectionPreviewDto(
            String sectionKey,
            String deviceType,
            String name,
            String availability,
            List<PvDevicePreviewValueDto> values,
            String message
    ) {
    }

    public record PvDevicePreviewValueDto(String key, double value, String unit) {
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
            List<ProviderSelection> pvDevices,
            List<PanelGroupInput> panelGroups,
            List<ProviderSelection> miningPools
    ) {
        public CreateSetupRequest {
            pvDevices = pvDevices == null ? List.of() : List.copyOf(pvDevices);
            panelGroups = panelGroups == null ? List.of() : List.copyOf(panelGroups);
            miningPools = miningPools == null ? List.of() : List.copyOf(miningPools);
        }

        public List<ProviderSelection> effectivePvDevices() {
            return pvDevices.isEmpty() && pvSource != null ? List.of(pvSource) : pvDevices;
        }
    }

    public record ProviderValidationDto(boolean valid, String message) {
    }

    public record SetupCreatedDto(UUID siteId) {
    }
}
