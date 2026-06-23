package de.verdox.pv_miner_extensions.braiins.pool;

import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.globalconstants.GlobalConstantsService;
import de.verdox.pv_miner.miningpool.MiningPoolData;
import de.verdox.pv_miner.miningpool.MiningPoolEntity;
import jakarta.persistence.Entity;

import java.util.List;
import java.util.Map;

@Entity
public class BraiinsPoolEntity extends MiningPoolEntity<BraiinsPoolEntity.BraiinsPoolData> {

    private static final BraiinsFFPSPayout brainsPayoutEstimator = new BraiinsFFPSPayout();

    private String authToken;

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    @Override
    public String getUrlIdentifier() {
        return "braiins.com";
    }

    @Override
    public String getStratumV1Url() {
        return "stratum+tcp://stratum.braiins.com:3333";
    }

    public record BraiinsPoolData(String poolUserName, double currentPoolBalance, double todayReward, double estimatedRewardToday,
                                  double payPerShare, List<WorkerData> workerData,
                                  Map<Long, Double> paidRewardsSatoshis) implements MiningPoolData {
        @Override
        public boolean containsWorkerWithName(String workerName) {
            return workerData.stream().anyMatch(workerData1 -> workerData1.workerName().equals(workerName));
        }

        @Override
        public double calculateSatoshiRewardToday(String workerName) {
            if (todayReward != 0) {
                System.out.println("RETURN TODAY REWARD: "+todayReward);
                return todayReward;
            }
            GlobalConstantsService globalConstantsService = SpringContextHelper.getBean(GlobalConstantsService.class);

            double generatedShares = workerData().stream()
                    .filter(workerData1 -> workerData1.workerName().equals(workerName))
                    .mapToDouble(BraiinsPoolData.WorkerData::generatedSharesToday)
                    .sum();

            float braiinsPoolFee = 0.02f;

            return brainsPayoutEstimator.calculateRewardForDay(
                    globalConstantsService.getTodayMiningDifficulty(),
                    globalConstantsService.getTodayBlockSubsidy(),
                    globalConstantsService.getTodayAverageTxPrice24h(),
                    generatedShares,
                    braiinsPoolFee
            );
        }

        @Override
        public String getDefaultWorkerName() {
            return "Verdox.";
        }

        @Override
        public double calculateSatoshiRewardToday() {
/*            GlobalConstantsService globalConstantsService = SpringContextHelper.getBean(GlobalConstantsService.class);

            double generatedShares = workerData().stream()
                    .mapToDouble(BraiinsPoolData.WorkerData::generatedSharesToday)
                    .sum();

            float braiinsPoolFee = 0.02f;

            return brainsPayoutEstimator.calculateRewardForDay(
                    globalConstantsService.getTodayMiningDifficulty(),
                    globalConstantsService.getTodayBlockSubsidy(),
                    globalConstantsService.getTodayAverageTxPrice24h(),
                    generatedShares,
                    braiinsPoolFee
            );*/
            return estimatedRewardToday;
        }

        public record WorkerData(String workerName, double generatedSharesToday) {
        }

        @Override
        public Map<Long, Double> getHistoricalPaidRewardsSatoshis() {
            return paidRewardsSatoshis;
        }
    }
}
