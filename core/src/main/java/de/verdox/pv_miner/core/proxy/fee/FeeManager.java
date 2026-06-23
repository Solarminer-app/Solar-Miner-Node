package de.verdox.pv_miner.core.proxy.fee;

import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class FeeManager {
    public static final String USER_TARGET_ID = "USER";

    private final Map<String, FeeTarget> targetMap = new HashMap<>();
    @Getter
    private final List<FeeTarget> feeTargets;
    private final double totalFeePercentage;

    public FeeManager(List<FeeTarget> feeTargets) {
        this.feeTargets = feeTargets;
        for (FeeTarget target : feeTargets) {
            this.targetMap.put(target.targetId(), target);
        }
        this.totalFeePercentage = feeTargets.stream().mapToDouble(FeeTarget::percentage).sum();
    }

    public FeeTarget getTarget(String targetId) {
        return targetMap.get(targetId);
    }

    public String rollNextJobTarget() {
        if (feeTargets.isEmpty() || totalFeePercentage <= 0) return USER_TARGET_ID;

        double roll = ThreadLocalRandom.current().nextDouble(100.0);

        if (roll >= totalFeePercentage) return USER_TARGET_ID;

        double currentThreshold = 0.0;
        for (FeeTarget target : feeTargets) {
            currentThreshold += target.percentage();
            if (roll < currentThreshold) return target.targetId();
        }

        return USER_TARGET_ID;
    }
}