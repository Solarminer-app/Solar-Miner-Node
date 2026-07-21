package de.verdox.pv_miner.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import de.verdox.pv_miner.dto.SetupCatalogDto;
import de.verdox.pv_miner.dto.SetupRequests;
import de.verdox.pv_miner.setup.SetupService;
import de.verdox.pv_miner.setup.PvDevicePreviewService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/setup")
@CrossOrigin(origins = "http://localhost:3000")
@Tag(name = "Setup")
public class SetupController {
    private final SetupService setupService;
    private final PvDevicePreviewService previewService;

    public SetupController(SetupService setupService, PvDevicePreviewService previewService) {
        this.setupService = setupService;
        this.previewService = previewService;
    }

    @GetMapping("/catalog")
    public SetupCatalogDto getCatalog(@RequestParam(defaultValue = "de") String locale) {
        return setupService.getCatalog(locale);
    }

    @PostMapping("/catalog/refresh")
    public SetupCatalogDto refreshCatalog(@RequestParam(defaultValue = "de") String locale) throws IOException, InterruptedException {
        return setupService.refreshCatalog(locale);
    }

    @GetMapping("/pv-devices/profiles")
    public List<SetupRequests.PvDeviceProfileDto> getPvDeviceProfiles(
            @RequestParam(required = false) String providerId,
            @RequestParam(defaultValue = "") String query
    ) {
        return setupService.getPvDeviceProfiles(providerId, query);
    }

    @PostMapping("/pv-devices/discover")
    public List<SetupRequests.DiscoveredPvDeviceDto> discoverPvDevices(
            @RequestBody SetupRequests.PvDiscoveryRequest request
    ) {
        return setupService.discoverPvDevices(request);
    }

    @PostMapping("/pv-devices/preview")
    public SetupRequests.PvDevicePreviewDto previewPvDevice(
            @RequestBody SetupRequests.PvDevicePreviewRequest request
    ) {
        return previewService.preview(request);
    }

    @PostMapping("/options/{kind}/{providerId}/validate")
    public SetupRequests.ProviderValidationDto validateProvider(
            @PathVariable String kind,
            @PathVariable String providerId,
            @RequestBody SetupRequests.ProviderValidationRequest request
    ) {
        return setupService.validateProvider(kind, providerId, request.values());
    }

    @PostMapping
    public SetupRequests.SetupCreatedDto createSetup(@RequestBody SetupRequests.CreateSetupRequest request) {
        return setupService.createSetup(request);
    }
}
