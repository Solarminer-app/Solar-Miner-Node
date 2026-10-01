package de.verdox.pv_miner.core.miner.twentyoneenergy;

/** Capabilities are probed; version strings alone never grant a write capability. */
public record TwentyOneEnergyCapabilities(
        boolean healthReadable,
        boolean summaryReadable,
        boolean temperatureReadable,
        boolean powerLevelReadable,
        boolean powerLevelWritable,
        boolean powerTargetWattsReadable,
        boolean poolConfigReadable,
        boolean poolConfigWritable,
        boolean schedulesReadable,
        boolean diagnosticsAvailable,
        boolean verifiedPowerMap,
        boolean verifiedHashrateUnit,
        boolean verifiedProxyRouting
) {
    public static final TwentyOneEnergyCapabilities MONITORING = new TwentyOneEnergyCapabilities(
            true, false, false, false, false, false, false, false, false, false, false, false, false);
}
