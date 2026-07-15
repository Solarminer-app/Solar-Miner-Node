package de.verdox.pv_miner.controller;

import de.verdox.pv_miner.dto.SetupCatalogDto;
import de.verdox.pv_miner.dto.SetupRequests;
import de.verdox.pv_miner.setup.SetupService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/setup")
@CrossOrigin(origins = "http://localhost:3000")
public class SetupController {
    private final SetupService setupService;

    public SetupController(SetupService setupService) {
        this.setupService = setupService;
    }

    @GetMapping("/catalog")
    public SetupCatalogDto getCatalog(@RequestParam(defaultValue = "de") String locale) {
        return setupService.getCatalog(locale);
    }

    @PostMapping("/catalog/refresh")
    public SetupCatalogDto refreshCatalog(@RequestParam(defaultValue = "de") String locale) throws IOException, InterruptedException {
        return setupService.refreshCatalog(locale);
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
