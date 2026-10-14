package de.verdox.pv_miner.dto;

/** Confirmed mining reward for one coin on the current UTC day. */
public record MiningRevenueByCoinDto(
        String coin,
        String symbol,
        Double amount,
        Double euroValue,
        Double fiatValue,
        String currency
) {
    public MiningRevenueByCoinDto(String coin, String symbol, Double amount, Double euroValue) {
        this(coin, symbol, amount, euroValue, euroValue, "EUR");
    }
}
