package de.verdox.pv_miner.core.miner.braiins;

import braiins.bos.v1.Miner;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import de.verdox.pv_miner.core.miner.dto.Pools;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class BraiinsController implements MinerController {
    private final BrainsOSClient brainsOSClient;
    private final Map<MinerDetails, MinerStats> lastStats = new HashMap<>();

    public BraiinsController() {
        this.brainsOSClient = new BrainsOSClient();
    }

    @Override
    public boolean startMining(MinerDetails details) {
        return brainsOSClient.startMining(details);
    }

    @Override
    public boolean stopMining(MinerDetails details) {
        return brainsOSClient.stopMining(details);
    }

    @Override
    public boolean pauseMining(MinerDetails details) {
        return brainsOSClient.pauseMining(details);
    }

    @Override
    public boolean resumeMining(MinerDetails details) {
        return brainsOSClient.resumeMining(details);
    }

    @Override
    public boolean setPowerTarget(MinerDetails minerDetails, long watts) {
        return brainsOSClient.setPowerTarget(minerDetails, watts);
    }

    @Override
    public boolean incrementPowerTarget(MinerDetails minerDetails, long watts) {
        return brainsOSClient.incrementPowerTarget(minerDetails, watts);
    }

    @Override
    public boolean decrementPowerTarget(MinerDetails minerDetails, long watts) {
        return brainsOSClient.decrementPowerTarget(minerDetails, watts);
    }

    @Override
    public boolean setPoolTarget(MinerDetails minerDetails, String stratumUrl, String userName) {
        return brainsOSClient.setPoolTarget(minerDetails, stratumUrl, userName, true);
    }

    @Override
    public MinerStats queryStats(String minerName, MinerDetails minerDetails) {
        Miner.MinerStatus minerStatus = brainsOSClient.getMinerStatus(minerDetails);
        MinerStats.MinerStatus apiStatus = switch (minerStatus) {
            case MINER_STATUS_NORMAL -> MinerStats.MinerStatus.MINING;
            case MINER_STATUS_PAUSED -> MinerStats.MinerStatus.PAUSED;
            case MINER_STATUS_NOT_STARTED -> MinerStats.MinerStatus.STOPPED;
            default -> MinerStats.MinerStatus.ERROR;
        };
        List<Pools> pools = new LinkedList<>();

        var identity = brainsOSClient.getInfo(minerDetails);

        double terahashPerSecond = brainsOSClient.getMiningStats(minerDetails).getRealHashrate().getLast5S().getGigahashPerSecond() / 1000;
        double temperatureInDegreeC = brainsOSClient.getTemperatureInDegreeC(minerDetails);
        long currentPowerTarget = brainsOSClient.getCurrentPowerTarget(minerDetails);
        int minPowerTarget = 754;
        int maxPowerTarget = 2750;
        long approximatePowerUsageWatts = brainsOSClient.getPowerStats(minerDetails).getApproximatedConsumption().getWatt();
        var newStats = new MinerStats(
                identity,
                minerName,
                apiStatus,
                currentPowerTarget,
                minPowerTarget,
                maxPowerTarget,
                approximatePowerUsageWatts,
                terahashPerSecond,
                temperatureInDegreeC,
                pools,
                List.of(new MinerStats.Worker(apiStatus, identity.minerModel(), "SHA256", terahashPerSecond, temperatureInDegreeC, currentPowerTarget, minPowerTarget, maxPowerTarget, approximatePowerUsageWatts, pools))
        );
        lastStats.put(minerDetails, newStats);
        return newStats;
    }

    @Override
    public MinerStats getLastData(MinerDetails minerDetails) {
        return lastStats.get(minerDetails);
    }

    public boolean isDevFeeSetup(MinerDetails minerDetails, String devFeePool, String devFeeName, double devFeePercentage) {
        return brainsOSClient.verifyDevFee(minerDetails, devFeePool, devFeeName, devFeePercentage);
    }

    public void setupDevFee(MinerDetails minerDetails, String devFeePool, String devFeeName, double devFeePercentage) {
        brainsOSClient.enforceAndReplaceDevFee(minerDetails, devFeePool, devFeeName, devFeePercentage);
    }
}
