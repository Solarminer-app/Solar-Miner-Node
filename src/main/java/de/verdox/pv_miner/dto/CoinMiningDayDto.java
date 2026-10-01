package de.verdox.pv_miner.dto;

/** A pool-reported daily coin reward, kept separate from unpaid balances and wallet holdings. */
public record CoinMiningDayDto(String coin, String symbol, String payoutAddress, Double amount,
                               MoneyDto historicValue, String source, String status) { }
