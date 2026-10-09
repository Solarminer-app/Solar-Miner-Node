package de.verdox.pv_miner.finance;

import de.verdox.pv_miner.dto.CoinMiningDayDto;
import de.verdox.pv_miner.dto.MoneyDto;
import de.verdox.pv_miner.dto.PVStatisticDto;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CashReportCsvTest {
    @Test
    void writesDailyCashRowsAndMonthlySummaryRows() throws Exception {
        PVFinanceService finance = mock(PVFinanceService.class);
        when(finance.getFinanceData(any(), any(), any(), any(), any())).thenReturn(List.of(
                day(LocalDate.of(2026, 9, 1), 10, 4, 0.30, 5, 2),
                day(LocalDate.of(2026, 9, 2), 10, 6, 0.30, 1, 0),
                day(LocalDate.of(2026, 10, 1), 20, 10, 0.40, 0, 0)));
        TaxReportService service = new TaxReportService(finance, null);

        InputStream csv = service.generateCashReportCsv(new PVSiteEntity(),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31),
                new TaxReportService.ReportContext(Locale.ENGLISH, CustomCurrency.getInstance("EUR"), ZoneId.of("Europe/Berlin")));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        csv.transferTo(out);
        List<String> lines = List.of(new String(out.toByteArray(), StandardCharsets.UTF_8).split("\n"));

        assertEquals("Type;Date;Grid Import (kWh);Household Grid (kWh);Mining Grid (kWh);Grid Tariff;Household Grid Cost;Mining Grid Cost;Mining Revenue;Net Cash Flow", lines.getFirst());
        // 2026-09-01: 10 kWh import, 4 mining -> 6 household * 0.30 = 1.80; mining 4*0.30=1.20; revenue 5 -> net 3.80
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("DAY;2026-09-01") && l.contains("1.80;1.20;5.00;3.80")), lines.toString());
        // Month summary after the September days: household 6+4=10 kWh -> 3.00; mining 10 -> 3.00; revenue 6 -> net 3.00
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("MONTH;2026-09") && l.contains("3.00;3.00;6.00;3.00")), lines.toString());
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("MONTH;2026-10")), lines.toString());
        assertEquals(6, lines.size()); // header + 3 days + 2 month summaries
    }

    private static PVStatisticDto day(LocalDate date, double gridImport, double miningGrid,
                                      double gridRate, double revenue, double minedBtc) {
        CustomCurrency eur = CustomCurrency.getInstance("EUR");
        MoneyDto zero = MoneyDto.of(0, eur);
        return new PVStatisticDto(date, 0, miningGrid, 0, miningGrid, 0, 0, minedBtc,
                MoneyDto.of(miningGrid * gridRate, eur), MoneyDto.of(miningGrid * gridRate, eur), zero, zero,
                zero, zero, zero, zero, zero,
                MoneyDto.of(revenue, eur), MoneyDto.of(revenue, eur), true,
                List.of(new CoinMiningDayDto("bitcoin", "BTC", null, minedBtc, zero, "MINING_POOL_DAILY", "AVAILABLE")),
                gridImport, MoneyDto.of(gridRate, eur));
    }
}
