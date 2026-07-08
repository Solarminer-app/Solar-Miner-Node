package de.verdox.pv_miner.miningcontroller;

import java.time.Instant;

public record MinerLock(
        Instant runStateUnlockTime,
        Instant powerChangeUnlockTime,
        Instant coolingCooldownUnlockTime,
        double expectedPowerWatts
) {
}
