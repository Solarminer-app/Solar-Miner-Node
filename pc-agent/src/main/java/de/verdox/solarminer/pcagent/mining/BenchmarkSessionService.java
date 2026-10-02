package de.verdox.solarminer.pcagent.mining;

import de.verdox.solarminer.pcagent.dto.MinerStats;
import de.verdox.solarminer.pcagent.pearl.PearlMinerService;
import de.verdox.solarminer.pcagent.xmr.XmrMinerService;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** Timed local benchmark; full runs restore the miners that were active beforehand. */
@Service
public class BenchmarkSessionService {
    private final MiningService mining;
    private final XmrMinerService xmr;
    private final PearlMinerService pearl;
    private final BenchmarkSharingService sharing;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> Thread.ofPlatform().name("pc-agent-benchmark").daemon(true).unstarted(r));
    private volatile Session session = Session.idle();
    private volatile boolean cancel;

    public BenchmarkSessionService(MiningService mining, XmrMinerService xmr, PearlMinerService pearl, BenchmarkSharingService sharing) {
        this.mining = mining; this.xmr = xmr; this.pearl = pearl; this.sharing = sharing;
    }

    public synchronized Session start(String mode, int seconds) {
        if (!"LIVE".equals(mode) && !"INSTALLED".equals(mode)) throw new IllegalArgumentException("Unknown benchmark mode");
        if (seconds < 30 || seconds > 300) throw new IllegalArgumentException("Duration must be 30–300 seconds");
        if (session.running()) throw new IllegalStateException("A benchmark is already running");
        List<String> phases = "INSTALLED".equals(mode) ? installedPhases() : List.of("live");
        if (phases.isEmpty()) throw new IllegalStateException("Install and configure at least one miner before running this benchmark");
        cancel = false;
        Instant start = Instant.now();
        int phaseCount = phases.size();
        session = new Session(true, mode, "Preparing", start, start.plusSeconds(seconds), start.plusSeconds((long) seconds * phaseCount), 0, phaseCount, List.of(), (long) seconds * phaseCount);
        executor.submit(() -> run(mode, seconds, phases));
        return session;
    }

    public Session status() {
        Session value = session;
        if (!value.running()) return value;
        long remaining = Math.max(0, java.time.Duration.between(Instant.now(), value.totalEndsAt()).toSeconds());
        return new Session(true, value.mode(), value.phase(), value.startedAt(), value.phaseEndsAt(), value.totalEndsAt(),
                value.phaseIndex(), value.phaseCount(), value.results(), remaining);
    }
    public synchronized Session cancel() { cancel = true; return status(); }

    /** Serializes Node controls with benchmark admission so a checked command cannot race a new run. */
    public synchronized <T> T withExternalControl(Supplier<T> command) {
        if (session.running()) throw new BenchmarkRunningException();
        return command.get();
    }

    private List<String> installedPhases() {
        List<String> phases = new ArrayList<>();
        if (xmr.readyForStart()) phases.add("monero");
        if (pearl.binaryAvailable() && pearl.configuration() != null) phases.add("pearl");
        return List.copyOf(phases);
    }

    private void run(String mode, int seconds, List<String> phases) {
        if ("INSTALLED".equals(mode) && (xmr.hasExternalMinerProcess() || pearl.hasExternalMinerProcess())) {
            Session old = session;
            session = new Session(false, mode, "Sequential benchmark skipped: externally started miners cannot be safely paused and restored",
                    old.startedAt(), Instant.now(), old.totalEndsAt(), 0, old.phaseCount(), List.of(), 0L);
            return;
        }
        List<MinerStats.Worker> before = mining.getWorkerStats();
        boolean cpuPausedBefore = mining.cpuManuallyPaused();
        boolean xmrWasMining = before.stream().anyMatch(w -> isMining(w) && "CPU".equalsIgnoreCase(w.hardwareType()));
        Set<String> pearlWasMining = before.stream().filter(w -> isMining(w) && "GPU".equalsIgnoreCase(w.hardwareType()))
                .map(MinerStats.Worker::deviceId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Set<String> pearlPausedBefore = pearl.manuallyPausedGpuKeys();
        Map<String, List<MinerStats.Worker>> observations = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>();
        int index = 0;
        String error = null;
        try {
            if ("INSTALLED".equals(mode)) {
                update(mode, "Pausing current miners", Instant.now(), session.totalEndsAt(), 0, phases.size(), observations);
                if (xmrWasMining) mining.pauseMining("monero");
                if (!pearlWasMining.isEmpty()) mining.pauseMining("pearl");
            }
            for (String phase : phases) {
                if (cancel) break;
                index++;
                if ("INSTALLED".equals(mode)) {
                    if (!mining.resumeMining(phase)) { skipped.add(phase); continue; }
                }
                Instant end = Instant.now().plusSeconds(seconds);
                Instant totalEnd = end.plusSeconds((long) seconds * (phases.size() - index));
                update(mode, "live".equals(phase) ? "Measuring active miners" : "Benchmarking " + phase, end, totalEnd, index, phases.size(), observations);
                List<MinerStats.Worker> captured = new ArrayList<>();
                while (!cancel && Instant.now().isBefore(end)) {
                    mining.getWorkerStats().stream().filter(BenchmarkSessionService::isMining)
                            .filter(w -> "live".equals(phase) || ("monero".equals(phase)
                                    ? "CPU".equalsIgnoreCase(w.hardwareType()) : "GPU".equalsIgnoreCase(w.hardwareType())))
                            .filter(w -> Double.isFinite(w.terahashPerSecond()) && w.terahashPerSecond() > 0).forEach(captured::add);
                    Thread.sleep(2000);
                }
                if ("INSTALLED".equals(mode)) mining.pauseMining(phase);
                for (MinerStats.Worker worker : captured) observations.computeIfAbsent(key(worker), ignored -> new ArrayList<>()).add(worker);
                update(mode, "Summarizing", Instant.now(), totalEnd, index, phases.size(), observations);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); error = "Benchmark interrupted";
        } catch (RuntimeException e) {
            error = Objects.toString(e.getMessage(), "Benchmark failed");
        } finally {
            try {
                if ("INSTALLED".equals(mode)) {
                    if (xmrWasMining) mining.resumeMining("monero");
                    else mining.restoreCpuPauseState(cpuPausedBefore);
                    pearl.restoreWorkerState(pearlPausedBefore, pearlWasMining);
                }
            } catch (RuntimeException restoreFailure) {
                error = "Could not fully restore miner state: " + Objects.toString(restoreFailure.getMessage(), "unknown error");
            }
            List<MinerStats.Worker> captured = aggregateWorkers(observations);
            if (!captured.isEmpty()) sharing.reportManualResults(captured);
            Session old = session;
            String result = error != null ? "Error: " + error : cancel ? "Cancelled; previous miner state restored"
                    : "Complete; previous miner state restored" + (skipped.isEmpty() ? "" : "; skipped: " + String.join(", ", skipped));
            session = new Session(false, mode, result, old.startedAt(), Instant.now(), old.totalEndsAt(), phases.size(), phases.size(), summarize(observations), 0L);
        }
    }

    private void update(String mode, String phase, Instant end, Instant totalEnd, int index, int count, Map<String, List<MinerStats.Worker>> observations) {
        Session old = session;
        session = new Session(true, mode, phase, old.startedAt(), end, totalEnd, index, count, summarize(observations), null);
    }
    private static boolean isMining(MinerStats.Worker w) { return w.miningStatus() == MinerStats.MinerStatus.MINING; }
    private static String key(MinerStats.Worker w) { return String.join("|", Objects.toString(w.deviceId(), ""), Objects.toString(w.currentAlgorithm(), "")); }
    private static List<Result> summarize(Map<String, List<MinerStats.Worker>> observations) {
        return observations.values().stream().map(values -> {
            MinerStats.Worker w = values.getFirst();
            double[] rates = values.stream().mapToDouble(v -> v.terahashPerSecond() * 1_000_000_000_000d).sorted().toArray();
            double median = rates.length % 2 == 1 ? rates[rates.length / 2] : (rates[rates.length / 2 - 1] + rates[rates.length / 2]) / 2;
            double watts = values.stream().mapToLong(MinerStats.Worker::approximatedPowerUsageWatts).filter(v -> v > 0).average().orElse(0);
            return new Result(w.hardwareType(), w.hardwareModel(), w.currentAlgorithm(), median, watts, watts > 0 ? median / watts : 0, values.size());
        }).toList();
    }
    private static List<MinerStats.Worker> aggregateWorkers(Map<String, List<MinerStats.Worker>> observations) {
        return observations.values().stream().map(values -> {
            MinerStats.Worker w = values.getFirst();
            double[] rates = values.stream().mapToDouble(MinerStats.Worker::terahashPerSecond).sorted().toArray();
            double medianRate = rates.length % 2 == 1 ? rates[rates.length / 2] : (rates[rates.length / 2 - 1] + rates[rates.length / 2]) / 2;
            long watts = Math.round(values.stream().mapToLong(MinerStats.Worker::approximatedPowerUsageWatts).filter(v -> v > 0).average().orElse(0));
            return new MinerStats.Worker(w.miningStatus(), w.workerDisplayName(), w.currentAlgorithm(), medianRate,
                    w.temperatureCelsius(), w.powerTargetWatts(), w.minPowerTarget(), w.defaultPowerTarget(),
                    w.maxPowerTarget(), watts, w.pools(), w.hardwareType(), w.hardwareModel(), w.deviceId());
        }).toList();
    }
    @PreDestroy public void close() { cancel = true; executor.shutdownNow(); }

    public record Result(String hardwareType, String hardwareModel, String algorithm, double hashrateHs, double powerWatts, double hashesPerWatt, int observations) { }
    public record Session(boolean running, String mode, String phase, Instant startedAt, Instant phaseEndsAt, Instant totalEndsAt,
                          int phaseIndex, int phaseCount, List<Result> results, Long secondsRemaining) {
        static Session idle() { return new Session(false, "", "Idle", null, null, null, 0, 0, List.of(), 0L); }
    }

    public static final class BenchmarkRunningException extends RuntimeException {
        public BenchmarkRunningException() { super("Node controls are temporarily locked while a benchmark is running"); }
    }
}
