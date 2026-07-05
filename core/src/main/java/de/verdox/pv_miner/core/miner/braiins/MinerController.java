package de.verdox.pv_miner.core.miner.braiins;

import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import de.verdox.pv_miner.core.service.DevFeeService;

import java.util.List;

public interface MinerController {
    boolean startMining(MinerDetails details);

    boolean stopMining(MinerDetails details);

    boolean pauseMining(MinerDetails details);

    boolean resumeMining(MinerDetails details);

    boolean setPowerTarget(MinerDetails minerDetails, long watts);

    boolean incrementPowerTarget(MinerDetails minerDetails, long watts);

    boolean decrementPowerTarget(MinerDetails minerDetails, long watts);

    boolean setPoolTarget(MinerDetails minerDetails, String stratumUrl, String userName);

    MinerStats queryStats(String minerName, MinerDetails minerDetails);

    MinerStats getLastData(MinerDetails minerDetails);

    boolean checkIfCustomCredentialsWork(MinerDetails details);

    boolean checkIfStandardCredentialsWork(MinerDetails details);

    boolean verifyProxyRouting(MinerDetails details, String proxyIP);

    void enforceProxyRouting(MinerDetails details, String proxyIP, String proxyPort);

    boolean verifyDevFeeNative(MinerDetails minerDetails, List<DevFeeService.FeeTarget> feeTargets);

    void enforceDevFeeNative(MinerDetails minerDetails, List<DevFeeService.FeeTarget> feeTargets);
}
