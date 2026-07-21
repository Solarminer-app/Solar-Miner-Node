package de.verdox.solarminer.pcagent.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import de.verdox.solarminer.pcagent.dto.MinerStats;
import de.verdox.solarminer.pcagent.mining.MiningService;
import de.verdox.solarminer.pcagent.xmr.XmrConfigService;
import de.verdox.solarminer.pcagent.xmr.XmrDevFeeManager;
import de.verdox.solarminer.pcagent.xmr.download.XmrDownloadService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
@Tag(name = "PC mining agent")
public class MiningController {
    private final MiningService miningService;
    private final XmrConfigService xmrConfigService;
    private final XmrDevFeeManager xmrDevFeeManager;

    public MiningController(MiningService miningService, XmrConfigService xmrConfigService, XmrDevFeeManager xmrDevFeeManager) {
        this.miningService = miningService;
        this.xmrConfigService = xmrConfigService;
        this.xmrDevFeeManager = xmrDevFeeManager;
    }

    @GetMapping("identify")
    public boolean identify() {
        return true;
    }

    @PostMapping("/setPoolConfiguration")
    public boolean setPoolConfiguration(String poolUrl, String poolUser, double devFeePercentage) {
        if (poolUrl != null) {
            xmrConfigService.configureXmrig(XmrDownloadService.CONFIG_PATH, poolUrl, poolUser, false);
        }
        if (poolUser != null) {
            xmrDevFeeManager.registerUserConfig(poolUrl, poolUser, true);
        }
        if (devFeePercentage >= 0) {
            xmrDevFeeManager.setDevFee(devFeePercentage);
        }
        return true;
    }

    @PostMapping("/setPowerTarget")
    public boolean setPowerTarget(@RequestParam long powerTarget) {
        return miningService.setTarget(powerTarget);
    }

    @PostMapping("/increasePowerTarget")
    public boolean increasePowerTarget(@RequestParam long powerTarget) {
        return miningService.increasePowerTarget(powerTarget);
    }

    @PostMapping("/decreasePowerTarget")
    public boolean decreasePowerTarget(@RequestParam long powerTarget) {
        return miningService.decreasePowerTarget(powerTarget);
    }

    @PostMapping("/pause")
    public boolean pause() {
        return miningService.pauseMining();
    }

    @PostMapping("/resume")
    public boolean resume() {
        return miningService.resumeMining();
    }

    @GetMapping
    public MinerStats getMiningStats() {
        return miningService.getStats();
    }
}
