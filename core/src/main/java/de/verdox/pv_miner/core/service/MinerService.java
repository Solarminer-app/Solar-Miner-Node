package de.verdox.pv_miner.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.pv_miner.core.miner.MinerDataRegistry;
import de.verdox.pv_miner.core.miner.MiningOS;
import de.verdox.pv_miner.core.miner.agent.MinerAgentController;
import de.verdox.pv_miner.core.miner.antminer.AntminerBackend;
import de.verdox.pv_miner.core.miner.antminer.AntminerDTOs;
import de.verdox.pv_miner.core.miner.braiins.BraiinsController;
import de.verdox.pv_miner.core.miner.braiins.MinerController;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import de.verdox.pv_miner.core.miner.dto.Pools;
import de.verdox.pv_miner.core.miner.twentyoneenergy.TwentyOneEnergyController;
import de.verdox.pv_miner.core.miner.twentyoneenergy.TwentyOneEnergyDtos;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        TwentyOneEnergyDtos.StatusResponse.class,
        TwentyOneEnergyDtos.SystemStatusResponse.class,
        TwentyOneEnergyDtos.HeaterEnabledDto.class,
        TwentyOneEnergyDtos.PoolConfigDto.class,
        MinerDetails.class,
        MinerStats.class,
        Pools.class
})
@Service
public class MinerService {
    private static final Logger LOGGER = Logger.getLogger(MinerService.class.getName());
    private final ProxyDiscoveryService proxyDiscoveryService;
    private final DevFeeService devFeeService;
    private final Map<MiningOS, MinerControllerRegistration> registrations;
    private final MinerDataRegistry minerDataRegistry;

    @Autowired
    public MinerService(ProxyDiscoveryService proxyDiscoveryService, DevFeeService devFeeService, ObjectMapper objectMapper, MinerDataRegistry minerDataRegistry) {
        this.proxyDiscoveryService = proxyDiscoveryService;
        this.devFeeService = devFeeService;
        this.minerDataRegistry = minerDataRegistry;
        this.registrations = createDefaultRegistrations(objectMapper);
    }

    MinerService(ProxyDiscoveryService proxyDiscoveryService, DevFeeService devFeeService, MinerDataRegistry minerDataRegistry,
                 Map<MiningOS, MinerControllerRegistration> registrations) {
        this.proxyDiscoveryService = proxyDiscoveryService;
        this.devFeeService = devFeeService;
        this.minerDataRegistry = minerDataRegistry;
        this.registrations = registrationsForAllOperatingSystems(registrations);
    }

    private Map<MiningOS, MinerControllerRegistration> createDefaultRegistrations(ObjectMapper objectMapper) {
        MinerAgentController agentController = new MinerAgentController();
        AntminerBackend antminerController = new AntminerBackend(objectMapper);
        BraiinsController braiinsController = new BraiinsController(objectMapper);
        TwentyOneEnergyController twentyOneEnergyController = new TwentyOneEnergyController(objectMapper, false, null);

        EnumMap<MiningOS, MinerControllerRegistration> configured = new EnumMap<>(MiningOS.class);
        configured.put(MiningOS.AGENT, new MinerControllerRegistration(
                agentController,
                Set.of(MinerCapability.DYNAMIC_POWER_SCALING, MinerCapability.POOL_CONFIGURATION, MinerCapability.AGENT_COIN_CONFIGURATION),
                request -> {
                    String proxyUrl = request.proxyUrl(3335);
                    String proxyUserName = request.proxyUserName();
                    return proxyUrl != null && proxyUserName != null
                            && agentController.setReferral(request.minerDetails(), request.referralCode())
                            && agentController.setPoolTarget(request.minerDetails(), proxyUrl, proxyUserName);
                }
        ));
        configured.put(MiningOS.ANTMINER_STOCK_OS, new MinerControllerRegistration(
                antminerController,
                Set.of(MinerCapability.POOL_CONFIGURATION),
                request -> {
                    String proxyUrl = request.proxyUrl(3333);
                    String proxyUserName = request.proxyUserName();
                    return proxyUrl != null && proxyUserName != null
                            && antminerController.setPoolTarget(request.minerDetails(), proxyUrl, proxyUserName);
                }
        ));
        configured.put(MiningOS.BRAIINS, new MinerControllerRegistration(
                braiinsController,
                Set.of(MinerCapability.DYNAMIC_POWER_SCALING, MinerCapability.POOL_CONFIGURATION),
                request -> braiinsController.setPoolTargetNoProxy(request.minerDetails(), request.stratumUrl(), request.userName(),
                        devFeeService.resolveFeeTargets("bitcoin", request.referralCode()))
        ));
        // A heater starts monitoring-only. Control needs a persisted, hardware-verified level map.
        configured.put(MiningOS.TWENTY_ONE_ENERGY, new MinerControllerRegistration(
                twentyOneEnergyController,
                Set.of(MinerCapability.TWENTY_ONE_ENERGY_CALIBRATION),
                request -> false
        ));
        return registrationsForAllOperatingSystems(configured);
    }

    private static Map<MiningOS, MinerControllerRegistration> registrationsForAllOperatingSystems(
            Map<MiningOS, MinerControllerRegistration> configured
    ) {
        EnumMap<MiningOS, MinerControllerRegistration> complete = new EnumMap<>(MiningOS.class);
        for (MiningOS miningOS : MiningOS.values()) {
            complete.put(miningOS, configured.getOrDefault(miningOS, MinerControllerRegistration.unsupported()));
        }
        return Map.copyOf(complete);
    }

    public boolean startMining(MiningOS miningOS, MinerDetails details) {
        return tryOrGet(miningOS, minerController -> minerController.startMining(details), false);
    }

    public boolean stopMining(MiningOS miningOS, MinerDetails details) {
        return tryOrGet(miningOS, minerController -> minerController.stopMining(details), false);
    }

    public boolean pauseMining(MiningOS miningOS, MinerDetails details) {
        return tryOrGet(miningOS, minerController -> minerController.pauseMining(details), false);
    }

    public boolean resumeMining(MiningOS miningOS, MinerDetails details) {
        return tryOrGet(miningOS, minerController -> minerController.resumeMining(details), false);
    }

    public boolean setPoolTarget(MiningOS miningOS, MinerDetails details, String stratumUrl, String userName) {
        return setPoolTarget(miningOS, details, stratumUrl, userName, null);
    }

    public boolean setPoolTarget(MiningOS miningOS, MinerDetails details, String stratumUrl, String userName, String referralCode) {
        return registration(miningOS).configurePoolTarget(new MinerControllerRegistration.PoolTargetRequest(
                details, stratumUrl, userName, referralCode, proxyDiscoveryService.getCurrentProxyIp()
        ));
    }

    public boolean configurePearlAgent(MinerDetails details, String poolUrl, String wallet, String worker, String devices, String referralCode) {
        String proxyIp = proxyDiscoveryService.getCurrentProxyIp();
        String proxyUrl = proxyIp == null || proxyIp.isBlank() ? null : "stratum+tcp://" + proxyIp + ":3334";
        MinerAgentController agentController = agentController();
        return agentController != null && registration(MiningOS.AGENT).supports(MinerCapability.AGENT_COIN_CONFIGURATION)
                && agentController.configurePearl(details, poolUrl, proxyUrl, wallet, worker, devices, referralCode);
    }

    public TwentyOneEnergyController.CalibratedPowerMap calibrateTwentyOneEnergyPowerMap(MinerDetails details, boolean heatAndLoadRiskAcknowledged) {
        if (!registration(MiningOS.TWENTY_ONE_ENERGY).supports(MinerCapability.TWENTY_ONE_ENERGY_CALIBRATION)) {
            throw new IllegalStateException("21energy calibration is unavailable");
        }
        MinerController controller = registration(MiningOS.TWENTY_ONE_ENERGY).controller();
        if (!(controller instanceof TwentyOneEnergyController twentyOneEnergyController)) {
            throw new IllegalStateException("21energy controller is unavailable");
        }
        return twentyOneEnergyController.calibratePowerMap(details, heatAndLoadRiskAcknowledged);
    }

    public boolean configureMoneroAgent(MinerDetails details, String poolUrl, String wallet, String worker, String referralCode) {
        String proxyIp = proxyDiscoveryService.getCurrentProxyIp();
        String proxyUrl = proxyIp == null || proxyIp.isBlank() ? null : "stratum+tcp://" + proxyIp + ":3335";
        MinerAgentController agentController = agentController();
        return agentController != null && registration(MiningOS.AGENT).supports(MinerCapability.AGENT_COIN_CONFIGURATION)
                && agentController.configureMonero(details, poolUrl, proxyUrl, wallet, worker, referralCode);
    }

    public MinerAgentController.AgentCoinConfigurations agentCoinConfigurations(MinerDetails details) {
        MinerAgentController agentController = agentController();
        return agentController == null ? null : agentController.coinConfigurations(details);
    }

    public boolean setPowerTarget(MiningOS miningOS, MinerDetails details, long watts) {
        return tryOrGet(miningOS, minerController -> {
            if (!registration(miningOS).supports(MinerCapability.DYNAMIC_POWER_SCALING)) {
                return false;
            }
            return minerController.setPowerTarget(details, watts);
        }, false);
    }

    public boolean incrementPowerTarget(MiningOS miningOS, MinerDetails details, long watts) {
        return tryOrGet(miningOS, minerController -> {
            if (!registration(miningOS).supports(MinerCapability.DYNAMIC_POWER_SCALING)) {
                return false;
            }
            return minerController.incrementPowerTarget(details, watts);
        }, false);
    }

    public boolean decrementPowerTarget(MiningOS miningOS, MinerDetails details, long watts) {
        return tryOrGet(miningOS, minerController -> {
            if (!registration(miningOS).supports(MinerCapability.DYNAMIC_POWER_SCALING)) {
                return false;
            }
            return minerController.decrementPowerTarget(details, watts);
        }, false);
    }

    public MinerStats queryStats(MiningOS miningOS, String minerName, MinerDetails details) {
        return queryStats(miningOS, minerName, details, null);
    }

    public MinerStats queryStats(MiningOS miningOS, String minerName, MinerDetails details, String referralCode) {
        return tryOrGet(miningOS, minerController -> {
            try {
                var stats = minerController.queryStats(minerName, details);
                if (stats != null) {
                    minerDataRegistry.record(details, stats);
                    // Monitoring and unverified-control heaters must never have their pool configuration changed.
                    if (miningOS != MiningOS.TWENTY_ONE_ENERGY) {
                        devFeeService.enforceDevFee(stats.minerIdentity(), this, miningOS, details, referralCode);
                    }
                }
                return stats;
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Error while getting data of miner " + details.ipv4(), e);
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

    public void syncAgentReferral(MiningOS miningOS, MinerDetails details, String referralCode) {
        MinerAgentController agentController = agentController();
        if (miningOS == MiningOS.AGENT && agentController != null) agentController.setReferral(details, referralCode);
    }

    public boolean checkIfStandardCredentialsWork(MiningOS miningOS, MinerDetails details) {
        return tryOrGet(miningOS, minerController -> minerController.checkIfStandardCredentialsWork(details), false);
    }

    public boolean checkIfCustomCredentialsWork(MiningOS miningOS, MinerDetails details) {
        return tryOrGet(miningOS, minerController -> minerController.checkIfCustomCredentialsWork(details), false);
    }

    public boolean verifyDevFeeNative(MiningOS miningOS, MinerDetails details, List<DevFeeService.FeeTarget> feeTargets) {
        return tryOrGet(miningOS, minerController -> minerController.verifyDevFeeNative(details, feeTargets), false);
    }

    public void enforceDevFeeNative(MiningOS miningOS, MinerDetails details, List<DevFeeService.FeeTarget> feeTargets) {
        tryOrDo(miningOS, minerController -> minerController.enforceDevFeeNative(details, feeTargets));
    }

    private <RESULT> RESULT tryOrGet(MiningOS miningOS, Function<MinerController, RESULT> logic, RESULT defaultValue) {
        MinerController controller = registration(miningOS).controller();
        if (controller == null)
            return defaultValue;
        return logic.apply(controller);
    }

    private <RESULT> void tryOrDo(MiningOS miningOS, Consumer<MinerController> logic) {
        MinerController controller = registration(miningOS).controller();
        if (controller != null) {
            logic.accept(controller);
        }
    }

    private MinerControllerRegistration registration(MiningOS miningOS) {
        return miningOS == null
                ? MinerControllerRegistration.unsupported()
                : registrations.getOrDefault(miningOS, MinerControllerRegistration.unsupported());
    }

    private MinerAgentController agentController() {
        MinerController controller = registration(MiningOS.AGENT).controller();
        return controller instanceof MinerAgentController agentController ? agentController : null;
    }
}
