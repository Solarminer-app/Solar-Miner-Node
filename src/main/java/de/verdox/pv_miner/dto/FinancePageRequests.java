package de.verdox.pv_miner.dto;

import java.time.LocalDate;

public final class FinancePageRequests {
    private FinancePageRequests() {
    }

    public record BitcoinSaleRequest(
            LocalDate saleDate,
            double amountBtc,
            double fiatAmount,
            String currency
    ) {
    }
}
