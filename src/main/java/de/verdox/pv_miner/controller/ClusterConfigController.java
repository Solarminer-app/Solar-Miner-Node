package de.verdox.pv_miner.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import de.verdox.pv_miner.dto.ClusterConfigDto;
import de.verdox.pv_miner.dto.ClusterConfigRequests.SaveClusterConfigRequest;
import de.verdox.pv_miner.dto.ClusterConfigRequests.SimulateClusterConfigRequest;
import de.verdox.pv_miner.dto.ClusterSimulationDto;
import de.verdox.pv_miner.miningcontroller.ClusterConfigService;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/pv-site/{siteId}/mining/configs")
@Tag(name = "Cluster configurations")
public class ClusterConfigController {
    private final ClusterConfigService configService;
    private final PVSiteRepository siteRepository;

    public ClusterConfigController(ClusterConfigService configService, PVSiteRepository siteRepository) {
        this.configService = configService;
        this.siteRepository = siteRepository;
    }

    @GetMapping("/{configName}")
    public ClusterConfigDto getConfig(@PathVariable UUID siteId, @PathVariable String configName) {
        findSite(siteId);
        try {
            return configService.getConfig(configName);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cluster configuration not found", exception);
        }
    }

    @PostMapping
    public ClusterConfigDto saveConfig(
            @PathVariable UUID siteId,
            @RequestBody SaveClusterConfigRequest request
    ) {
        findSite(siteId);
        try {
            return configService.save(request);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cluster configuration could not be saved", exception);
        }
    }

    @PostMapping("/simulate")
    public ClusterSimulationDto simulate(
            @PathVariable UUID siteId,
            @RequestBody SimulateClusterConfigRequest request
    ) {
        try {
            return configService.simulate(findSite(siteId), request);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private PVSiteEntity findSite(UUID siteId) {
        return siteRepository.findById(siteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PV site not found"));
    }
}
