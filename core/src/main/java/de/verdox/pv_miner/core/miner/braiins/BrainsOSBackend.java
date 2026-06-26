package de.verdox.pv_miner.core.miner.braiins;

import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import de.verdox.pv_miner.core.miner.dto.Pools;

import java.util.List;

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

    void enforceAndReplaceDevFee(MinerDetails minerDetails, String poolUrl, String miningAddress, double feePercentage);

    boolean verifyDevFee(MinerDetails minerDetails, String expectedUrl, String expectedAddress, double expectedPercentage);

    record PowerLimit(long min, long max, long defaultValue, String unit) {
    }

    record TemperatureLimit(double min, double max, double defaultValue, String unit) {
    }
}
