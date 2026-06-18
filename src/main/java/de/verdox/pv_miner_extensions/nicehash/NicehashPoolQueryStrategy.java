package de.verdox.pv_miner_extensions.nicehash;

import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.miningpool.MiningPoolQueryStrategy;

import java.util.*;

public class NicehashPoolQueryStrategy implements MiningPoolQueryStrategy<NiceHashPoolEntity.NicehashWorkerStats, NiceHashPoolEntity> {

    private final Map<UUID, NiceHashPoolEntity.NicehashWorkerStats> cachedResults = new HashMap<>();
    private final Map<UUID, Long> lastQueryTimestamp = new HashMap<>();

    @Override
    public NiceHashPoolEntity.NicehashWorkerStats query(EntityQueryService entityQueryService, NiceHashPoolEntity entity) throws Throwable {
        if (lastQueryTimestamp.containsKey(entity.getId()) && System.currentTimeMillis() - lastQueryTimestamp.get(entity.getId()) < 1000 * 3600) {
            if (cachedResults.containsKey(entity.getId())) {
                return cachedResults.get(entity.getId());
            }
            return null;
        }

        try {
            List<NiceHashPoolEntity.UnpaidAmount> unpaidAmounts = new LinkedList<>();
            for (String workerName : NicehashAPIClient.queryWorkerNames(entity.getOrgId(), entity.getApiKey(), entity.getSecret())) {
                unpaidAmounts.add(NicehashAPIClient.queryUnpaid(workerName, entity.getOrgId(), entity.getApiKey(), entity.getSecret()));
            }

            List<NiceHashPoolEntity.Payout> payouts = NicehashAPIClient.queryPayouts(entity.getOrgId(), entity.getApiKey(), entity.getSecret());

            var miningPoolData = new NiceHashPoolEntity.NicehashWorkerStats(NicehashAPIClient.querySatoshiPayoutMin(entity.getOrgId(), entity.getApiKey(), entity.getSecret()), unpaidAmounts, payouts);

            cachedResults.put(entity.getId(), miningPoolData);
            lastQueryTimestamp.put(entity.getId(), System.currentTimeMillis());
            return miningPoolData;
        } finally {
            lastQueryTimestamp.put(entity.getId(), System.currentTimeMillis());
        }
    }

    @Override
    public void ping(NiceHashPoolEntity entity) throws Throwable {
        NicehashAPIClient.ping(entity.getOrgId(), entity.getApiKey(), entity.getSecret());
    }
}
