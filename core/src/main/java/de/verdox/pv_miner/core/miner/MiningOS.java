package de.verdox.pv_miner.core.miner;

public enum MiningOS {
    AGENT(false, true),
    ANTMINER_STOCK_OS,
    CANAAN_STOCK_OS,
    WHATSMINER_STOCK_OS(false, true),
    INNOSILICON_STOCK_OS,

    BRAIINS(true, true),
    VNISH(true, true),
    LUX_OS(true, true),
    HIVEON_ASIC(true, true),
    BIXBIT(true, true),

    HIVE_OS(true, true),
    MS_OS(true, true),
    RAVE_OS(true, true),
    ;
    private final boolean supportsNativeSplitting;
    private final boolean supportsDynamicPowerScaling;

    MiningOS(boolean supportsNativeSplitting, boolean supportsDynamicPowerScaling) {
        this.supportsNativeSplitting = supportsNativeSplitting;
        this.supportsDynamicPowerScaling = supportsDynamicPowerScaling;
    }

    MiningOS() {
        this(false, false);
    }

    public boolean supportsDynamicPowerScaling() {
        return supportsDynamicPowerScaling;
    }

    public boolean supportsNativeSplitting() {
        return supportsNativeSplitting;
    }
}
