package de.verdox.pv_miner.controller;

import de.verdox.pv_miner.entity.EntityService;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/start-info")
public class StartInfoController {

    private final PVSiteRepository pvSiteRepository;

    public StartInfoController(PVSiteRepository pvSiteRepository) {
        this.pvSiteRepository = pvSiteRepository;
    }

    @GetMapping
    public StartViewData getStartInfo() {
        long currentCount = 0;
        try {
            currentCount = pvSiteRepository.count();
        } catch (Exception ignored) {

        }

        long limit = EntityService.PV_SITE_LIMIT;
        boolean limitExceeded = currentCount >= limit;

        List<PVSiteDTO> sites = pvSiteRepository.findAll(PageRequest.of(0, 100))
                .stream()
                .map(site -> new PVSiteDTO(site.getId().toString(), site.getName()))
                .collect(Collectors.toList());

        return new StartViewData(sites, currentCount, limit, limitExceeded);
    }

    public record StartViewData(List<PVSiteDTO> sites, long currentCount, long limit, boolean limitExceeded) {}

    public record PVSiteDTO(String id, String name) {}
}
