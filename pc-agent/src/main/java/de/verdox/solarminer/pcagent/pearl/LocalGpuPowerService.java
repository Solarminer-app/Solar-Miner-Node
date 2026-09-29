package de.verdox.solarminer.pcagent.pearl;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local GPU power caps for Pearl rigs. The agent process needs device permissions. */
@Service
public class LocalGpuPowerService {
    private static final Pattern AMD_INDEX = Pattern.compile("(?m)^\\s*GPU:\s*(\\d+)\\b");
    private static final Pattern AMD_MIN = Pattern.compile("MIN_POWER_LIMIT:\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern AMD_MAX = Pattern.compile("MAX_POWER_LIMIT:\s*(\\d+(?:\\.\\d+)?)");

    private static final Pattern AMD_MODEL = Pattern.compile("(?m)^\\s*MARKET_NAME:\\s*(.+?)\\s*$");
    private volatile List<Gpu> cachedGpus = List.of();
    private volatile long lastDiscoveryNanos;
    private volatile Map<String, Integer> appliedTargets = Map.of();

    public record Gpu(String vendor, int index, String model, int minWatts, int maxWatts, Double currentWatts) { }

    public List<Gpu> discover() {
        long now = System.nanoTime();
        if (now - lastDiscoveryNanos < java.util.concurrent.TimeUnit.SECONDS.toNanos(2)) return cachedGpus;
        synchronized (this) {
            now = System.nanoTime();
            if (now - lastDiscoveryNanos < java.util.concurrent.TimeUnit.SECONDS.toNanos(2)) return cachedGpus;
            cachedGpus = detectGpus();
            lastDiscoveryNanos = System.nanoTime();
            return cachedGpus;
        }
    }

    private List<Gpu> detectGpus() {
        List<Gpu> result = new ArrayList<>();
        try {
            String csv = run(List.of("nvidia-smi", "--query-gpu=index,name,power.min_limit,power.max_limit,power.draw", "--format=csv,noheader,nounits"));
            for (String line : csv.lines().toList()) {
                String[] cells = line.split(",");
                if (cells.length != 5) continue;
                try {
                    Double currentWatts = parseOptionalWatts(cells[4]);
                    result.add(gpu("NVIDIA", Integer.parseInt(cells[0].trim()), cells[1].trim(), cells[2], cells[3], currentWatts));
                } catch (NumberFormatException ignored) { }
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        try {
            Matcher ids = AMD_INDEX.matcher(run(List.of("amd-smi", "list")));
            while (ids.find()) {
                int index = Integer.parseInt(ids.group(1));
                String limits = run(List.of("amd-smi", "static", "-g", Integer.toString(index), "-a", "-l"));
                Matcher min = AMD_MIN.matcher(limits);
                Matcher max = AMD_MAX.matcher(limits);
                Matcher model = AMD_MODEL.matcher(limits);
                if (min.find() && max.find()) result.add(gpu("AMD", index,
                        model.find() ? model.group(1).trim() : "AMD (Modell unbekannt)", min.group(1), max.group(1), null));
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        return result;
    }

    private static Gpu gpu(String vendor, int index, String model, String min, String max, Double currentWatts) {
        int minimum = (int) Math.ceil(Double.parseDouble(min.trim()));
        int maximum = (int) Math.floor(Double.parseDouble(max.trim()));
        if (index < 0 || minimum < 0 || maximum <= minimum) throw new NumberFormatException("Invalid GPU limits");
        return new Gpu(vendor, index, model.isBlank() ? vendor + " (Modell unbekannt)" : model,
                minimum, maximum, currentWatts);
    }

    private static Double parseOptionalWatts(String value) {
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) && parsed >= 0 ? parsed : null;
        } catch (NumberFormatException ignored) { return null; }
    }

    public boolean setTotalPowerTarget(long watts) {
        return setTotalPowerTarget(watts, discover());
    }

    public int appliedTarget(Gpu gpu) {
        return appliedTargets.getOrDefault(gpu.vendor() + ":" + gpu.index(), 0);
    }

    public boolean setTotalPowerTarget(long watts, List<Gpu> cards) {
        if (cards.isEmpty()) return false;
        long minimum = cards.stream().mapToLong(Gpu::minWatts).sum();
        long maximum = cards.stream().mapToLong(Gpu::maxWatts).sum();
        if (watts < minimum || watts > maximum) return false;
        long remaining = watts - minimum;
        long headroom = maximum - minimum;
        List<Integer> planned = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
                Gpu card = cards.get(i);
                int assigned = i == cards.size() - 1
                        ? (int) (card.minWatts() + remaining)
                        : (int) (card.minWatts() + (watts - minimum) * (card.maxWatts() - card.minWatts()) / headroom);
                remaining -= assigned - card.minWatts();
                if (assigned < card.minWatts() || assigned > card.maxWatts()) return false;
                planned.add(assigned);
        }
        try {
            for (int i = 0; i < cards.size(); i++) {
                Gpu card = cards.get(i);
                int assigned = planned.get(i);
                if (card.vendor().equals("NVIDIA")) {
                    run(List.of("nvidia-smi", "-i", Integer.toString(card.index()), "-pl", Integer.toString(assigned)));
                } else {
                    run(List.of("amd-smi", "set", "-g", Integer.toString(card.index()), "-o", Integer.toString(assigned)));
                }
            }
            Map<String, Integer> updated = new HashMap<>(appliedTargets);
            for (int i = 0; i < cards.size(); i++)
                updated.put(cards.get(i).vendor() + ":" + cards.get(i).index(), planned.get(i));
            appliedTargets = Map.copyOf(updated);
            return true;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    private String run(List<String> command) throws IOException, InterruptedException {
        Path outputFile = Files.createTempFile("solarminer-gpu-", ".log");
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(outputFile.toFile()).start();
            if (!process.waitFor(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("GPU command timed out");
            }
            if (Files.size(outputFile) > 32768) throw new IOException("GPU tool output exceeded 32 KiB");
            String output = Files.readString(outputFile, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) throw new IOException("GPU tool failed: " + output.strip());
            return output;
        } finally {
            Files.deleteIfExists(outputFile);
        }
    }
}
