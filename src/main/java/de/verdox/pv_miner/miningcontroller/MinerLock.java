package de.verdox.pv_miner.miningcontroller;

import java.time.Instant;

public record MinerLock(
        Instant runStateUnlockTime, // Lock for state change pause / resume
        Instant powerChangeUnlockTime,
        Instant coolingCooldownUnlockTime, // Lock for
        double expectedPowerWatts
) {
}
