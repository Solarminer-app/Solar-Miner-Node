package de.verdox.solarminer.pcagent.lowlevel.sensor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LinuxSensorReader implements HardwareSensorReader {

    private static final Logger LOGGER = Logger.getLogger(LinuxSensorReader.class.getName());

    private static final Path RAPL_ENERGY_FILE = Paths.get("/sys/class/powercap/intel-rapl/intel-rapl:0/energy_uj");
    private static final Path THERMAL_FILE = Paths.get("/sys/class/thermal/thermal_zone0/temp");

    private long lastTimeMs = 0;
    private long lastEnergyUj = 0;
    private double lastCalculatedWatts = 0.0;

    @Override
    public synchronized double getCpuPowerWatts() {
        if (!Files.exists(RAPL_ENERGY_FILE)) return -1.0;

        try {
            long currentEnergyUj = Long.parseLong(Files.readString(RAPL_ENERGY_FILE).trim());
            long currentTimeMs = System.currentTimeMillis();

            if (lastTimeMs == 0) {
                lastTimeMs = currentTimeMs;
                lastEnergyUj = currentEnergyUj;
                return 0.0;
            }

            long timeDeltaMs = currentTimeMs - lastTimeMs;
            long energyDeltaUj = currentEnergyUj - lastEnergyUj;

            if (timeDeltaMs < 50 || energyDeltaUj < 0) {
                return lastCalculatedWatts;
            }

            double energyDeltaJoules = energyDeltaUj / 1_000_000.0;
            double timeDeltaSeconds = timeDeltaMs / 1000.0;

            lastCalculatedWatts = energyDeltaJoules / timeDeltaSeconds;

            lastTimeMs = currentTimeMs;
            lastEnergyUj = currentEnergyUj;

            return lastCalculatedWatts;
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to read Linux CPU Power", e);
            return -1.0;
        }
    }

    @Override
    public double getCpuTemperatureCelsius() {
        if (!Files.exists(THERMAL_FILE)) return -1.0;

        try {
            long millidegrees = Long.parseLong(Files.readString(THERMAL_FILE).trim());
            return millidegrees / 1000.0;
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to read Linux CPU Temperature", e);
            return -1.0;
        }
    }

    @Override
    public boolean isAccurate() {
        return true;
    }
}
