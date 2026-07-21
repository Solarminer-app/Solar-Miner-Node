package de.verdox.pv_miner.pvsite.smartmeter;

import de.verdox.pv_miner.influx.QueryResult;

/**
 * Data Transfer Object representing the real-time readings of a grid-connected Smart Meter.
 * This is the most critical entity for the mining algorithm, as it represents the net energy
 * balance of the entire site (production minus house consumption).
 */
public record SmartMeterDataDTO(
        /**
         * The total net active power exchanged with the public grid, in Watts.
         * IMPORTANT: Positive values (> 0) indicate grid import (buying electricity).
         * Negative values (< 0) indicate grid export (excess solar power - trigger for mining).
         */
        int totalActivePowerW,

        // --- Energy Counters ---

        /**
         * The absolute total amount of energy imported from the grid over the meter's lifetime, in Watt-hours.
         */
        long totalImportedWh,

        /**
         * The absolute total amount of energy exported to the grid over the meter's lifetime, in Watt-hours.
         */
        long totalExportedWh,

        // --- Phase Details (L1, L2, L3) ---

        /**
         * The active power on Phase 1, in Watts.
         */
        int powerL1W,

        /**
         * The active power on Phase 2, in Watts.
         */
        int powerL2W,

        /**
         * The active power on Phase 3, in Watts.
         */
        int powerL3W,

        /**
         * The voltage measured on Phase 1, in Volts.
         */
        double voltageL1V,

        /**
         * The voltage measured on Phase 2, in Volts.
         */
        double voltageL2V,

        /**
         * The voltage measured on Phase 3, in Volts.
         */
        double voltageL3V
) implements QueryResult {
    public static final SmartMeterDataDTO DEFAULT = new SmartMeterDataDTO(0,0,0,0,0,0,0,0,0);
}
