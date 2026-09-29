package de.verdox.solarminer.pcagent.lowlevel.sensor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/** Best-effort Linux CPU sensors using kernel hwmon, thermal and powercap interfaces. */
public class LinuxSensorReader implements HardwareSensorReader {
    private static final Logger LOGGER = Logger.getLogger(LinuxSensorReader.class.getName());
    private static final Path HWMON = Path.of("/sys/class/hwmon");
    private static final Path THERMAL = Path.of("/sys/class/thermal");
    private static final Path POWERCAP = Path.of("/sys/class/powercap");

    private Path energyFile;
    private long previousEnergyUj = -1;
    private long previousTimeNs = -1;
    private double lastWatts = -1;

    @Override
    public synchronized double getCpuPowerWatts() {
        Path counter = findEnergyCounter();
        if (counter == null) return -1;
        try {
            long energy = Long.parseLong(Files.readString(counter).trim());
            long now = System.nanoTime();
            if (!counter.equals(energyFile)) {
                energyFile = counter;
                previousEnergyUj = energy;
                previousTimeNs = now;
                lastWatts = -1;
                return -1;
            }
            if (previousEnergyUj < 0 || now <= previousTimeNs) {
                previousEnergyUj = energy;
                previousTimeNs = now;
                return -1;
            }

            long delta = energy - previousEnergyUj;
            // A wrap is unlikely at the short polling cadence; a negative delta means
            // the counter reset, so establish a new baseline instead of inventing watts.
            if (delta < 0) {
                previousEnergyUj = energy;
                previousTimeNs = now;
                return -1;
            }
            lastWatts = (delta / 1_000_000.0) / ((now - previousTimeNs) / 1_000_000_000.0);
            previousEnergyUj = energy;
            previousTimeNs = now;
            return lastWatts;
        } catch (IOException | NumberFormatException e) {
            LOGGER.log(Level.FINE, "Could not read Linux energy counter " + counter, e);
            return -1;
        }
    }

    @Override
    public double getCpuTemperatureCelsius() {
        // Prefer package-labelled hwmon temperatures, then any CPU thermal zone.
        List<Path> inputs = tempInputs();
        Path preferred = inputs.stream().filter(path -> sensorLabel(path).contains("package")
                        || sensorLabel(path).contains("tctl") || sensorLabel(path).contains("cpu"))
                .findFirst().orElse(null);
        if (preferred == null) preferred = inputs.stream().findFirst().orElse(null);
        Double value = readTemperature(preferred);
        if (value != null) return value;

        try (Stream<Path> zones = Files.list(THERMAL)) {
            for (Path zone : zones.filter(path -> path.getFileName().toString().matches("thermal_zone\\d+"))
                    .sorted(Comparator.comparing(Path::toString)).toList()) {
                Double temp = readTemperature(zone.resolve("temp"));
                if (temp != null) return temp;
            }
        } catch (IOException | SecurityException ignored) { }
        return -1;
    }

    @Override
    public boolean isAccurate() {
        return true; // Values are direct kernel sensor readings; missing sensors return -1.
    }

    private Path findEnergyCounter() {
        if (energyFile != null && Files.isReadable(energyFile)) return energyFile;
        try (Stream<Path> roots = Files.list(POWERCAP)) {
            return roots.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).contains("rapl"))
                    .flatMap(this::energyFiles).filter(Files::isReadable).sorted().findFirst().orElse(null);
        } catch (IOException | SecurityException ignored) { return null; }
    }

    private Stream<Path> energyFiles(Path directory) {
        try {
            List<Path> children;
            try (Stream<Path> listing = Files.list(directory)) { children = listing.toList(); }
            Stream<Path> own = children.stream().filter(path -> path.getFileName().toString().equals("energy_uj"));
            Stream<Path> nested = children.stream().filter(Files::isDirectory).flatMap(this::energyFiles);
            return Stream.concat(own, nested);
        } catch (IOException | SecurityException ignored) { return Stream.empty(); }
    }

    private List<Path> tempInputs() {
        try (Stream<Path> devices = Files.list(HWMON)) {
            return devices.filter(Files::isDirectory).flatMap(device -> {
                try (Stream<Path> files = Files.list(device)) {
                    return files.filter(path -> path.getFileName().toString().matches("temp\\d+_input")).toList().stream();
                } catch (IOException | SecurityException ignored) { return Stream.empty(); }
            }).sorted().toList();
        } catch (IOException | SecurityException e) {
            return List.of();
        }
    }

    private String sensorLabel(Path input) {
        String filename = input.getFileName().toString();
        String index = filename.substring(4, filename.indexOf('_'));
        Path label = input.resolveSibling("temp" + index + "_label");
        try {
            if (Files.isReadable(label)) return Files.readString(label).trim().toLowerCase(Locale.ROOT);
        } catch (IOException ignored) { }
        Path chipName = input.getParent().resolve("name");
        try {
            return Files.readString(chipName).trim().toLowerCase(Locale.ROOT);
        } catch (IOException ignored) { return ""; }
    }

    private Double readTemperature(Path path) {
        if (path == null || !Files.isReadable(path)) return null;
        try {
            double raw = Double.parseDouble(Files.readString(path).trim());
            double celsius = raw > 1000 ? raw / 1000.0 : raw;
            return celsius >= -20 && celsius <= 150 ? celsius : null;
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }
}
