package de.verdox.solarminer.pcagent.lowlevel.sensor;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.solarminer.pcagent.lowlevel.HardwareIdentityService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;

@Configuration
public class SensorConfiguration {

    private static final Logger LOGGER = Logger.getLogger(SensorConfiguration.class.getName());

    @Bean
    public HardwareSensorReader hardwareSensorReader(ObjectMapper objectMapper, HardwareIdentityService hardwareIdentityService) {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("linux") || os.contains("nix")) {
            if (Files.exists(Paths.get("/sys/class/powercap/intel-rapl/intel-rapl:0/energy_uj"))) {
                LOGGER.info("Linux OS detected. Hardware Power Sensor (RAPL) available. Using LinuxSensorReader.");
                return new LinuxSensorReader();
            } else {
                LOGGER.info("Linux OS detected, but no RAPL sensor found. Falling back to estimation.");
            }
        } else if (os.contains("win")) {
            LOGGER.info("Windows OS detected. Attempting to use LibreHardwareMonitor via port 8085.");
            return new WindowsLhmSensorReader(hardwareIdentityService, objectMapper);
        }

        LOGGER.warning("No native hardware sensors could be attached. Using Fallback mode.");
        return new FallbackSensorReader(() -> 0.0);
    }
}