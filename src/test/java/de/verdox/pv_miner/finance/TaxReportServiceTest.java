package de.verdox.pv_miner.finance;

import de.verdox.pv_miner.dto.MoneyDto;
import de.verdox.pv_miner.dto.PVStatisticDto;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaxReportServiceTest {
    @Test
    void showsSmallBitcoinRewardsInDailyAndSummaryRows() {
        PVStatisticDto first = day(LocalDate.of(2026, 9, 1), 0.00000037);
        PVStatisticDto second = day(LocalDate.of(2026, 9, 2), 0.00000042);

        assertEquals("0.00000037 BTC", TaxReportService.coinRewards(first));
        assertEquals("0.00000079 BTC", TaxReportService.coinTotals(List.of(first, second)));
    }

    private static PVStatisticDto day(LocalDate date, double minedBtc) {
        MoneyDto zero = MoneyDto.of(0, CustomCurrency.getInstance("EUR"));
        return new PVStatisticDto(date, 0, 0, 0, 0, 0, 0, minedBtc,
                zero, zero, zero, zero, zero, zero, zero, zero, zero);
    }
}
