package de.verdox.pv_miner.shared.dto;

import java.util.List;

public record DevFeeOverviewDto(
        boolean backendAvailable,
        double userPercentage,
        double totalFeePercentage,
        String referralCode,
        boolean referralValid,
        List<AllocationDto> allocations
) {
    public DevFeeOverviewDto {
        allocations = allocations == null ? List.of() : List.copyOf(allocations);
    }

    public record AllocationDto(
            String beneficiaryType,
            String beneficiaryName,
            double percentage
    ) {
    }
}
