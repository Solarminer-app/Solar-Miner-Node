package de.verdox.pv_miner.miner.data;

import de.verdox.pv_miner.influx.QueryResult;

import java.util.List;
import java.util.stream.Collectors;

public record MinerStats(
        MinerStats.MinerIdentity minerIdentity, String name, MinerStatus miningStatus,
        long powerTargetWatts,
        long minPowerTarget,
        long defaultPowerTarget,
        long maxPowerTarget,
        long approximatedPowerUsageWatts,
        double terahashPerSecond,
        double temperatureCelsius,
        List<Pools> pools,
        List<Worker> workers
) implements QueryResult {

    /** Power attributable to mining; idle/paused device draw is not mining power. */
    public long miningPowerWatts() {
        return miningStatus == MinerStatus.MINING ? approximatedPowerUsageWatts : 0;
    }

    public MinerStats withName(String name) {
        return new MinerStats(minerIdentity, name, miningStatus, powerTargetWatts, minPowerTarget, defaultPowerTarget, maxPowerTarget, approximatedPowerUsageWatts, terahashPerSecond, temperatureCelsius, pools, workers);
    }

    public static final MinerStats DEFAULT = new MinerStats(
            new MinerIdentity("", "", ""),
            "-",
            MinerStatus.ERROR,
            0, 0,0, 0, 0, 0.0, 0.0,
            List.of(),
            List.of()
    );

    public boolean minesOnlyOneAlgorithm() {
        return workers.isEmpty() || workers.stream().collect(Collectors.groupingBy(worker -> worker.currentAlgorithm)).size() <= 1;
    }

    public String getSingleAlgorithmMined() {
        return workers.stream().map(worker -> worker.currentAlgorithm).findAny().orElse("-");
    }

    public record Worker(
            MinerStatus miningStatus,
            String workerDisplayName,
            String currentAlgorithm,
            double terahashPerSecond,
            double temperatureCelsius,
            long powerTargetWatts,
            long minPowerTarget,
            long defaultPowerTarget,
            long maxPowerTarget,
            long approximatedPowerUsageWatts,
            List<Pools> pools,
            String hardwareType,
            String hardwareModel,
            String deviceId,
            Long acceptedShares,
            Long rejectedShares
    ) {
        public long miningPowerWatts() {
            return miningStatus == MinerStatus.MINING ? approximatedPowerUsageWatts : 0;
        }

        /**
         * Compatibility constructor for older core responses and miner integrations that have
         * no accepted/rejected share counters.
         */
        public Worker(
                MinerStatus miningStatus, String workerDisplayName, String currentAlgorithm,
                double terahashPerSecond, double temperatureCelsius,
                long powerTargetWatts, long minPowerTarget, long defaultPowerTarget, long maxPowerTarget,
                long approximatedPowerUsageWatts, List<Pools> pools,
                String hardwareType, String hardwareModel, String deviceId
        ) {
            this(miningStatus, workerDisplayName, currentAlgorithm, terahashPerSecond, temperatureCelsius,
                    powerTargetWatts, minPowerTarget, defaultPowerTarget, maxPowerTarget,
                    approximatedPowerUsageWatts, pools, hardwareType, hardwareModel, deviceId, null, null);
        }
    }

    public enum MinerStatus {
        MINING,
        STOPPED,
        PAUSED,
        ERROR
    }

    public record MinerIdentity(String minerUID, String macAddress, String minerModel) {

    }
}
