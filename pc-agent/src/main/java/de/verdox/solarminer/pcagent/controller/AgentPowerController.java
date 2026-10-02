package de.verdox.solarminer.pcagent.controller;

import de.verdox.solarminer.pcagent.mining.MiningService;
import de.verdox.solarminer.pcagent.mining.AgentControlSettingsService;
import de.verdox.solarminer.pcagent.pearl.LocalGpuPowerService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Stable LAN contract for PV controllers. It intentionally hides CPU/GPU allocation details. */
@RestController
@RequestMapping("/api/agent/power-control")
public class AgentPowerController {
    private final MiningService mining;
    private final LocalGpuPowerService gpus;
    private final AgentControlSettingsService controls;

    public AgentPowerController(MiningService mining, LocalGpuPowerService gpus, AgentControlSettingsService controls) { this.mining = mining; this.gpus = gpus; this.controls = controls; }

    @GetMapping("/identity")
    public Identity identity() { return new Identity("solarminer-pc-agent", 1, "SolarMiner PC Agent"); }

    @GetMapping
    public PowerStatus status() {
        return status(false);
    }

    /** Node-only view omits locally opted-out devices and their identifiers. */
    @GetMapping("/external-status")
    public PowerStatus externalStatus() {
        return status(true);
    }

    private PowerStatus status(boolean externalView) {
        AgentControlSettingsService.Settings owner = controls.get();
        List<GpuStatus> cards = gpus.discover().stream().map(g -> new GpuStatus(g.deviceId(), g.vendor(), g.index(), g.model(),
                g.driverMinWatts(), g.driverMaxWatts(), g.minWatts(), g.maxWatts(), g.currentPowerLimitWatts(),
                g.currentWatts(), g.usageMeasurement(), g.supportsDynamicPowerScaling(), g.regulationError(), controls.workerEnabled(g.deviceId()))).toList();
        if (externalView) cards = owner.externalControlEnabled()
                ? cards.stream().filter(GpuStatus::externalControlEnabled).toList() : List.of();
        long min = mining.calculateMinPowerTargetFromComponents(), max = mining.calculateMaxPowerTargetFromComponents();
        List<GpuStatus> visibleCards = cards.stream().filter(GpuStatus::externalControlEnabled).toList();
        boolean measured = owner.externalControlEnabled() && mining.desiredCpuPowerTarget() == 0 && !visibleCards.isEmpty()
                && visibleCards.stream().allMatch(c -> "measured".equals(c.usageMeasurement()));
        Long usage = measured ? visibleCards.stream().map(GpuStatus::currentUsageWatts).mapToLong(Math::round).sum() : null;
        var application = mining.powerApplication();
        return new PowerStatus(owner.dynamicPowerScalingEnabled() && cards.stream().anyMatch(g -> g.externalControlEnabled() && g.supportsDynamicPowerScaling()), owner.dynamicPowerScalingEnabled(), owner.externalControlEnabled(), min, max, max == 0 ? 0 : Math.min(max, Math.max(min, max / 2)),
                mining.desiredGlobalPowerTarget(), usage,
                measured ? "measured" : "unavailable", mining.desiredGlobalPowerTarget() == 0,
                externalView ? Map.of() : owner.workerExternalControl(), cards,
                application.requestedWatts(), application.appliedWatts(), application.appliedCpuWatts(), application.appliedGpuWatts(),
                application.status().name(), application.failureReason());
    }

    @PostMapping("/target")
    public PowerStatus target(@RequestParam long watts) {
        if (!mining.setTarget(watts)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Leistungsziel konnte nicht sicher angewendet werden");
        return status();
    }

    /** Node-only command path; local dashboard actions intentionally use their existing endpoints. */
    @PostMapping("/external/target")
    public PowerStatus externalTarget(@RequestParam long watts) {
        requireExternalControl();
        // Dynamic GPU regulation is optional. When it is off, a positive target
        // still means that the Node may start this fixed-power miner.
        if (watts > 0 && !controls.get().dynamicPowerScalingEnabled()) {
            if (!mining.resumeExternally())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Miner konnte nicht gestartet werden");
            return externalStatus();
        }
        if (!mining.setExternalTarget(watts)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Leistungsziel konnte nicht sicher angewendet werden");
        return externalStatus();
    }

    @PostMapping("/external/pause")
    public boolean externalPause() { requireExternalControl(); return mining.pauseExternally(); }

    @PostMapping("/external/resume")
    public boolean externalResume() { requireExternalControl(); return mining.resumeExternally(); }

    @GetMapping("/settings")
    public AgentControlSettingsService.Settings settings() { return controls.get(); }

    @PostMapping("/settings")
    public AgentControlSettingsService.Settings settings(@RequestBody AgentControlSettingsService.Settings value) {
        if (!controls.update(value)) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Lokale Steuereinstellungen konnten nicht gespeichert werden");
        return controls.get();
    }

    @PostMapping("/workers/{workerId}/external-control")
    public AgentControlSettingsService.Settings workerControl(@PathVariable String workerId, @RequestParam boolean enabled) {
        if (!"cpu".equals(workerId) && gpus.discover().stream().noneMatch(gpu -> gpu.deviceId().equals(workerId)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Worker wurde nicht erkannt");
        if (!controls.setWorkerEnabled(workerId, enabled))
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Worker-Freigabe konnte nicht gespeichert werden");
        return controls.get();
    }

    private void requireExternalControl() {
        if (!controls.get().externalControlEnabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Externe Steuerung wurde lokal deaktiviert");
    }

    @PostMapping("/gpus/{deviceId}/limits")
    public GpuStatus limits(@PathVariable String deviceId, @RequestBody UserLimits request) {
        if (request == null || !gpus.setUserLimits(deviceId, request.minimumWatts(), request.maximumWatts()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GPU-Leistungsgrenzen liegen außerhalb der aktuellen Treibergrenzen");
        return status().gpus().stream().filter(g -> g.deviceId().equals(deviceId)).findFirst().orElseThrow();
    }

    public record UserLimits(int minimumWatts, int maximumWatts) { }
    public record Identity(String kind, int powerControlProtocolVersion, String model) { }
    public record PowerStatus(boolean supportsDynamicPowerScaling, boolean dynamicPowerScalingEnabled, boolean externalControlEnabled, long minPowerWatts, long maxPowerWatts,
                              long defaultPowerWatts, long currentTargetWatts, Long currentUsageWatts,
                              String usageMeasurement, boolean miningPaused, Map<String, Boolean> workerExternalControl, List<GpuStatus> gpus,
                              long requestedTargetWatts, long appliedTargetWatts, long appliedCpuTargetWatts, long appliedGpuTargetWatts,
                              String targetApplicationStatus, String targetApplicationFailureReason) { }
    public record GpuStatus(String deviceId, String vendor, int index, String model, int driverMinPowerLimitWatts,
                            int driverMaxPowerLimitWatts, int userMinPowerLimitWatts, int userMaxPowerLimitWatts,
                            Integer currentPowerLimitWatts, Double currentUsageWatts, String usageMeasurement,
                            boolean supportsDynamicPowerScaling, String regulationError, boolean externalControlEnabled) { }
}
