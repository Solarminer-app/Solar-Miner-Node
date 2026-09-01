package de.verdox.pv_miner.dto;

import java.util.List;
import java.util.UUID;

public final class MiningPageRequests {
    private MiningPageRequests() {
    }

    public record MinerSelectionRequest(List<UUID> minerIds) {
        public MinerSelectionRequest {
            minerIds = minerIds == null ? List.of() : List.copyOf(minerIds);
        }
    }

    public record MinerConnectionRequest(
            String model,
            String ipAddress,
            String operatingSystem,
            String username,
            String password
    ) {
    }

    public record PoolConnectionRequest(
            String type,
            String accessToken
    ) {
    }

    public record MinerPowerTargetRequest(
            long minimumPowerWatts,
            long maximumPowerWatts,
            boolean electricalRiskAcknowledged
    ) {
    }

    public record MinerEfficiencySettingsRequest(
            Integer dispatchPriority,
            Double nominalEfficiencyJTh
    ) {
    }

    public record ReferralRequest(String referralCode) {
    }

    /**
     * Data-sharing settings for the anonymized network telemetry: the opt-in
     * flag plus the location privacy level ('OFF'|'COUNTRY'|'REGIONAL'|'AREA'),
     * the country (for COUNTRY) and approximate coordinates (for REGIONAL/AREA).
     * Null fields keep the previous value.
     */
    public record TelemetryOptInRequest(
            Boolean enabled,
            String geoLevel,
            String country,
            Double lat,
            Double lng
    ) {
        public TelemetryOptInRequest(Boolean enabled) {
            this(enabled, null, null, null, null);
        }
    }
}
