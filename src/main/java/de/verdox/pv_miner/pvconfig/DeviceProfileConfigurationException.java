package de.verdox.pv_miner.pvconfig;

/**
 * Signals that a persisted PV device references a profile or profile section
 * that is no longer available. Monitoring pauses until the device is saved
 * with a valid profile assignment.
 */
public class DeviceProfileConfigurationException extends IllegalStateException {
    public DeviceProfileConfigurationException(String message) {
        super(message);
    }
}
