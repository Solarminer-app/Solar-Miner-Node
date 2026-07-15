package de.verdox.pv_miner.controller;

import de.verdox.pv_miner.dto.MinerDetailsPageDto;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.MinerRepository;
import de.verdox.pv_miner.miningcontroller.MinerAnalyticsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/pv-site/{siteId}/mining/miners/{minerId}")
public class MinerDetailsController {
    private final MinerRepository minerRepository;
    private final MinerAnalyticsService analyticsService;

    public MinerDetailsController(MinerRepository minerRepository, MinerAnalyticsService analyticsService) {
        this.minerRepository = minerRepository;
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public MinerDetailsPageDto getDetails(
            @PathVariable UUID siteId,
            @PathVariable UUID minerId,
            @RequestParam(defaultValue = "24") int hours
    ) {
        MinerEntity<?> miner = minerRepository.findById(minerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Miner not found"));
        if (miner.getParentEntity() == null || !siteId.equals(miner.getParentEntity().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Miner does not belong to this PV site");
        }
        return analyticsService.getDetails(miner, hours);
    }
}
