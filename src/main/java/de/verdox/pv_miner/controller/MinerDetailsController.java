package de.verdox.pv_miner.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import de.verdox.pv_miner.dto.MinerDetailsPageDto;
import de.verdox.pv_miner.dto.MiningPageRequests.MinerEfficiencySettingsRequest;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.MinerRepository;
import de.verdox.pv_miner.miningcontroller.MinerAnalyticsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/pv-site/{siteId}/mining/miners/{minerId}")
@Tag(name = "Miner analytics")
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
        MinerEntity<?> miner = findMiner(siteId, minerId);
        return analyticsService.getDetails(miner, hours);
    }

    @PatchMapping("/efficiency-settings")
    public MinerDetailsPageDto updateEfficiencySettings(
            @PathVariable UUID siteId,
            @PathVariable UUID minerId,
            @RequestBody MinerEfficiencySettingsRequest request,
            @RequestParam(defaultValue = "24") int hours
    ) {
        MinerEntity<?> miner = findMiner(siteId, minerId);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Efficiency settings are required");
        }
        if (request.dispatchPriority() != null
                && (request.dispatchPriority() < 1 || request.dispatchPriority() > 10_000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dispatch priority must be between 1 and 10000");
        }
        if (request.nominalEfficiencyJTh() != null
                && (!Double.isFinite(request.nominalEfficiencyJTh())
                || request.nominalEfficiencyJTh() < 5
                || request.nominalEfficiencyJTh() > 200)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nominal efficiency must be between 5 and 200 J/TH");
        }
        miner.setEfficiencyDispatchPriority(request.dispatchPriority());
        miner.setNominalEfficiencyJTh(request.nominalEfficiencyJTh());
        minerRepository.save(miner);
        return analyticsService.getDetails(miner, hours);
    }

    private MinerEntity<?> findMiner(UUID siteId, UUID minerId) {
        MinerEntity<?> miner = minerRepository.findById(minerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Miner not found"));
        if (miner.getParentEntity() == null || !siteId.equals(miner.getParentEntity().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Miner does not belong to this PV site");
        }
        return miner;
    }
}
