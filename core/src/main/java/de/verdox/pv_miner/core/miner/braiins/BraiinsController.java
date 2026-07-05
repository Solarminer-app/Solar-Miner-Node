package de.verdox.pv_miner.core.miner.braiins;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.cgminerapi.CGMinerClient;
import de.verdox.pv_miner.core.miner.braiins.graphql.BosminerUnavailableException;
import de.verdox.pv_miner.core.miner.braiins.graphql.BrainsOSGraphQLClient;
import de.verdox.pv_miner.core.miner.braiins.grpc.BraiinsOSGRPCClient;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import de.verdox.pv_miner.core.miner.dto.Pools;
import de.verdox.pv_miner.core.service.DevFeeService;
import de.verdox.pv_miner.core.util.AsicMinerSpec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BraiinsController implements MinerController {
    private final BraiinsOSGRPCClient brainsOSGRPCClient;
    private final BrainsOSGraphQLClient brainsOSGraphQLClient;
    private final CGMinerClient cgMinerClient;
    private final Map<MinerDetails, MinerStats> lastStats = new HashMap<>();

    public BraiinsController(ObjectMapper objectMapper) {
        this.brainsOSGRPCClient = new BraiinsOSGRPCClient();
        this.brainsOSGraphQLClient = new BrainsOSGraphQLClient();
        this.cgMinerClient = new CGMinerClient(objectMapper);
    }

    private BrainsOSBackend client(MinerDetails details) {
        String rawVersion = brainsOSGraphQLClient.version(details);

        BraiinsVersion version = BraiinsVersion.parse(rawVersion);

        BraiinsAPICapabilities apiCapabilities = BraiinsAPICapabilities.fromVersion(version);

        return apiCapabilities.supportsGRPC() ? brainsOSGRPCClient : brainsOSGraphQLClient;
    }

    @Override
    public boolean startMining(MinerDetails details) {
        return client(details).startMining(details);
    }

    @Override
    public boolean stopMining(MinerDetails details) {
        return client(details).stopMining(details);
    }

    @Override
    public boolean pauseMining(MinerDetails details) {
        return client(details).pauseMining(details);
    }

    @Override
    public boolean resumeMining(MinerDetails details) {
        return client(details).resumeMining(details);
    }

    @Override
    public boolean setPowerTarget(MinerDetails minerDetails, long watts) {
        return client(minerDetails).setPowerTarget(minerDetails, watts);
    }

    @Override
    public boolean incrementPowerTarget(MinerDetails minerDetails, long watts) {
        return client(minerDetails).incrementPowerTarget(minerDetails, watts);
    }

    @Override
    public boolean decrementPowerTarget(MinerDetails minerDetails, long watts) {
        return client(minerDetails).decrementPowerTarget(minerDetails, watts);
    }

    @Override
    public boolean setPoolTarget(MinerDetails minerDetails, String stratumUrl, String userName) {
        return client(minerDetails).setPoolTarget(minerDetails, stratumUrl, userName, true);
    }

    @Override
    public MinerStats queryStats(String minerName, MinerDetails minerDetails) {
        var client = client(minerDetails);
        var identity = client.getInfo(minerDetails);
        int asicStandardPowerTarget = AsicMinerSpec.find(identity.minerModel()).watts();
        try {
            MinerStats.MinerStatus apiStatus = client.getMinerStatus(minerDetails);
            List<Pools> pools = client.getPools(minerDetails);

            double terahashPerSecond = client.getHashrateTH(minerDetails);
            double temperatureInDegreeC = client.getTemperatureInDegreeC(minerDetails);
            long currentPowerTarget = client.getCurrentPowerTarget(minerDetails);

            int minPowerTarget = Math.toIntExact(client.getPowerLimit(minerDetails).min());
            int defaultPowerTarget = Math.toIntExact(client.getPowerLimit(minerDetails).defaultValue());
            int maxPowerTarget = Math.toIntExact(client.getPowerLimit(minerDetails).max());
            long approximatePowerUsageWatts = client.getApproximatePowerUsage(minerDetails);
            var newStats = new MinerStats(identity, minerName, apiStatus, currentPowerTarget, minPowerTarget, defaultPowerTarget, maxPowerTarget, approximatePowerUsageWatts, terahashPerSecond, temperatureInDegreeC, pools, List.of(new MinerStats.Worker(apiStatus, identity.minerModel(), "SHA256", terahashPerSecond, temperatureInDegreeC, currentPowerTarget, minPowerTarget, defaultPowerTarget, maxPowerTarget, approximatePowerUsageWatts, pools)));
            lastStats.put(minerDetails, newStats);
            return newStats;
        } catch (BosminerUnavailableException e) {
            return new MinerStats(
                    identity,
                    minerName,
                    MinerStats.MinerStatus.STOPPED,
                    0L,
                    asicStandardPowerTarget,
                    asicStandardPowerTarget,
                    asicStandardPowerTarget,
                    0L,
                    0.0D,
                    0.0D,
                    List.of(),
                    List.of()
            );
        }
    }

    @Override
    public MinerStats getLastData(MinerDetails minerDetails) {
        return lastStats.get(minerDetails);
    }

    @Override
    public boolean checkIfStandardCredentialsWork(MinerDetails details) {
        return client(details).checkIfStandardCredentialsWork(details);
    }

    @Override
    public boolean checkIfCustomCredentialsWork(MinerDetails details) {
        return client(details).checkIfCustomCredentialsWork(details);
    }

    @Override
    public boolean verifyProxyRouting(MinerDetails minerDetails, String proxyIp) {
        return client(minerDetails).verifyProxyRouting(minerDetails, proxyIp);
    }

    @Override
    public void enforceProxyRouting(MinerDetails minerDetails, String proxyIp, String proxyPort) {
        client(minerDetails).enforceProxyRouting(minerDetails, proxyIp, proxyPort);
    }

    @Override
    public boolean verifyDevFeeNative(MinerDetails minerDetails, List<DevFeeService.FeeTarget> feeTargets) {
        return client(minerDetails).verifyDevFeeNative(minerDetails, feeTargets);
    }

    @Override
    public void enforceDevFeeNative(MinerDetails minerDetails, List<DevFeeService.FeeTarget> feeTargets) {
        client(minerDetails).enforceDevFeeNative(minerDetails, feeTargets);
    }

    public boolean setPoolTargetNoProxy(MinerDetails minerDetails, String stratumUrl, String userName, List<DevFeeService.FeeTarget> feeTargets) {
        return client(minerDetails).setPoolTargetAndSetNativeDevFees(minerDetails, stratumUrl, userName, true, feeTargets);
    }
}
