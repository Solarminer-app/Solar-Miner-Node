package de.verdox.solarminer.pcagent.controller;

import de.verdox.solarminer.pcagent.mining.BenchmarkSessionService;
import de.verdox.solarminer.pcagent.mining.BenchmarkSharingService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/agent/benchmarks")
public class BenchmarkController {
    private final BenchmarkSessionService sessions;
    private final BenchmarkSharingService sharing;

    public BenchmarkController(BenchmarkSessionService sessions, BenchmarkSharingService sharing) {
        this.sessions = sessions;
        this.sharing = sharing;
    }

    @GetMapping
    public BenchmarkSessionService.Session status() {
        return sessions.status();
    }

    @GetMapping("/match")
    public Map<String, Object> comparison(@RequestParam String hardwareType,
                                          @RequestParam String hardwareModel, @RequestParam String algorithm) {
        return sharing.comparison(hardwareType, hardwareModel, algorithm);
    }

    @PostMapping
    public BenchmarkSessionService.Session start(@RequestBody StartRequest request) {
        try {
            return sessions.start(request.mode(), request.seconds());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/cancel")
    public BenchmarkSessionService.Session cancel() {
        return sessions.cancel();
    }

    @GetMapping("/sharing")
    public boolean sharing() {
        return sharing.sharingEnabled();
    }

    @PostMapping("/sharing")
    public boolean sharing(@RequestBody SharingRequest request) {
        if (request == null || !sharing.setSharingEnabled(request.enabled()))
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save benchmark sharing choice");
        return sharing.sharingEnabled();
    }

    public record StartRequest(String mode, int seconds) {
    }

    public record SharingRequest(boolean enabled) {
    }
}
