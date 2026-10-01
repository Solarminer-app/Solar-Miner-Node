package de.verdox.solarminer.pcagent.mining;

import de.verdox.solarminer.pcagent.dto.MinerStats;
import de.verdox.solarminer.pcagent.dto.Pools;
import de.verdox.solarminer.pcagent.lowlevel.HardwareIdentityService;
import de.verdox.solarminer.pcagent.pearl.PearlMinerService;
import de.verdox.solarminer.pcagent.pearl.LocalGpuPowerService;
import de.verdox.solarminer.pcagent.xmr.XmrMinerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Service
public class MiningService {
    private final String minerUID;
    private final String macAddress;
    private final String minerModel;
    private final HardwareIdentityService hardwareIdentityService;

    //TODO: Save desired global power target to disk or smth
    private long desiredCpuPowerTarget;
    private long desiredGpuPowerTarget;
    private long desiredGlobalPowerTarget;
    private volatile boolean cpuManuallyPaused;
    private final XmrMinerService xmrMinerService;
    private final PearlMinerService pearlMinerService;
    private final LocalGpuPowerService gpuPowerService;
    private final AgentControlSettingsService controls;
    private final Path coinSelectionFile;
    private volatile String activeCoin = "monero";
    private final MinerStats.MinerIdentity minerIdentity;
    private final MinerStats.MinerStatus status = MinerStats.MinerStatus.PAUSED;

    public MiningService(XmrMinerService xmrMinerService, PearlMinerService pearlMinerService,
                         LocalGpuPowerService gpuPowerService, HardwareIdentityService hardwareIdentityService,
                         AgentControlSettingsService controls,
                         @Value("${solarminer.agent.coin-selection-file:./solarminer-agent/active-coin.txt}") String coinSelectionPath) {
        this.xmrMinerService = xmrMinerService;
        this.pearlMinerService = pearlMinerService;
        this.gpuPowerService = gpuPowerService;
        this.controls = controls;
        this.coinSelectionFile = Path.of(coinSelectionPath).toAbsolutePath().normalize();
        this.activeCoin = readSelectedCoin();
        minerUID = "";
        macAddress = "";
        minerModel = "";
        this.minerIdentity = new MinerStats.MinerIdentity(minerUID, macAddress, minerModel);
        this.hardwareIdentityService = hardwareIdentityService;
    }

    /** Allocates the legacy PV-wide budget across CPU and selected GPUs. */
    public synchronized boolean setTarget(long powerTarget) {
        return setTarget(powerTarget, false);
    }

    public synchronized boolean setExternalTarget(long powerTarget) {
        if (powerTarget <= 0) return pauseExternally();
        if (!controls.get().dynamicPowerScalingEnabled()) return resumeExternally();
        return setTarget(powerTarget, true);
    }

    private boolean setTarget(long powerTarget, boolean external) {
        if (powerTarget <= 0) {
            if (external) return pauseExternally();
            desiredGlobalPowerTarget = 0;
            desiredCpuPowerTarget = 0;
            desiredGpuPowerTarget = 0;
            return pauseAll();
        }
        long cpuMax = (!external || controls.workerEnabled("cpu")) && !cpuManuallyPaused && xmrMinerService.readyForStart() ? xmrMinerService.getEstimatedMaxCpuWattage() : 0;
        List<LocalGpuPowerService.Gpu> allowedGpus = pearlMinerService.eligibleGpus().stream()
                .filter(gpu -> !external || controls.workerEnabled(gpu.deviceId()))
                .toList();
        List<LocalGpuPowerService.Gpu> cards = allowedGpus.stream()
                .filter(LocalGpuPowerService.Gpu::supportsDynamicPowerScaling).toList();
        long gpuMinimum = cards.stream().mapToLong(LocalGpuPowerService.Gpu::minWatts).sum();
        long gpuMaximum = cards.stream().mapToLong(LocalGpuPowerService.Gpu::maxWatts).sum();
        long minimum = cpuMax > 0 ? xmrMinerService.getMinimumControllablePowerWatts() : gpuMinimum;
        long maximum = cpuMax + gpuMaximum;
        if (powerTarget < minimum) {
            if (external) { pauseExternally(); return false; }
            return setTarget(0);
        }
        powerTarget = Math.min(powerTarget, maximum);
        long cpuTarget = Math.min(cpuMax, powerTarget);
        long gpuTarget = 0;
        if (!cards.isEmpty() && powerTarget >= gpuMinimum) {
            cpuTarget = Math.min(cpuMax, powerTarget - gpuMinimum);
            gpuTarget = Math.min(gpuMaximum, powerTarget - cpuTarget);
        }
        if (cpuTarget == 0 && gpuTarget == 0) {
            if (external) { pauseExternally(); return false; }
            return setTarget(0);
        }
        if (gpuTarget > 0 && !gpuPowerService.setTotalPowerTarget(gpuTarget, cards)) {
            if (!(external ? stopGpus(allowedGpus) : pearlMinerService.stop())) return failGlobalBudget(external);
            gpuTarget = 0;
            cpuTarget = Math.min(cpuMax, powerTarget);
        }
        if (gpuTarget == 0 && !(external ? stopGpus(allowedGpus) : pearlMinerService.stop())) return failGlobalBudget(external);
        boolean cpuStartSucceeded = cpuTarget <= 0;
        if (!external || controls.workerEnabled("cpu")) {
            xmrMinerService.setDesiredPowerUsage(cpuTarget);
            if (cpuTarget > 0) {
                xmrMinerService.startMining();
                cpuStartSucceeded = xmrMinerService.isMiningProcessAlive();
            }
        }
        boolean gpuStartsSucceeded = true;
        if (gpuTarget > 0) {
            if (external) {
                for (LocalGpuPowerService.Gpu gpu : cards)
                    gpuStartsSucceeded = pearlMinerService.startGpu(gpu.vendor(), gpu.index()) && gpuStartsSucceeded;
            } else gpuStartsSucceeded = pearlMinerService.startForBudget();
        }
        desiredGlobalPowerTarget = powerTarget;
        desiredCpuPowerTarget = cpuTarget;
        desiredGpuPowerTarget = gpuTarget;
        return external
                ? (cpuTarget > 0 || gpuTarget > 0) && cpuStartSucceeded && (gpuTarget <= 0 || gpuStartsSucceeded)
                : xmrMinerService.isMiningProcessAlive() || pearlMinerService.running();
    }

    public synchronized boolean resumeExternally() {
        boolean attempted = false;
        boolean allStarted = true;
        if (controls.workerEnabled("cpu") && !cpuManuallyPaused && xmrMinerService.readyForStart()) {
            attempted = true;
            xmrMinerService.startMining();
            allStarted = xmrMinerService.isMiningProcessAlive() && allStarted;
        }
        for (LocalGpuPowerService.Gpu gpu : pearlMinerService.eligibleGpus()) {
            if (controls.workerEnabled(gpu.deviceId())) {
                attempted = true;
                allStarted = pearlMinerService.startGpu(gpu.vendor(), gpu.index()) && allStarted;
            }
        }
        return attempted && allStarted;
    }

    public synchronized boolean pauseExternally() {
        desiredGlobalPowerTarget = 0;
        desiredCpuPowerTarget = 0;
        desiredGpuPowerTarget = 0;
        boolean success = true;
        if (controls.workerEnabled("cpu")) {
            xmrMinerService.hardStopMining();
        }
        for (LocalGpuPowerService.Gpu gpu : pearlMinerService.selectedGpus()) {
            if (controls.workerEnabled(gpu.deviceId()))
                success = pearlMinerService.stopGpu(gpu.vendor(), gpu.index()) && success;
        }
        return success;
    }

    private boolean stopGpus(List<LocalGpuPowerService.Gpu> cards) {
        boolean success = true;
        for (LocalGpuPowerService.Gpu gpu : cards)
            success = pearlMinerService.stopGpu(gpu.vendor(), gpu.index()) && success;
        return success;
    }

    private boolean failGlobalBudget(boolean external) {
        desiredGlobalPowerTarget = 0;
        desiredCpuPowerTarget = 0;
        desiredGpuPowerTarget = 0;
        if (external) pauseExternally(); else pauseAll();
        return false;
    }

    public synchronized boolean setTarget(String coin, long powerTarget) {
        if ("pearl".equals(coin)) {
            if (powerTarget <= 0) {
                desiredGpuPowerTarget = 0;
                desiredGlobalPowerTarget = desiredCpuPowerTarget;
                return pearlMinerService.pauseSelectedManually();
            }
            if (!gpuPowerService.setTotalPowerTarget(powerTarget, pearlMinerService.selectedGpus())) return false;
            desiredGpuPowerTarget = powerTarget;
            desiredGlobalPowerTarget = desiredCpuPowerTarget + desiredGpuPowerTarget;
            return pearlMinerService.start();
        }
        if (!"monero".equals(coin)) return false;
        this.desiredCpuPowerTarget = Math.max(0, powerTarget);
        desiredGlobalPowerTarget = desiredCpuPowerTarget + desiredGpuPowerTarget;
        xmrMinerService.setDesiredPowerUsage(desiredCpuPowerTarget);
        cpuManuallyPaused = desiredCpuPowerTarget == 0;
        if (desiredCpuPowerTarget > 0) xmrMinerService.startMining();
        return desiredCpuPowerTarget == 0 || xmrMinerService.isMiningProcessAlive();
    }

    public boolean pauseMining() {
        return pauseMining(activeCoin);
    }

    public boolean pauseMining(String coin) {
        if ("pearl".equals(coin)) return pearlMinerService.pauseSelectedManually();
        if (!"monero".equals(coin)) return false;
        cpuManuallyPaused = true;
        xmrMinerService.hardStopMining();
        return true;
    }

    public boolean pauseAll() {
        boolean gpuStopped = pearlMinerService.stop();
        xmrMinerService.hardStopMining();
        return gpuStopped;
    }

    public boolean resumeMining() {
        return resumeMining(activeCoin);
    }

    public synchronized boolean resumeAll() {
        if (desiredGlobalPowerTarget > 0) return setTarget(desiredGlobalPowerTarget);
        return resumeMining(activeCoin);
    }

    public boolean resumeMining(String coin) {
        if ("pearl".equals(coin)) return pearlMinerService.start();
        if (!"monero".equals(coin)) return false;
        cpuManuallyPaused = false;
        xmrMinerService.startMining();
        return xmrMinerService.isMiningProcessAlive();
    }

    public synchronized void usePearl(PearlMinerService.Config config) throws IOException {
        pearlMinerService.configure(config);
        if (!switchCoin("pearl")) throw new IOException("Could not select Pearl after configuration");
    }

    public synchronized boolean useMonero() {
        return switchCoin("monero");
    }

    public String activeCoin() { return activeCoin; }

    public synchronized boolean switchCoin(String coin) {
        if (!"monero".equals(coin) && !"pearl".equals(coin)) return false;
        if (coin.equals(activeCoin)) return true;
        try {
            persistSelectedCoin(coin);
        } catch (IOException e) {
            return false;
        }
        activeCoin = coin;
        return true;
    }

    private String readSelectedCoin() {
        try {
            String selected = Files.readString(coinSelectionFile).strip();
            if ("monero".equals(selected) || "pearl".equals(selected)) return selected;
        } catch (IOException ignored) { }
        return pearlMinerService.configuration() == null ? "monero" : "pearl";
    }

    private void persistSelectedCoin(String coin) throws IOException {
        Files.createDirectories(coinSelectionFile.getParent());
        Path temp = Files.createTempFile(coinSelectionFile.getParent(), "coin-", ".tmp");
        try {
            Files.writeString(temp, coin);
            try {
                Files.move(temp, coinSelectionFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, coinSelectionFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public boolean increasePowerTarget(long powerTarget) {
        return setTarget(desiredGlobalPowerTarget + powerTarget);
    }

    public boolean decreasePowerTarget(long powerTarget) {
        return setTarget(desiredGlobalPowerTarget - powerTarget);
    }

    public long calculateMinPowerTargetFromComponents() {
        long cpu = controls.workerEnabled("cpu") && !cpuManuallyPaused && xmrMinerService.readyForStart()
                ? xmrMinerService.getMinimumControllablePowerWatts() : 0;
        long gpu = pearlMinerService.eligibleGpus().stream().filter(g -> controls.workerEnabled(g.deviceId())).filter(LocalGpuPowerService.Gpu::supportsDynamicPowerScaling)
                .mapToLong(LocalGpuPowerService.Gpu::minWatts).sum();
        return cpu > 0 ? cpu : gpu;
    }

    public long calculateMaxPowerTargetFromComponents() {
        long cpu = controls.workerEnabled("cpu") && !cpuManuallyPaused && xmrMinerService.readyForStart() ? xmrMinerService.getEstimatedMaxCpuWattage() : 0;
        long gpu = pearlMinerService.eligibleGpus().stream().filter(g -> controls.workerEnabled(g.deviceId())).filter(LocalGpuPowerService.Gpu::supportsDynamicPowerScaling)
                .mapToLong(LocalGpuPowerService.Gpu::maxWatts).sum();
        return cpu + gpu;
    }

    public long desiredGlobalPowerTarget() { return desiredGlobalPowerTarget; }
    public long desiredCpuPowerTarget() { return desiredCpuPowerTarget; }

    public long approximatePowerUsageSystem() {
        return 0;
    }

    public long getHotspotTemperature() {
        return 0;
    }

    public List<Pools> collectPoolsForAllWorkers() {
        return List.of();
    }

    public List<MinerStats.Worker> getWorkerStats() {
        return getWorkerStats(null);
    }

    public List<MinerStats.Worker> getExternallyVisibleWorkerStats() {
        return getWorkerStats().stream().filter(worker -> controls.workerEnabled(worker.deviceId())).toList();
    }

    private List<MinerStats.Worker> getWorkerStats(List<LocalGpuPowerService.Gpu> discoveredGpus) {
        List<MinerStats.Worker> workers = new ArrayList<>();
        workers.add(xmrMinerService.getWorkerStats());
        workers.addAll(pearlMinerService.workerStats(discoveredGpus == null ? gpuPowerService.discover() : discoveredGpus));
        return workers;
    }

    public MinerStats getStats() {
        return getStats(null);
    }

    public MinerStats getExternalStats() {
        List<MinerStats.Worker> visible = controls.get().externalControlEnabled()
                ? getExternallyVisibleWorkerStats() : List.of();
        return getStatsFromWorkers(visible);
    }

    public MinerStats getStats(List<LocalGpuPowerService.Gpu> discoveredGpus) {
        List<MinerStats.Worker> workers = new ArrayList<>();
        workers.addAll(getWorkerStats(discoveredGpus));
        return getStatsFromWorkers(workers);
    }

    private MinerStats getStatsFromWorkers(List<MinerStats.Worker> workers) {
        long totalPowerTarget = 0;
        long totalMinPowerTarget = 0;
        long totalDefaultPowerTarget = 0;
        long totalMaxPowerTarget = 0;
        long totalApproximatedPowerUsage = 0;
        double totalTerahashPerSecond = 0.0;
        double maxTemperature = 0.0;
        List<Pools> allPools = new ArrayList<>();

        boolean isAnyMining = false;
        boolean isAnyError = false;
        boolean isAnyPaused = false;

        for (MinerStats.Worker worker : workers) {
            totalPowerTarget += worker.powerTargetWatts();
            totalMinPowerTarget += worker.minPowerTarget();
            totalDefaultPowerTarget += worker.defaultPowerTarget();
            totalMaxPowerTarget += worker.maxPowerTarget();
            totalApproximatedPowerUsage += worker.approximatedPowerUsageWatts();
            totalTerahashPerSecond += worker.terahashPerSecond();

            if (worker.temperatureCelsius() > maxTemperature) {
                maxTemperature = worker.temperatureCelsius();
            }

            allPools.addAll(worker.pools());

            switch (worker.miningStatus()) {
                case MINING -> isAnyMining = true;
                case ERROR -> isAnyError = true;
                case PAUSED -> isAnyPaused = true;
            }
        }

        MinerStats.MinerStatus globalStatus = MinerStats.MinerStatus.STOPPED;
        if (isAnyMining) {
            globalStatus = MinerStats.MinerStatus.MINING;
        } else if (isAnyError) {
            globalStatus = MinerStats.MinerStatus.ERROR;
        } else if (isAnyPaused) {
            globalStatus = MinerStats.MinerStatus.PAUSED;
        }

        List<Pools> distinctPools = allPools.stream().distinct().toList();

        MinerStats.MinerIdentity identity = new MinerStats.MinerIdentity(
                hardwareIdentityService.getDeterministicUuid().toString(),
                hardwareIdentityService.getMacAddress(),
                "SolarMiner-PC-Agent"
        );

        return new MinerStats(
                identity,
                "SolarMiner Agent",
                globalStatus,
                totalPowerTarget,
                totalMinPowerTarget,
                totalDefaultPowerTarget,
                totalMaxPowerTarget,
                totalApproximatedPowerUsage,
                totalTerahashPerSecond,
                maxTemperature,
                distinctPools,
                workers
        );
    }
}
