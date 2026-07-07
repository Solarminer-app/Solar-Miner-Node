package de.verdox.pv_miner.miningcontroller;

import java.time.Instant;

public record MinerLock(
        Instant stateUnlockTime,
        Instant powerUnlockTime,
        Instant lockStartTime,
        double expectedPowerWatts
) {
}
