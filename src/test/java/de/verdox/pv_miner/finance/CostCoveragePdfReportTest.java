package de.verdox.pv_miner.finance;

import de.verdox.pv_miner.dto.CoinMiningDayDto;
import de.verdox.pv_miner.dto.MoneyDto;
import de.verdox.pv_miner.dto.PVStatisticDto;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CostCoveragePdfReportTest {
    private static final LocalDate DATE = LocalDate.of(2026, 9, 1);
    private static final CustomCurrency EUR = CustomCurrency.getInstance("EUR");

    @Test
    void calculatesCoinsForPayableAndEconomicEnergyCostSeparately() {
        PVStatisticDto day = day(DATE, 0.001, 10, 3, 2, true);

        assertEquals("0.0003 BTC", TaxReportService.coverageCoins(List.of(day), false, true));
        assertEquals("0.0005 BTC", TaxReportService.coverageCoins(List.of(day), true, true));
    }

    @Test
    void rendersLocalizedDailyAndMonthlyPlanningRows() {
        TaxReportService service = new TaxReportService(null, null);
        TaxReportService.ReportContext context = new TaxReportService.ReportContext(
                Locale.GERMAN, EUR, ZoneId.of("Europe/Berlin"));

        String html = service.generateCostCoverageHtml(
                List.of(day(DATE, 0.001, 10, 3, 2, true)), DATE, DATE, context);

        assertTrue(html.contains("Stromkosten- und Coin-Deckungsreport"));
        assertTrue(html.contains("Coins für Stromrechnung"));
        assertTrue(html.contains("0.0003 BTC"));
        assertTrue(html.contains("0.0005 BTC"));
        assertTrue(html.contains("Summe September 2026"));
        assertTrue(html.contains("Wirtschaftliche Sicht"));
        assertTrue(html.contains("Tageswert-Schätzung"));
        assertTrue(html.contains("Batterieenergie wird ausgewiesen"));
    }

    @Test
    void putsMonthlyAndYearlyTotalsBeforeMonthDetailsWithPageBreaksAndRepeatedHeaders() {
        TaxReportService service = new TaxReportService(null, null);
        TaxReportService.ReportContext context = new TaxReportService.ReportContext(
                Locale.ENGLISH, EUR, ZoneId.of("Europe/Berlin"));
        LocalDate nextMonth = DATE.plusMonths(1);

        String html = service.generateCostCoverageHtml(List.of(
                day(DATE, 0.001, 10, 3, 2, true),
                day(nextMonth, 0.002, 20, 4, 3, true)), DATE, nextMonth, context);

        assertTrue(html.indexOf("Monthly and yearly totals") < html.indexOf("Daily details by month"));
        assertTrue(html.indexOf("Year total 2026") < html.indexOf("<h3>September 2026</h3>"));
        assertTrue(html.indexOf("Monthly and yearly totals") < html.indexOf("<h3>October 2026</h3>"));
        assertTrue(html.contains(".month-details { page-break-before: always; break-before: page; }"));
        assertTrue(html.contains("thead { display: table-header-group; }"));
    }

    @Test
    void generatesARealPdfDocument() throws Exception {
        PVFinanceService finance = mock(PVFinanceService.class);
        when(finance.getFinanceData(any(), any(), any(), any(), any()))
                .thenReturn(List.of(day(DATE, 0.001, 10, 3, 2, true)));
        TaxReportService service = new TaxReportService(finance, null);
        TaxReportService.ReportContext context = new TaxReportService.ReportContext(
                Locale.ENGLISH, EUR, ZoneId.of("Europe/Berlin"));

        try (InputStream pdf = service.generateCostCoveragePdfReport(new PVSiteEntity(), DATE, DATE, context)) {
            byte[] signature = pdf.readNBytes(4);
            assertEquals("%PDF", new String(signature, StandardCharsets.US_ASCII));
        }
    }

    @Test
    void marksCostAsUncoverableWhenNoValuedRewardExists() {
        PVStatisticDto day = day(DATE, 0, 0, 3, 0, false);

        assertEquals("not fully coverable", TaxReportService.coverageCoins(List.of(day), false, false));
    }

    private static PVStatisticDto day(LocalDate date, double coinAmount, double revenue,
                                      double gridCost, double opportunityCost, boolean complete) {
        MoneyDto zero = MoneyDto.of(0, EUR);
        MoneyDto historicCoinValue = revenue > 0 ? MoneyDto.of(revenue, EUR) : null;
        return new PVStatisticDto(date, 0, 10, 6, 4, 0, 0, coinAmount,
                MoneyDto.of(gridCost + opportunityCost, EUR), MoneyDto.of(gridCost, EUR),
                MoneyDto.of(opportunityCost, EUR), zero, zero, zero, zero, zero, zero,
                MoneyDto.of(revenue, EUR), MoneyDto.of(revenue, EUR), complete,
                List.of(new CoinMiningDayDto("bitcoin", "BTC", null, coinAmount, historicCoinValue,
                        "MINING_POOL_DAILY", complete ? "AVAILABLE" : "PRICE_UNAVAILABLE")),
                4, MoneyDto.of(0.75, EUR));
    }
}
