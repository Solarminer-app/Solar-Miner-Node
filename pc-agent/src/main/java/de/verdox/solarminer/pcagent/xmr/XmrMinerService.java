package de.verdox.solarminer.pcagent.xmr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.solarminer.pcagent.dto.MinerStats;
import de.verdox.solarminer.pcagent.lowlevel.sensor.HardwareSensorReader;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class XmrMinerService {
    private static final Logger LOGGER = Logger.getLogger(XmrMinerService.class.getName());
    private static final Path XMRIG_DIR = Paths.get("./solarminer-agent/xmrig/").toAbsolutePath().normalize();

    private final XmrConfigService configService;
    private final ObjectMapper objectMapper;
    private final HardwareSensorReader sensorReader;
    private final String processorName;
    private Process minerProcess;

    private final int totalLogicalProcessors;
    private int currentMaxThreads;
    @Getter
    private final long estimatedMaxCpuWattage;

    private volatile MinerStats.MinerStatus minerStatus = MinerStats.MinerStatus.PAUSED;

    @Getter
    private long desiredPowerUsage = 0;

    private final ExecutorService streamReaderExecutor = Executors.newCachedThreadPool();
    private ScheduledExecutorService apiPollerExecutor;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private volatile long currentHashesPerSecond = 0;
    private int apiErrorCount = 0;

    public XmrMinerService(XmrConfigService configService, ObjectMapper objectMapper, HardwareSensorReader sensorReader) {
        this.configService = configService;
        this.objectMapper = objectMapper;
        this.sensorReader = sensorReader;

        SystemInfo systemInfo = new SystemInfo();
        CentralProcessor processor = systemInfo.getHardware().getProcessor();
        this.processorName = processor.getProcessorIdentifier().getName();
        this.totalLogicalProcessors = processor.getLogicalProcessorCount();
        this.currentMaxThreads = this.totalLogicalProcessors;
        this.estimatedMaxCpuWattage = estimateCpuTdp(processor.getProcessorIdentifier().getName(), totalLogicalProcessors);
    }

    @PreDestroy
    public void onShutdown() {
        LOGGER.info("Stopping XMR Miner service...");
        hardStopMining();
    }

    public MinerStats.Worker getWorkerStats() {
        if (minerStatus == MinerStats.MinerStatus.MINING && !isMiningProcessAlive()) {
            minerStatus = MinerStats.MinerStatus.ERROR;
            currentHashesPerSecond = 0;
        }

        return new MinerStats.Worker(
                minerStatus,
                processorName,
                "RandomX",
                currentHashesPerSecond / Math.pow(10, 12),
                readCPUTemperature(),
                desiredPowerUsage,
                desiredPowerUsage,
                estimatedMaxCpuWattage,
                estimatedMaxCpuWattage,
                getWattage(),
                List.of(configService.readUserPoolFromConfig()));
    }

    public synchronized void startMining() {
        if (isMiningProcessAlive()) {
            LOGGER.info("XMRig is already running.");
            return;
        }

        String os = System.getProperty("os.name").toLowerCase();
        String executableName = os.contains("win") ? "xmrig.exe" : "xmrig";
        File executableFile = XMRIG_DIR.resolve(executableName).toFile();

        if (!executableFile.exists()) {
            LOGGER.severe("Cannot start mining: Executable not found at " + executableFile.getAbsolutePath());
            minerStatus = MinerStats.MinerStatus.ERROR;
            return;
        }

        if (!os.contains("win")) {
            executableFile.setExecutable(true);
        }

        try {
            LOGGER.info("Starting XMRig process with max " + currentMaxThreads + " threads...");
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(executableFile.getAbsolutePath(), "--config=config.json");
            processBuilder.directory(XMRIG_DIR.toFile());
            processBuilder.redirectErrorStream(true);

            minerProcess = processBuilder.start();
            minerStatus = MinerStats.MinerStatus.MINING;
            apiErrorCount = 0;

            minerProcess.onExit().thenAccept(process -> {
                if (minerStatus == MinerStats.MinerStatus.MINING) {
                    LOGGER.severe("XMRig process crashed or exited unexpectedly with code: " + process.exitValue());
                    minerStatus = MinerStats.MinerStatus.ERROR;
                }
                currentHashesPerSecond = 0;
            });

            streamReaderExecutor.submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(minerProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        LOGGER.info("[XMRig] " + line);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error reading XMRig output stream", e);
                }
            });

            if (apiPollerExecutor != null && !apiPollerExecutor.isShutdown()) {
                apiPollerExecutor.shutdownNow();
            }
            apiPollerExecutor = Executors.newSingleThreadScheduledExecutor();
            apiPollerExecutor.scheduleAtFixedRate(this::fetchHashrateFromApi, 5, 5, TimeUnit.SECONDS);

            LOGGER.info("XMRig started successfully. PID: " + minerProcess.pid());

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start XMRig process: " + e.getMessage(), e);
            minerStatus = MinerStats.MinerStatus.ERROR;
            currentHashesPerSecond = 0;
        }
    }

    public synchronized void hardStopMining() {
        if (apiPollerExecutor != null && !apiPollerExecutor.isShutdown()) {
            apiPollerExecutor.shutdownNow();
        }

        if (isMiningProcessAlive()) {
            LOGGER.info("Sending kill signal to XMRig process...");
            minerProcess.destroyForcibly();
            try {
                minerProcess.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            LOGGER.info("XMRig process terminated.");
        }

        minerStatus = MinerStats.MinerStatus.STOPPED;
        currentHashesPerSecond = 0;
        minerProcess = null;
    }

    private void fetchHashrateFromApi() {
        if (!isMiningProcessAlive()) {
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:1999/2/summary"))
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer token")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode hashrateNode = root.path("hashrate").path("total");

                if (hashrateNode.isArray() && !hashrateNode.isEmpty() && !hashrateNode.get(0).isNull()) {
                    this.currentHashesPerSecond = (long) hashrateNode.get(0).asDouble();
                    this.apiErrorCount = 0; // Reset error count on success
                }
            } else {
                handleApiError();
            }
        } catch (Exception e) {
            handleApiError();
        }
    }

    private void handleApiError() {
        apiErrorCount++;
        if (apiErrorCount > 6) {
            this.currentHashesPerSecond = 0;
            LOGGER.log(Level.FINE, "API is unresponsive. Hashrate zeroed out.");
        }
    }

    public synchronized void setDesiredPowerUsage(long watts) {
        LOGGER.info("Setting desired power target to: " + watts + "W (Estimated max: " + estimatedMaxCpuWattage + "W)");
        this.desiredPowerUsage = watts;

        if (watts <= 0) {
            hardStopMining();
            return;
        }

        if (watts >= estimatedMaxCpuWattage) {
            this.currentMaxThreads = this.totalLogicalProcessors;
        } else {
            double percentage = (double) watts / estimatedMaxCpuWattage;
            this.currentMaxThreads = Math.max(1, (int) Math.floor(this.totalLogicalProcessors * percentage));
        }

        LOGGER.info("Calculated thread limit based on power target: " + currentMaxThreads + " of " + totalLogicalProcessors + " threads.");

        if (minerProcess != null && minerProcess.isAlive()) {
            hardStopMining();
            startMining();
        }
    }

    public double readCPUTemperature() {
        return sensorReader.getCpuTemperatureCelsius();
    }

    public long getWattage() {
        if(isMiningProcessAlive()) {
            return Math.round(sensorReader.getCpuPowerWatts());
        }
        return 0;
    }

    private void applyThreadLimitToConfig() {
        Path configPath = XMRIG_DIR.resolve("config.json");
        configService.setMaxThreads(configPath, currentMaxThreads);
    }

    private long estimateCpuTdp(String cpuName, int coreCount) {
        cpuName = cpuName.toLowerCase();

        if (cpuName.contains(" i9") || cpuName.contains(" ryzen 9")) {
            return cpuName.endsWith("k") || cpuName.endsWith("x") ? 150 : 100;
        } else if (cpuName.contains(" i7") || cpuName.contains(" ryzen 7")) {
            return cpuName.endsWith("k") || cpuName.endsWith("x") ? 125 : 65;
        } else if (cpuName.contains(" i5") || cpuName.contains(" ryzen 5")) {
            return 65;
        } else if (cpuName.endsWith("u") || cpuName.endsWith("p")) {
            return 28;
        }
        return Math.min(200L, coreCount * 15L);
    }

    public boolean isMiningProcessAlive() {
        return minerProcess != null && minerProcess.isAlive();
    }
}