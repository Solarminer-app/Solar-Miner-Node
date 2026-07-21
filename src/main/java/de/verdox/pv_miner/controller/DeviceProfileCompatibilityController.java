package de.verdox.pv_miner.controller;

import de.verdox.pv_miner.pvconfig.DeviceProfileCompatibilityService;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/pv-site/{siteId}/profile-compatibility")
@Tag(name = "PV device profiles")
public class DeviceProfileCompatibilityController {
    private final PVSiteRepository siteRepository;
    private final DeviceProfileCompatibilityService compatibilityService;

    public DeviceProfileCompatibilityController(PVSiteRepository siteRepository,
                                                DeviceProfileCompatibilityService compatibilityService) {
        this.siteRepository = siteRepository;
        this.compatibilityService = compatibilityService;
    }

    @GetMapping
    public DeviceProfileCompatibilityService.CompatibilityResult inspect(@PathVariable UUID siteId) {
        return compatibilityService.inspect(findSite(siteId));
    }

    @PutMapping
    public DeviceProfileCompatibilityService.CompatibilityResult repair(
            @PathVariable UUID siteId,
            @RequestBody DeviceProfileCompatibilityService.RepairRequest request
    ) {
        return compatibilityService.repair(findSite(siteId), request.repairs());
    }

    private PVSiteEntity findSite(UUID siteId) {
        return siteRepository.findById(siteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PV site not found"));
    }
}
