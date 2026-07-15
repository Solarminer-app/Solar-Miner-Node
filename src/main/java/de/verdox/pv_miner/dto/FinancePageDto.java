package de.verdox.pv_miner.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Canonical finance page contract shared by React and the legacy Vaadin view.
 */
public record FinancePageDto(
        LocalDate setupDate,
        LocalDate from,
        LocalDate to,
        FinanceKpiDto filteredKpis,
        FinanceKpiDto allTimeKpis,
        FinanceInsightsDto periodInsights,
        FinanceInsightsDto allTimeInsights,
        List<PVStatisticDto> days,
        List<BitcoinSaleDto> sales
) {
    public record BitcoinSaleDto(
            LocalDate saleDate,
            double amountBtc,
            MoneyDto fiatValue
    ) {
    }
}
