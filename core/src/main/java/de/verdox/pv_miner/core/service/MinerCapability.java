package de.verdox.pv_miner.core.service;

/**
 * Operations a registered miner adapter may safely perform.
 *
 * <p>These are intentionally local to core. They describe the active adapter
 * registration, not a promise inferred solely from a {@code MiningOS} enum
 * value.</p>
 */
public enum MinerCapability {
    DYNAMIC_POWER_SCALING,
    POOL_CONFIGURATION,
    AGENT_COIN_CONFIGURATION,
    TWENTY_ONE_ENERGY_CALIBRATION
}
