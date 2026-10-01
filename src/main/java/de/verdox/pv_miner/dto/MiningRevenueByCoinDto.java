package de.verdox.pv_miner.dto;

/** Confirmed mining reward for one coin on the current UTC day. */
public record MiningRevenueByCoinDto(
        String coin,
        String symbol,
        double amount,
        Double euroValue
) {
}
