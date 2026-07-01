package de.verdox.pv_miner.miner;

import de.verdox.pv_miner.statistic.daily.DailyStatistic;

public class MinerStatisticsPerDay extends DailyStatistic {
    private double totalEnergyUsedInKwh;

    public double getTotalEnergyUsedInKwh() {
        return totalEnergyUsedInKwh;
    }

    public void setTotalEnergyUsedInKwh(double totalEnergyUsedInKwh) {
        this.totalEnergyUsedInKwh = totalEnergyUsedInKwh;
    }

    @Override
    public String toString() {
        return "MinerStatisticsPerDay{" +
                "totalEnergyUsedInKwh=" + totalEnergyUsedInKwh +
                '}';
    }
}
