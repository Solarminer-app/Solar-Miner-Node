package de.verdox.pv_miner.dto;

import java.util.List;

public record SetupCatalogDto(
        long currentSiteCount,
        int siteLimit,
        boolean limitReached,
        List<SetupOptionDto> pvSources,
        List<SetupOptionDto> miningPools
) {
    public record SetupOptionDto(
            String id,
            String kind,
            String label,
            String description,
            boolean recommended,
            List<SetupFieldDto> fields
    ) {
    }

    public record SetupFieldDto(
            String key,
            String label,
            String helpText,
            String type,
            boolean required,
            String defaultValue,
            Double minimum,
            Double maximum,
            List<SetupSelectOptionDto> options
    ) {
    }

    public record SetupSelectOptionDto(String value, String label) {
    }
}
