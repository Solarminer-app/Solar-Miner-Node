package de.verdox.pv_miner.core.controller;

import de.verdox.pv_miner.core.miner.MiningOS;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import de.verdox.pv_miner.core.service.MinerService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/miners")
public class MinerController {
    private final MinerService minerService;

    public MinerController(MinerService minerService) {
        this.minerService = minerService;
    }

    @PostMapping("/identify-os")
    public MinerService.DetectedMiner identifyMiningOS(@RequestBody String ipv4) {
        return minerService.identifyMinerDetails(ipv4);
    }

    @PostMapping("/start")
    public boolean startMining(
            @RequestBody MinerDetails details,
            @RequestParam(name = "os", required = false) MiningOS os
    ) {
        return minerService.startMining(os, details);
    }

    @PostMapping("/stop")
    public boolean stopMining(
            @RequestBody MinerDetails details,
            @RequestParam(name = "os", required = false) MiningOS os
    ) {
        return minerService.stopMining(os, details);
    }

    @PostMapping("/pause")
    public boolean pauseMining(
            @RequestBody MinerDetails details,
            @RequestParam(name = "os", required = false) MiningOS os
    ) {
        return minerService.pauseMining(os, details);
    }

    @PostMapping("/resume")
    public boolean resumeMining(
            @RequestBody MinerDetails details,
            @RequestParam(name = "os", required = false) MiningOS os
    ) {
        return minerService.resumeMining(os, details);
    }

    @PostMapping("/pool-target")
    public boolean setPoolTarget(@RequestBody SetPoolRequest request) {
        return minerService.setPoolTarget(request.os(), request.minerDetails(), request.stratumUrl(), request.userName());
    }

    @PostMapping("/power-target")
    public boolean setPowerTarget(@RequestBody PowerTargetRequest request) {
        return minerService.setPowerTarget(request.os(), request.minerDetails(), request.watts());
    }

    @PostMapping("/power-target/increment")
    public boolean incrementPowerTarget(@RequestBody PowerTargetRequest request) {
        return minerService.incrementPowerTarget(request.os(), request.minerDetails(), request.watts());
    }

    @PostMapping("/power-target/decrement")
    public boolean decrementPowerTarget(@RequestBody PowerTargetRequest request) {
        return minerService.decrementPowerTarget(request.os(), request.minerDetails(), request.watts());
    }

    @PostMapping("/stats")
    public MinerStats getStats(
            @RequestBody MinerDetails details,
            @RequestParam(name = "os", required = false) MiningOS os
    ) {
        return minerService.queryStats(os, details.id().toString(), details);
    }

    public record PowerTargetRequest(MiningOS os, MinerDetails minerDetails, long watts) { }

    public record SetPoolRequest(MiningOS os, MinerDetails minerDetails, String stratumUrl, String userName) { }
}