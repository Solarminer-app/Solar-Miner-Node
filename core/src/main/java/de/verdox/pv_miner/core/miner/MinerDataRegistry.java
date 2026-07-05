package de.verdox.pv_miner.core.miner;

import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A registry where we map the latest miner details to the fetched miner identity
 */
@Service
public class MinerDataRegistry {
    private static final Map<MinerDetails, CachedMinerData> cache = new ConcurrentHashMap<>();

    public void record(MinerDetails details, MinerStats minerStats) {
        cache.put(details, new CachedMinerData(minerStats.minerIdentity(), minerStats.powerTargetWatts(), minerStats.minPowerTarget(), minerStats.defaultPowerTarget(), minerStats.maxPowerTarget()));
    }

    public CachedMinerData getIdentity(MinerDetails details) {
        return cache.getOrDefault(details, new CachedMinerData(new MinerStats.MinerIdentity("-", "-", "Offline"), 0, 0, 0,0));
    }

    public record CachedMinerData(
            MinerStats.MinerIdentity minerIdentity,
            long currentPowerTargetWatts,
            long minPowerTarget,
            long defaultPowerTarget,
            long maxPowerTarget
    ) {}
}
