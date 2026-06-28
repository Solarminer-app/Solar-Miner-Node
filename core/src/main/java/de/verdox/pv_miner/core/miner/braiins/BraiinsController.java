package de.verdox.pv_miner.core.miner.braiins;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.cgminerapi.CGMinerClient;
import de.verdox.cgminerapi.StandardCommand;
import de.verdox.pv_miner.core.miner.braiins.graphql.BosminerUnavailableException;
import de.verdox.pv_miner.core.miner.braiins.graphql.BrainsOSGraphQLClient;
import de.verdox.pv_miner.core.miner.braiins.grpc.BraiinsOSGRPCClient;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import de.verdox.pv_miner.core.miner.dto.Pools;
import de.verdox.pv_miner.core.util.AsicMinerSpec;

import java.io.IOException;
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
            int maxPowerTarget = Math.toIntExact(client.getPowerLimit(minerDetails).defaultValue());
            long approximatePowerUsageWatts = client.getApproximatePowerUsage(minerDetails);
            var newStats = new MinerStats(identity, minerName, apiStatus, currentPowerTarget, minPowerTarget, maxPowerTarget, approximatePowerUsageWatts, terahashPerSecond, temperatureInDegreeC, pools, List.of(new MinerStats.Worker(apiStatus, identity.minerModel(), "SHA256", terahashPerSecond, temperatureInDegreeC, currentPowerTarget, minPowerTarget, maxPowerTarget, approximatePowerUsageWatts, pools)));
            lastStats.put(minerDetails, newStats);
            return newStats;
        }
        catch (BosminerUnavailableException e) {
            return new MinerStats(
                    identity,
                    minerName,
                    MinerStats.MinerStatus.STOPPED,
                    0L,
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

    public boolean isDevFeeSetup(MinerDetails minerDetails, String devFeePool, String devFeeName, double devFeePercentage) {
        return client(minerDetails).verifyDevFee(minerDetails, devFeePool, devFeeName, devFeePercentage);
    }

    public void setupDevFee(MinerDetails minerDetails, String devFeePool, String devFeeName, double devFeePercentage) {
        client(minerDetails).enforceAndReplaceDevFee(minerDetails, devFeePool, devFeeName, devFeePercentage);
    }

    public boolean checkIfStandardCredentialsWork(MinerDetails details) {
        return client(details).checkIfStandardCredentialsWork(details);
    }

    public boolean checkIfCustomCredentialsWork(MinerDetails details) {
        return client(details).checkIfCustomCredentialsWork(details);
    }
}
