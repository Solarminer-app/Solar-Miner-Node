package de.verdox.currencyrates.currencyrates.service;

import java.math.BigDecimal;

public abstract class BlockchainMiningDataFetcher {

    public abstract long getMiningDifficulty();

    public abstract double getGlobalHashRateInThs();

    public abstract double getPriceInDollar();

    public abstract int getBlockSubsidy();

    public abstract int getAverageTxPrice24h();

    public abstract BigDecimal getCommaSeparator();
}
