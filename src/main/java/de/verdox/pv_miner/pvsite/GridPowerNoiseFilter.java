package de.verdox.pv_miner.pvsite;

/**
 * Canonical deadband for grid-meter noise. Import and export values at or
 * below 50 W are not treated as energy flow by statistics or finance.
 */
public final class GridPowerNoiseFilter {
    public static final double DEADBAND_KW = 0.05;

    private GridPowerNoiseFilter() {
    }

    public static double directionalKw(double powerKw) {
        if (!Double.isFinite(powerKw) || powerKw <= DEADBAND_KW) return 0;
        return powerKw;
    }

    public static double signedKw(double powerKw) {
        if (!Double.isFinite(powerKw) || Math.abs(powerKw) <= DEADBAND_KW) return 0;
        return powerKw;
    }
}
