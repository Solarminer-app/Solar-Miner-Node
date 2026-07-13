package de.verdox.pv_miner.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.pv_miner.core.miner.MinerDataRegistry;
import de.verdox.pv_miner.core.miner.MiningOS;
import de.verdox.pv_miner.core.miner.agent.MinerAgentController;
import de.verdox.pv_miner.core.miner.antminer.AntminerBackend;
import de.verdox.pv_miner.core.miner.antminer.AntminerDTOs;
import de.verdox.pv_miner.core.miner.braiins.BraiinsController;
import de.verdox.pv_miner.core.miner.MinerController;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import de.verdox.pv_miner.core.miner.dto.Pools;
import de.verdox.pv_miner.core.miner.whatsminer.WhatsMinerController;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

@RegisterReflectionForBinding({
        AntminerDTOs.BlinkStatusResponse.class,
        AntminerDTOs.ApiStatus.class,
        AntminerDTOs.ApiInfo.class,
        AntminerDTOs.SystemInfoResponse.class,
        AntminerDTOs.MinerTypeResponse.class,
        AntminerDTOs.NetworkInfoResponse.class,
        AntminerDTOs.MinerConfigResponse.class,
        AntminerDTOs.MinerConfigResponse.PoolConfig.class,
        AntminerDTOs.PoolInfoResponse.class,
        AntminerDTOs.PoolInfoResponse.PoolDetail.class,
        AntminerDTOs.SummaryResponse.class,
        AntminerDTOs.SummaryResponse.SummaryDetail.class,
        AntminerDTOs.SummaryResponse.DeviceStatus.class,
        AntminerDTOs.StatsResponse.class,
        AntminerDTOs.StatsResponse.StatsDetail.class,
        AntminerDTOs.StatsResponse.ChainDetail.class,
        AntminerDTOs.SetNetworkConfigRequest.class,
        AntminerDTOs.SetMinerConfigRequest.class,
        AntminerDTOs.SetMinerConfigRequest.Pool.class,
        AntminerDTOs.PasswordRequest.class,
        MinerDetails.class,
        MinerStats.class,
        Pools.class
})
@Service
public class MinerService {
    private static final Logger LOGGER = Logger.getLogger(MinerService.class.getName());
    private final MinerAgentController agentController;
    private final BraiinsController braiinsController;
    private final AntminerBackend antminerBackend;
    private final ProxyDiscoveryService proxyDiscoveryService;
    private final DevFeeService devFeeService;
    private final Map<MiningOS, MinerController> controllersByOS = new ConcurrentHashMap<>();
    private final MinerDataRegistry minerDataRegistry;

    public MinerService(ProxyDiscoveryService proxyDiscoveryService, DevFeeService devFeeService, ObjectMapper objectMapper, MinerDataRegistry minerDataRegistry) {
        this.proxyDiscoveryService = proxyDiscoveryService;
        this.minerDataRegistry = minerDataRegistry;
        this.devFeeService = devFeeService;
        this.agentController = new MinerAgentController();
        this.antminerBackend = new AntminerBackend(objectMapper);
        this.braiinsController = new BraiinsController(objectMapper);

        controllersByOS.put(MiningOS.AGENT, new MinerAgentController());
        controllersByOS.put(MiningOS.ANTMINER_STOCK_OS, new AntminerBackend(objectMapper));
        controllersByOS.put(MiningOS.BRAIINS, new BraiinsController(objectMapper));
        controllersByOS.put(MiningOS.WHATSMINER_STOCK_OS, new WhatsMinerController());
    }

    public boolean startMining(MiningOS miningOS, MinerDetails details) {
        return tryOrGet(miningOS, minerController -> minerController.startMining(details), false);
    }

    public boolean stopMining(MiningOS miningOS, MinerDetails details) {
        return tryOrGet(miningOS, minerController -> minerController.startMining(details), false);
    }

    public boolean pauseMining(MiningOS miningOS, MinerDetails details) {
        return tryOrGet(miningOS, minerController -> minerController.pauseMining(details), false);
    }

    public boolean resumeMining(MiningOS miningOS, MinerDetails details) {
        return tryOrGet(miningOS, minerController -> minerController.resumeMining(details), false);
    }

    public boolean setPoolTarget(MiningOS miningOS, MinerDetails details, String stratumUrl, String userName) {
        String proxyIp = proxyDiscoveryService.getCurrentProxyIp();
        String proxyStratumUrl = "stratum+tcp://" + proxyIp + ":3333";
        String cleanTargetUrl = stratumUrl.replace("stratum+tcp://", "");
        String proxyUserName = cleanTargetUrl + ";" + userName + ";x";

        return switch (miningOS) {
            case AGENT -> agentController.setPoolTarget(details, proxyStratumUrl, proxyUserName);
            case BRAIINS -> braiinsController.setPoolTargetNoProxy(details, stratumUrl, userName, devFeeService.fetchFeeTargets("bitcoin"));
            case ANTMINER_STOCK_OS -> antminerBackend.setPoolTarget(details, proxyStratumUrl, proxyUserName);
            case BIXBIT, CANAAN_STOCK_OS, INNOSILICON_STOCK_OS, VNISH,
                 WHATSMINER_STOCK_OS, LUX_OS, HIVEON_ASIC, HIVE_OS, MS_OS, RAVE_OS -> false;
        };
    }

    public boolean setPowerTarget(MiningOS miningOS, MinerDetails details, long watts) {
        return tryOrGet(miningOS, minerController -> {
            if (!miningOS.supportsDynamicPowerScaling()) {
                return false;
            }
            return minerController.setPowerTarget(details, watts);
        }, false);
    }

    public boolean incrementPowerTarget(MiningOS miningOS, MinerDetails details, long watts) {
        return tryOrGet(miningOS, minerController -> {
            if (!miningOS.supportsDynamicPowerScaling()) {
                return false;
            }
            return minerController.incrementPowerTarget(details, watts);
        }, false);
    }

    public boolean decrementPowerTarget(MiningOS miningOS, MinerDetails details, long watts) {
        return tryOrGet(miningOS, minerController -> {
            if (!miningOS.supportsDynamicPowerScaling()) {
                return false;
            }
            return minerController.decrementPowerTarget(details, watts);
        }, false);
    }

    public MinerStats queryStats(MiningOS miningOS, String minerName, MinerDetails details) {
        return tryOrGet(miningOS, minerController -> {
            try {
                var stats = minerController.queryStats(minerName, details);
                if (stats != null) {
                    minerDataRegistry.record(details, stats);
                    devFeeService.enforceDevFee(stats.minerIdentity(), this, miningOS, details);
                }
                return stats;
            }
            catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Error while getting data of miner "+details.ipv4(), e);
                var cachedStats = minerDataRegistry.getIdentity(details);
                return new MinerStats(
                        cachedStats.minerIdentity(),
                        minerName,
                        MinerStats.MinerStatus.STOPPED,
                        cachedStats.currentPowerTargetWatts(),
                        cachedStats.minPowerTarget(),
                        cachedStats.defaultPowerTarget(),
                        cachedStats.maxPowerTarget(),
                        0L,
                        0.0D,
                        0.0D,
                        List.of(),
                        List.of()
                );
            }
        }, null);
    }

    public boolean verifyProxyRouting(MiningOS miningOS, MinerDetails details) {
        String proxyIp = proxyDiscoveryService.getCurrentProxyIp();
        return tryOrGet(miningOS, minerController -> minerController.verifyProxyRouting(details, proxyIp), false);
    }

    public void enforceProxyRouting(MiningOS miningOS, MinerDetails details) {
        String proxyIp = proxyDiscoveryService.getCurrentProxyIp();
        String proxyPort = "3333";

        tryOrDo(miningOS, minerController -> minerController.enforceProxyRouting(details, proxyIp, proxyPort));
    }

    public boolean checkIfStandardCredentialsWork(MiningOS miningOS, String minerName, MinerDetails details) {
        return tryOrGet(miningOS, minerController -> minerController.checkIfStandardCredentialsWork(details), false);
    }

    public boolean checkIfCustomCredentialsWork(MiningOS miningOS, String minerName, MinerDetails details) {
        return tryOrGet(miningOS, minerController -> minerController.checkIfCustomCredentialsWork(details), false);
    }

    public boolean verifyDevFeeNative(MiningOS miningOS, MinerDetails details, List<DevFeeService.FeeTarget> feeTargets) {
        return tryOrGet(miningOS, minerController -> minerController.verifyDevFeeNative(details, feeTargets), false);
    }

    public void enforceDevFeeNative(MiningOS miningOS, MinerDetails details, List<DevFeeService.FeeTarget> feeTargets) {
        tryOrDo(miningOS, minerController -> minerController.enforceDevFeeNative(details, feeTargets));
    }

    private <RESULT> RESULT tryOrGet(MiningOS miningOS, Function<MinerController, RESULT> logic, RESULT defaultValue) {
        if (!controllersByOS.containsKey(miningOS))
            return defaultValue;
        return logic.apply(controllersByOS.get(miningOS));
    }

    private <RESULT> void tryOrDo(MiningOS miningOS, Consumer<MinerController> logic) {
        if (controllersByOS.containsKey(miningOS)) {
            logic.accept(controllersByOS.get(miningOS));
        }
    }
}