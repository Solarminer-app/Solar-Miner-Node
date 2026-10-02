package de.verdox.solarminer.pcagent.mining;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.solarminer.pcagent.dto.MinerStats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Periodic opt-in-only benchmark reporting; separate from user-triggered benchmark sessions.
 */
@Service
public class BenchmarkSharingService {
    private final AgentControlSettingsService settings;
    private final MiningService mining;
    private final RestClient client;
    private final ObjectMapper json;
    private final Path identityFile;
    private final Path consentFile;
    private volatile State state;

    public BenchmarkSharingService(AgentControlSettingsService settings, MiningService mining, ObjectMapper json,
                                   RestClient.Builder client, @Value("${solarminer.agent.benchmark.url:https://portal.solarminer.app}") String url,
                                   @Value("${solarminer.agent.benchmark.state-file:./solarminer-agent/benchmark-sharing.json}") String statePath) {
        this.settings = settings;
        this.mining = mining;
        this.json = json;
        this.client = client.baseUrl(url.replaceAll("/+$", "")).build();
        Path stateFile = Path.of(statePath).toAbsolutePath().normalize();
        this.identityFile = stateFile.resolveSibling("benchmark-participant.id");
        this.consentFile = stateFile;
        this.state = loadState();
    }

    @Scheduled(fixedDelayString = "${solarminer.agent.benchmark.interval-ms:900000}", initialDelayString = "${solarminer.agent.benchmark.initial-delay-ms:60000}")
    public synchronized void report() {
        boolean optedIn = settings.get().benchmarkSharingEnabled();
        if (!optedIn && !state.previouslyShared()) return;
        try {
            List<Sample> samples = optedIn ? sampleWorkers(mining.getWorkerStats()) : List.of();
            send(optedIn, samples);
        } catch (RuntimeException e) {
            // Delivery is best-effort and retried at the next interval.
        }
    }

    public synchronized void reportManualResults(List<MinerStats.Worker> workers) {
        if (!settings.get().benchmarkSharingEnabled()) return;
        try { send(true, sampleWorkers(workers)); }
        catch (RuntimeException ignored) { /* periodic reporting retries */ }
    }

    private List<Sample> sampleWorkers(List<MinerStats.Worker> workers) {
        return workers.stream()
                    .filter(w -> w.miningStatus() == MinerStats.MinerStatus.MINING)
                    .filter(w -> positive(w.terahashPerSecond()) && present(w.hardwareType()) && present(w.hardwareModel()) && present(w.currentAlgorithm()))
                    .collect(java.util.stream.Collectors.toMap(this::workerKey, this::sample, (a, b) -> b,
                            java.util.LinkedHashMap::new)).values().stream().toList();
    }

    private String workerKey(MinerStats.Worker worker) {
        return Objects.toString(worker.deviceId(), worker.hardwareModel()) + ":" + worker.currentAlgorithm();
    }

    private void send(boolean optedIn, List<Sample> samples) {
            client.post().uri("/api/telemetry/standalone-benchmarks")
                    .body(new Batch(participantId(), optedIn, samples)).retrieve().toBodilessEntity();
            writeState(new State(optedIn));
    }

    public Map<String, Object> comparison(String hardwareType, String hardwareModel, String algorithm) {
        try {
            return client.get().uri(uri -> uri.path("/api/public/benchmarks/match")
                            .queryParam("hardwareType", hardwareType).queryParam("hardwareModel", hardwareModel)
                            .queryParam("algorithm", algorithm).build())
                    .retrieve().body(new ParameterizedTypeReference<>() {
                    });
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Sample sample(MinerStats.Worker worker) {
        String identity = worker.deviceId() == null || worker.deviceId().isBlank() ? worker.workerDisplayName() : worker.deviceId();
        String deviceKey = UUID.nameUUIDFromBytes((participantId() + ":" + identity).getBytes(StandardCharsets.UTF_8)).toString();
        return new Sample(deviceKey, worker.hardwareType(), worker.hardwareModel(), worker.currentAlgorithm(),
                System.getProperty("os.name", "unknown"), System.getProperty("os.arch", "unknown"),
                worker.terahashPerSecond() * 1_000_000_000_000d, positive(worker.approximatedPowerUsageWatts()) ? (double) worker.approximatedPowerUsageWatts() : null,
                positive(worker.powerTargetWatts()) ? (double) worker.powerTargetWatts() : null);
    }

    private String participantId() {
        try {
            if (Files.isRegularFile(identityFile)) return Files.readString(identityFile).trim();
            Files.createDirectories(identityFile.getParent());
            String id = UUID.randomUUID().toString();
            Files.writeString(identityFile, id);
            return id;
        } catch (IOException e) {
            throw new IllegalStateException("Could not persist anonymous benchmark identity", e);
        }
    }

    private State loadState() {
        try {
            return json.readValue(Files.readString(consentFile), State.class);
        } catch (Exception ignored) {
            return new State(false);
        }
    }

    private void writeState(State value) {
        try {
            Files.createDirectories(consentFile.getParent());
            Path temp = Files.createTempFile(consentFile.getParent(), "benchmark-", ".json");
            try {
                json.writeValue(temp.toFile(), value);
                Files.move(temp, consentFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, consentFile, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
            state = value;
        } catch (IOException e) {
            throw new IllegalStateException("Could not persist benchmark consent state", e);
        }
    }

    private static boolean positive(double value) {
        return Double.isFinite(value) && value > 0;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank() && !"-".equals(value);
    }

    public record Batch(String uuid, boolean telemetryOptIn, List<Sample> benchmarks) {
    }

    public record Sample(String deviceKey, String hardwareType, String hardwareModel, String algorithm,
                         String minerOs, String processorArchitecture, double hashrateHs,
                         Double powerWatts, Double powerTargetWatts) {
    }

    public record State(boolean previouslyShared) {
    }
}
