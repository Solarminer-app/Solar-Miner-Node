package de.verdox.pv_miner.core.proxy.fee;

public record FeeTarget(
        String targetId,
        String poolAddress,
        String workerName,
        String password,
        double percentage
) {
}