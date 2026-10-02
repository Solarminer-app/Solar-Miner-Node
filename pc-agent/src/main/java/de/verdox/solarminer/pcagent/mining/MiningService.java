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

    // Deliberately volatile: a process restart must not start mining from a stale target.
    private long desiredCpuPowerTarget;
    private long desiredGpuPowerTarget;
    private long desiredGlobalPowerTarget;
    private final PowerApplicationState powerApplication = new PowerApplicationState();
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
        List<LocalGpuPowerService.Gpu> allowedGpus = pearlMinerService.eligibleGpus().stream()
                .filter(gpu -> !external || controls.workerEnabled(gpu.deviceId()))
                .toList();
        List<LocalGpuPowerService.Gpu> cards = allowedGpus.stream()
                .filter(LocalGpuPowerService.Gpu::supportsDynamicPowerScaling).toList();
        PowerBudgetPlanner.Cpu cpu = cpuCapability(external);
        PowerBudgetPlanner.Plan requestedPlan = PowerBudgetPlanner.plan(powerTarget, cpu, plannerGpus(cards));
        if (requestedPlan.outcome() == PowerBudgetPlanner.Outcome.PAUSE) {
            boolean paused = external ? pauseExternally() : pauseAll();
            if (paused) powerApplication.paused(powerTarget);
            else powerApplication.failed(requestedPlan, "Miner konnten nicht vollständig angehalten werden");
            return paused;
        }
        if (requestedPlan.outcome() == PowerBudgetPlanner.Outcome.REJECTED) {
            if (external) pauseExternally(); else pauseAll();
            desiredGlobalPowerTarget = 0;
            desiredCpuPowerTarget = 0;
            desiredGpuPowerTarget = 0;
            powerApplication.rejected(requestedPlan);
            return false;
        }

        PowerBudgetPlanner.Plan appliedPlan = requestedPlan;
        String partialReason = null;
        if (requestedPlan.appliesGpuPowerLimits() && !gpuPowerService.setTotalPowerTarget(requestedPlan.gpuWatts(), cards)) {
            if (!(external ? stopGpus(allowedGpus) : pearlMinerService.stop()))
                return failGlobalBudget(external, requestedPlan, "GPU-Leistungsgrenzen konnten nicht gesetzt und GPU-Miner nicht sicher angehalten werden");
            appliedPlan = PowerBudgetPlanner.plan(requestedPlan.plannedWatts(), cpu, List.of());
            if (appliedPlan.outcome() != PowerBudgetPlanner.Outcome.APPLY)
                return failGlobalBudget(external, requestedPlan, "GPU-Leistungsgrenzen konnten nicht gesetzt werden");
            partialReason = "GPU-Leistungsgrenzen konnten nicht gesetzt werden; nur CPU-Budget wurde angewendet";
        }
        if (appliedPlan.gpuWatts() == 0 && !(external ? stopGpus(allowedGpus) : pearlMinerService.stop()))
            return failGlobalBudget(external, requestedPlan, "GPU-Miner konnten nicht sicher angehalten werden");

        long cpuTarget = appliedPlan.cpuWatts();
        long gpuTarget = appliedPlan.gpuWatts();
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
        desiredGlobalPowerTarget = appliedPlan.plannedWatts();
        desiredCpuPowerTarget = cpuTarget;
        desiredGpuPowerTarget = gpuTarget;
        boolean fullyStarted = (cpuTarget <= 0 || cpuStartSucceeded) && (gpuTarget <= 0 || gpuStartsSucceeded);
        if (!fullyStarted) {
            powerApplication.partiallyApplied(requestedPlan, appliedPlan,
                    "Mindestens ein Miner-Prozess konnte nach dem Anwenden des Leistungsplans nicht gestartet werden");
            return false;
        }
        if (partialReason != null) powerApplication.partiallyApplied(requestedPlan, appliedPlan, partialReason);
        else powerApplication.applied(appliedPlan);
        return true;
    }

    private PowerBudgetPlanner.Cpu cpuCapability(boolean external) {
        boolean available = (!external || controls.workerEnabled("cpu")) && !cpuManuallyPaused && xmrMinerService.readyForStart();
        return available
                ? new PowerBudgetPlanner.Cpu(true, xmrMinerService.getMinimumControllablePowerWatts(), xmrMinerService.getEstimatedMaxCpuWattage())
                : new PowerBudgetPlanner.Cpu(false, 0, 0);
    }

    private static List<PowerBudgetPlanner.Gpu> plannerGpus(List<LocalGpuPowerService.Gpu> cards) {
        return cards.stream().map(gpu -> new PowerBudgetPlanner.Gpu(gpu.deviceId(), gpu.minWatts(), gpu.maxWatts())).toList();
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
        if (success) powerApplication.paused(0);
        return success;
    }

    private boolean stopGpus(List<LocalGpuPowerService.Gpu> cards) {
        boolean success = true;
        for (LocalGpuPowerService.Gpu gpu : cards)
            success = pearlMinerService.stopGpu(gpu.vendor(), gpu.index()) && success;
        return success;
    }

    private boolean failGlobalBudget(boolean external, PowerBudgetPlanner.Plan plan, String reason) {
        desiredGlobalPowerTarget = 0;
        desiredCpuPowerTarget = 0;
        desiredGpuPowerTarget = 0;
        if (external) pauseExternally(); else pauseAll();
        powerApplication.failed(plan, reason);
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
    public PowerApplicationState.Snapshot powerApplication() { return powerApplication.snapshot(); }

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
