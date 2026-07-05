package de.verdox.pv_miner.miningcontroller;

import java.time.Instant;

record MinerLock(
        Instant stateUnlockTime,
        Instant powerUnlockTime,
        Instant lockStartTime,
        double expectedPowerWatts
) {
}
