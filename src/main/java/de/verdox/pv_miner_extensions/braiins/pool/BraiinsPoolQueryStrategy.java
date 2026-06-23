package de.verdox.pv_miner_extensions.braiins.pool;

import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.globalconstants.GlobalConstantsService;
import de.verdox.pv_miner.miningpool.MiningPoolQueryStrategy;
import de.verdox.pv_miner.util.CryptoCurrency;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BraiinsPoolQueryStrategy implements MiningPoolQueryStrategy<BraiinsPoolEntity.BraiinsPoolData, BraiinsPoolEntity> {
    private static final Logger LOGGER = Logger.getLogger(BraiinsPoolQueryStrategy.class.getName());

    private final Map<UUID, BraiinsPoolEntity.BraiinsPoolData> cachedResults = new HashMap<>();
    private final Map<UUID, Long> lastQueryTimestamp = new HashMap<>();

    @Override
    public BraiinsPoolEntity.BraiinsPoolData query(EntityQueryService entityQueryService, BraiinsPoolEntity entity) throws Throwable {
        if (lastQueryTimestamp.containsKey(entity.getId()) && System.currentTimeMillis() - lastQueryTimestamp.get(entity.getId()) < 1000 * 60) {
            if (cachedResults.containsKey(entity.getId())) {
                return cachedResults.get(entity.getId());
            }
            return null;
        }
        GlobalConstantsService globalConstantsService = SpringContextHelper.getBean(GlobalConstantsService.class);
        try {
            var setupDate = entity.getParentEntity().getSetupDate();
            BrainsPoolDTOs.BraiinsProfileData profileData = BraiinsPoolAPIClient.getProfileData(entity.getAuthToken(), CryptoCurrency.BITCOIN);
            String fetchedUsername = profileData.username();
            double todayReward = profileData.todayReward();
            double currentBalance = profileData.currentBalance();
            double payPerShare = new BraiinsFFPSPayout().calculateRewardForDay(globalConstantsService.getTodayMiningDifficulty(), globalConstantsService.getTodayBlockSubsidy(), globalConstantsService.getTodayAverageTxPrice24h(), 1, 0);

            var workers = BraiinsPoolAPIClient.getWorkerData(entity.getAuthToken(), CryptoCurrency.BITCOIN);
            var dailyRewards = BraiinsPoolAPIClient.getDailyRewards(entity.getAuthToken(), CryptoCurrency.BITCOIN, setupDate, LocalDate.now());
            Map<Long, Double> rewards = new HashMap<>();
            for (BraiinsPoolAPIClient.DailyReward dailyReward : dailyRewards) {
                if (dailyReward.total_reward() == 0) {
                    continue;
                }
                long calcDate = dailyReward.calculation_date() * 1000;
                rewards.put(calcDate, dailyReward.total_reward() * Math.pow(10, 8));
            }
            BraiinsPoolEntity.BraiinsPoolData miningPoolData = new BraiinsPoolEntity.BraiinsPoolData(fetchedUsername, todayReward, currentBalance, payPerShare, workers.entrySet().stream().map(stringWorkerDataEntry -> new BraiinsPoolEntity.BraiinsPoolData.WorkerData(stringWorkerDataEntry.getKey(), stringWorkerDataEntry.getValue().shares_24h())).toList(), rewards);

            LOGGER.info("Received "+rewards.size()+" payout data from braiins pool");
            cachedResults.put(entity.getId(), miningPoolData);
            return miningPoolData;
        }
        catch (Throwable throwable) {
            LOGGER.log(Level.SEVERE, throwable.getMessage(), throwable);
        }
        finally {
            lastQueryTimestamp.put(entity.getId(), System.currentTimeMillis());
        }
        return null;
    }

    @Override
    public void ping(BraiinsPoolEntity entity) throws Throwable {
        String fetchedUsername = BraiinsPoolAPIClient.getUsername(entity.getAuthToken(), CryptoCurrency.BITCOIN);

        if (fetchedUsername != null && !fetchedUsername.isBlank()) {

        } else {
            throw new Exception("Ping successful but could not find the brains username");
        }
    }
}