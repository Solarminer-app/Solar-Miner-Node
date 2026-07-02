package de.verdox.pv_miner.core.miner.braiins.graphql;

import de.verdox.pv_miner.core.miner.dto.MinerStats;
import de.verdox.pv_miner.core.miner.dto.Pools;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;


record GraphQLMinerStatus(
        String model,
        String macAddress,
        String serialNumber,
        long powerTargetWatts,
        long approximatePowerUsageWatts,
        double terahashPerSecond,
        double temperatureCelsius,
        MinerStats.MinerStatus miningStatus,
        List<Pools> pools
) {
    public MinerStats toMinerStats(String ipv4) {
        return new MinerStats(
                new MinerStats.MinerIdentity(UUID.nameUUIDFromBytes(serialNumber.getBytes(StandardCharsets.UTF_8)).toString(), macAddress, model),
                ipv4,
                miningStatus,
                powerTargetWatts,
                0,
                0,
                approximatePowerUsageWatts,
                terahashPerSecond,
                temperatureCelsius,
                pools,
                List.of()
        );
    }
}
