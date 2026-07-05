package de.verdox.pv_miner.core.miner.braiins;

import de.verdox.pv_miner.core.miner.DevFeeConstants;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import de.verdox.pv_miner.core.miner.dto.Pools;
import de.verdox.pv_miner.core.service.DevFeeService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public interface BrainsOSBackend {

    boolean startMining(MinerDetails details);

    boolean stopMining(MinerDetails details);

    boolean pauseMining(MinerDetails details);

    boolean resumeMining(MinerDetails details);

    boolean setPowerTarget(MinerDetails details, long watts);

    boolean incrementPowerTarget(MinerDetails details, long watts);

    boolean decrementPowerTarget(MinerDetails details, long watts);

    boolean setPoolTarget(MinerDetails details, String stratumUrl, String userName, boolean alsoSetDevFee);

    MinerStats.MinerIdentity getInfo(MinerDetails details);

    long getCurrentPowerTarget(MinerDetails details);

    PowerLimit getPowerLimit(MinerDetails details);

    TemperatureLimit getTargetTemperature(MinerDetails details);

    TemperatureLimit getHotTemperature(MinerDetails details);

    TemperatureLimit getDangerousTemperature(MinerDetails details);

    long getApproximatePowerUsage(MinerDetails details);

    double getTemperatureInDegreeC(MinerDetails details);

    double getHashrateTH(MinerDetails details);

    MinerStats.MinerStatus getMinerStatus(MinerDetails details);

    List<Pools> getPools(MinerDetails details);

    boolean checkIfStandardCredentialsWork(MinerDetails details);

    boolean checkIfCustomCredentialsWork(MinerDetails details);

    // ### Dev Fee with Proxy

    void enforceProxyRouting(MinerDetails minerDetails, String proxyIp, String proxyPort);

    boolean verifyProxyRouting(MinerDetails minerDetails, String expectedUrl);

    // ### Dev Fee no Proxy

    void enforceDevFeeNative(MinerDetails minerDetails, List<DevFeeService.FeeTarget> feeTargets);

    boolean verifyDevFeeNative(MinerDetails minerDetails, List<DevFeeService.FeeTarget> feeTargets);

    boolean setPoolTargetAndSetNativeDevFees(MinerDetails minerDetails, String stratumUrl, String userName, boolean alsoSetDevFee, List<DevFeeService.FeeTarget> feeTargets);

    record PowerLimit(long min, long max, long defaultValue, String unit) {
    }

    record TemperatureLimit(double min, double max, double defaultValue, String unit) {
    }

    default String derivePoolGroupName(DevFeeService.FeeTarget feeTarget) {

        return DevFeeConstants.DEV_FEE_POOL_GROUP_NAME+"-" + UUID.nameUUIDFromBytes((feeTarget.poolAddress()+feeTarget.workerName()).getBytes(StandardCharsets.UTF_8));
    }
}
