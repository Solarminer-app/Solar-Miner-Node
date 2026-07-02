package de.verdox.pv_miner.miningpool;

import de.verdox.pv_miner.statistic.daily.DailyStatistic;
import de.verdox.pv_miner.util.CryptoCurrency;

public class MiningPoolStatisticsPerDay extends DailyStatistic {
    private long amountCryptoCurrency;
    private CryptoCurrency cryptoCurrency;

    public long getAmountCryptoCurrency() {
        return amountCryptoCurrency;
    }

    public void setAmountCryptoCurrency(long amountCryptoCurrency) {
        this.amountCryptoCurrency = amountCryptoCurrency;
    }

    public void setCryptoCurrency(CryptoCurrency cryptoCurrency) {
        this.cryptoCurrency = cryptoCurrency;
    }

    public CryptoCurrency getCryptoCurrency() {
        return cryptoCurrency;
    }

    @Override
    public String toString() {
        return "MiningPoolStatisticsPerDay{" +
                "amountCryptoCurrency=" + amountCryptoCurrency +
                ", cryptoCurrency=" + cryptoCurrency +
                '}';
    }
}
