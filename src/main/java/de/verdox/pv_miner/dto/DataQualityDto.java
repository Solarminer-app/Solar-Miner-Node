package de.verdox.pv_miner.dto;

import java.time.Instant;

public record DataQualityDto(
        String sourceStatus,
        String sourceType,
        Instant measuredAt,
        long ageSeconds
) {}
