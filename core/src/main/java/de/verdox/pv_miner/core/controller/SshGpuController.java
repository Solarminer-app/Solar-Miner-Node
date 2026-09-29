package de.verdox.pv_miner.core.controller;

import de.verdox.pv_miner.core.service.SshGpuService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
@RequestMapping("/api/miners/gpu/ssh")
public class SshGpuController {
    private final SshGpuService ssh;

    public SshGpuController(SshGpuService ssh) { this.ssh = ssh; }

    @GetMapping
    public SshGpuService.Inventory inventory(@RequestParam String host, @RequestParam String user) {
        try { return ssh.inventory(host, user); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e); }
        catch (IOException | InterruptedException e) { throw unavailable(e); }
    }

    @GetMapping("/pearl-service")
    public String pearlService(@RequestParam String host, @RequestParam String user) {
        try { return ssh.pearlServiceStatus(host, user); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e); }
        catch (IOException | InterruptedException e) { throw unavailable(e); }
    }

    @PostMapping("/pearl-service")
    public boolean pearlService(@RequestBody PearlServiceRequest request) {
        try {
            ssh.setPearlServiceRunning(request.host(), request.user(), request.running());
            return true;
        } catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e); }
        catch (IllegalStateException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e); }
        catch (IOException | InterruptedException e) { throw unavailable(e); }
    }

    @PostMapping("/power-limit")
    public boolean powerLimit(@RequestBody PowerRequest request) {
        try {
            ssh.setPowerLimit(request.target(), request.vendor(), request.watts());
            return true;
        } catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e); }
        catch (IOException | InterruptedException e) { throw unavailable(e); }
    }

    @PostMapping("/core-clock")
    public boolean coreClock(@RequestBody ClockRequest request) {
        try {
            ssh.lockNvidiaCoreClock(request.target(), request.mhz());
            return true;
        } catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e); }
        catch (IOException | InterruptedException e) { throw unavailable(e); }
    }

    @PostMapping("/core-clock/reset")
    public boolean resetCoreClock(@RequestBody SshGpuService.Target target) {
        try {
            ssh.resetNvidiaCoreClock(target);
            return true;
        } catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e); }
        catch (IOException | InterruptedException e) { throw unavailable(e); }
    }

    private ResponseStatusException unavailable(Exception e) {
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
    }

    public record PowerRequest(SshGpuService.Target target, String vendor, int watts) { }
    public record ClockRequest(SshGpuService.Target target, int mhz) { }
    public record PearlServiceRequest(String host, String user, boolean running) { }
}
