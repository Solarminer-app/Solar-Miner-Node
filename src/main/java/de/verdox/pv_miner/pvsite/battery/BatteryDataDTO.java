package de.verdox.pv_miner.pvsite.battery;

import de.verdox.pv_miner.influx.QueryResult;

import java.time.Instant;

/**
 * Data Transfer Object representing the real-time status of a connected battery system.
 * This DTO includes the current state of charge and dynamic hardware limits defined by the
 * Battery Management System (BMS), which are crucial for deciding whether to mine or charge.
 */
public record BatteryDataDTO(
        /**
         * The current State of Charge (SoC) of the battery, ranging from 0.0 to 100.0 percent.
         */
        double stateOfChargePct,

        /**
         * The current active power flowing into or out of the battery, in Watts.
         * IMPORTANT: Positive values (> 0) indicate charging. Negative values (< 0) indicate discharging.
         */
        int currentPowerW,

        // --- Dynamic Hardware Limits (BMS) ---

        /**
         * The maximum power the battery is technically allowed to absorb right now, in Watts.
         * This value dynamically drops when the battery is nearly full or too cold.
         */
        int currentMaxChargePowerW,

        /**
         * The maximum power the battery is technically allowed to provide right now, in Watts.
         */
        int currentMaxDischargePowerW,

        // --- Health & Diagnostics ---

        /**
         * The State of Health (SoH) of the battery, representing capacity degradation over time (0.0 to 100.0 percent).
         */
        double stateOfHealthPct,

        /**
         * The current temperature of the battery cells, in degrees Celsius.
         */
        double temperatureC
) implements QueryResult {
    public static final BatteryDataDTO DEFAULT = new BatteryDataDTO(0,0,0,0,0,0);
}