package de.verdox.pv_miner.core.proxy.fee;

import java.util.Set;

public record FeeResponse(
        String coin,
        String referral,
        double totalDevFee,
        double userDiscount,
        Set<FeeTarget> targets
) {
}
