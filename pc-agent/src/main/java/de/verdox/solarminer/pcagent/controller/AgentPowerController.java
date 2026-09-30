package de.verdox.solarminer.pcagent.controller;

import de.verdox.solarminer.pcagent.mining.MiningService;
import de.verdox.solarminer.pcagent.mining.AgentControlSettingsService;
import de.verdox.solarminer.pcagent.pearl.LocalGpuPowerService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

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
        List<GpuStatus> cards = gpus.discover().stream().map(g -> new GpuStatus(g.deviceId(), g.vendor(), g.index(), g.model(),
                g.driverMinWatts(), g.driverMaxWatts(), g.minWatts(), g.maxWatts(), g.currentPowerLimitWatts(),
                g.currentWatts(), g.usageMeasurement(), g.supportsDynamicPowerScaling(), g.regulationError())).toList();
        long min = mining.calculateMinPowerTargetFromComponents(), max = mining.calculateMaxPowerTargetFromComponents();
        boolean measured = mining.desiredCpuPowerTarget() == 0 && !cards.isEmpty()
                && cards.stream().allMatch(c -> "measured".equals(c.usageMeasurement()));
        Long usage = measured ? cards.stream().map(GpuStatus::currentUsageWatts).mapToLong(Math::round).sum() : null;
        AgentControlSettingsService.Settings owner = controls.get();
        return new PowerStatus(owner.dynamicPowerScalingEnabled() && cards.stream().anyMatch(GpuStatus::supportsDynamicPowerScaling), owner.dynamicPowerScalingEnabled(), owner.externalControlEnabled(), min, max, max == 0 ? 0 : Math.min(max, Math.max(min, max / 2)),
                mining.desiredGlobalPowerTarget(), usage,
                measured ? "measured" : "unavailable", mining.desiredGlobalPowerTarget() == 0, cards);
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
        if (watts > 0 && !controls.get().dynamicPowerScalingEnabled())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dynamische Leistungsregelung wurde lokal deaktiviert");
        if (!mining.setTarget(watts)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Leistungsziel konnte nicht sicher angewendet werden");
        return status();
    }

    @PostMapping("/external/pause")
    public boolean externalPause() { requireExternalControl(); return mining.pauseAll(); }

    @PostMapping("/external/resume")
    public boolean externalResume() { requireExternalControl(); return mining.resumeAll(); }

    @GetMapping("/settings")
    public AgentControlSettingsService.Settings settings() { return controls.get(); }

    @PostMapping("/settings")
    public AgentControlSettingsService.Settings settings(@RequestBody AgentControlSettingsService.Settings value) {
        if (!controls.update(value)) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Lokale Steuereinstellungen konnten nicht gespeichert werden");
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
                              String usageMeasurement, boolean miningPaused, List<GpuStatus> gpus) { }
    public record GpuStatus(String deviceId, String vendor, int index, String model, int driverMinPowerLimitWatts,
                            int driverMaxPowerLimitWatts, int userMinPowerLimitWatts, int userMaxPowerLimitWatts,
                            Integer currentPowerLimitWatts, Double currentUsageWatts, String usageMeasurement,
                            boolean supportsDynamicPowerScaling, String regulationError) { }
}
