package de.verdox.pv_miner.finance;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import de.verdox.pv_miner.dto.FinanceKpiDto;
import de.verdox.pv_miner.dto.PVStatisticDto;
import de.verdox.pv_miner.dto.CoinMiningDayDto;
import de.verdox.pv_miner.globalconstants.GlobalConstantsService;
import de.verdox.pv_miner.pvsite.BitcoinSale;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.util.FormatUtil;
import de.verdox.pv_miner.util.Money;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;

@Service
public class TaxReportService {

    public record ReportContext(Locale locale, CustomCurrency currency, ZoneId zoneId) {
        public Locale getLocale() {
            return locale;
        }

        public CustomCurrency getCurrency() {
            return currency;
        }

        public ZoneId getZoneId() {
            return zoneId;
        }
    }

    private final PVFinanceService pvFinanceService;
    private final GlobalConstantsService globalConstantsService;

    public TaxReportService(PVFinanceService pvFinanceService, GlobalConstantsService globalConstantsService) {
        this.pvFinanceService = pvFinanceService;
        this.globalConstantsService = globalConstantsService;
    }

    public InputStream generateMiningPdfReport(PVSiteEntity pvSite, LocalDate from, LocalDate to, ReportContext context) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            List<PVStatisticDto> statistics = pvFinanceService.getFinanceData(pvSite, from, to, context.getZoneId(), context.getCurrency());
            FinanceKpiDto kpis = pvFinanceService.calculateKPIs(pvSite, statistics, context.getCurrency(), context.getZoneId());

            String htmlContent = generateMiningHtml(statistics, kpis, context);
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(htmlContent, "");
            builder.toStream(os);
            builder.run();
            return new ByteArrayInputStream(os.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return generateErrorPdf(e);
        }
    }

    public InputStream generatePvPdfReport(PVSiteEntity pvSite, LocalDate from, LocalDate to, ReportContext context) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            List<PVStatisticDto> statistics = pvFinanceService.getFinanceData(pvSite, from, to, context.getZoneId(), context.getCurrency());
            FinanceKpiDto kpis = pvFinanceService.calculateKPIs(pvSite, statistics, context.getCurrency(), context.getZoneId());

            String htmlContent = generatePvHtml(statistics, kpis, context);
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(htmlContent, "");
            builder.toStream(os);
            builder.run();
            return new ByteArrayInputStream(os.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return generateErrorPdf(e);
        }
    }

    public InputStream generateSalesPdfReport(PVSiteEntity pvSite, ReportContext context) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            LocalDate setupDate = pvSite.getSetupDate() != null ? pvSite.getSetupDate() : LocalDate.now(context.getZoneId()).minusYears(10);
            List<PVStatisticDto> allTimeStats = pvFinanceService.getFinanceData(pvSite, setupDate, LocalDate.now(context.getZoneId()), context.getZoneId(), context.getCurrency());

            List<BitcoinSale> sortedSales = new ArrayList<>(pvSite.getBitcoinSales());
            sortedSales.sort(Comparator.comparing(BitcoinSale::getSaleDate));

            List<SaleReportDto> processedSales = calculateFifoSales(allTimeStats, sortedSales, context.getCurrency());

            String htmlContent = generateSalesHtml(processedSales, context);
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(htmlContent, "");
            builder.toStream(os);
            builder.run();
            return new ByteArrayInputStream(os.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return generateErrorPdf(e);
        }
    }

    public InputStream generateCsvReport(PVSiteEntity pvSite, LocalDate from, LocalDate to, ReportContext context) {
        try {
            List<PVStatisticDto> statistics = pvFinanceService.getFinanceData(pvSite, from, to, context.getZoneId(), context.getCurrency());
            StringBuilder sb = new StringBuilder();
            sb.append("Date;Total PV (kWh);Household Usage (kWh);Grid Export (kWh);Household Savings;Feed-In Revenue;BTC Reward;XMR Reward;PRL Reward;Mining Revenue (Historical);Revenue Complete;Mining Cost;Net Profit\n");

            for (PVStatisticDto stat : statistics) {
                double netProfitRaw = stat.miningRevenueHistoric().getRawMoneyAmount() - stat.miningCost().getRawMoneyAmount();
                Money netProfit = new Money(netProfitRaw, context.getCurrency());

                sb.append(stat.date()).append(";")
                        .append(FormatUtil.formatNumber(stat.totalPvProduction())).append(";")
                        .append(FormatUtil.formatNumber(stat.householdPvUsage())).append(";")
                        .append(FormatUtil.formatNumber(stat.exportedKwh())).append(";")
                        .append(stat.householdSavings().getRawMoneyAmount()).append(";")
                        .append(stat.feedInRevenue().getRawMoneyAmount()).append(";")
                        .append(rewardAmount(stat, "bitcoin")).append(";")
                        .append(rewardAmount(stat, "monero")).append(";")
                        .append(rewardAmount(stat, "pearl")).append(";")
                        .append(stat.miningRevenueHistoric().getRawMoneyAmount()).append(";")
                        .append(stat.miningRevenueComplete()).append(";")
                        .append(stat.miningCost().getRawMoneyAmount()).append(";")
                        .append(netProfit.getRawMoneyAmount()).append("\n");
            }
            return new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new ByteArrayInputStream(("Error in CSV Export: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Pure cash-flow report: what the grid actually costs per day (household vs.
     * mining split) against mining revenue at that day's coin prices, with a
     * summary block per calendar month. No PV self-consumption offsetting.
     */
    public InputStream generateCashReportCsv(PVSiteEntity pvSite, LocalDate from, LocalDate to, ReportContext context) {
        try {
            List<PVStatisticDto> statistics = new ArrayList<>(pvFinanceService.getFinanceData(pvSite, from, to, context.getZoneId(), context.getCurrency()));
            statistics.sort(Comparator.comparing(PVStatisticDto::date));
            StringBuilder sb = new StringBuilder();
            sb.append("Type;Date;Grid Import (kWh);Household Grid (kWh);Mining Grid (kWh);Grid Tariff;Household Grid Cost;Mining Grid Cost;Mining Revenue;Net Cash Flow\n");

            java.time.YearMonth currentMonth = null;
            MonthTotals totals = new MonthTotals();
            for (PVStatisticDto stat : statistics) {
                java.time.YearMonth month = java.time.YearMonth.from(stat.date());
                if (currentMonth != null && !month.equals(currentMonth)) {
                    appendMonthSummary(sb, currentMonth, totals);
                    totals = new MonthTotals();
                }
                currentMonth = month;

                double miningGridKwh = stat.miningGridUsage();
                double householdGridKwh = Math.max(0, stat.gridImportKwh() - miningGridKwh);
                double householdGridCost = householdGridKwh * stat.gridPricePerKwh().amount();
                double miningGridCost = stat.miningGridCost().amount();
                double revenue = stat.miningRevenueHistoric().amount();
                double netCash = revenue - miningGridCost;

                sb.append("DAY;").append(stat.date()).append(";")
                        .append(FormatUtil.formatNumber(stat.gridImportKwh())).append(";")
                        .append(FormatUtil.formatNumber(householdGridKwh)).append(";")
                        .append(FormatUtil.formatNumber(miningGridKwh)).append(";")
                        .append(stat.gridPricePerKwh().amount()).append(";")
                        .append(round(householdGridCost)).append(";")
                        .append(round(miningGridCost)).append(";")
                        .append(round(revenue)).append(";")
                        .append(round(netCash)).append("\n");

                totals.gridImport += stat.gridImportKwh();
                totals.householdGrid += householdGridKwh;
                totals.miningGrid += miningGridKwh;
                totals.householdCost += householdGridCost;
                totals.miningCost += miningGridCost;
                totals.revenue += revenue;
            }
            if (currentMonth != null) appendMonthSummary(sb, currentMonth, totals);
            return new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new ByteArrayInputStream(("Error in CSV Export: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Financial planning report that deliberately separates the payable grid
     * bill from the economic cost of using PV energy instead of exporting it.
     */
    public InputStream generateCostCoveragePdfReport(PVSiteEntity pvSite, LocalDate from, LocalDate to,
                                                      ReportContext context) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            List<PVStatisticDto> statistics = new ArrayList<>(pvFinanceService.getFinanceData(
                    pvSite, from, to, context.getZoneId(), context.getCurrency()));
            statistics.sort(Comparator.comparing(PVStatisticDto::date));

            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(generateCostCoverageHtml(statistics, from, to, context), "");
            builder.toStream(os);
            builder.run();
            return new ByteArrayInputStream(os.toByteArray());
        } catch (Exception exception) {
            return generateErrorPdf(exception, context);
        }
    }

    String generateCostCoverageHtml(List<PVStatisticDto> statistics, LocalDate from, LocalDate to,
                                    ReportContext context) {
        boolean german = "de".equalsIgnoreCase(context.getLocale().getLanguage());
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(german ? "dd.MM.yyyy" : "MM/dd/yyyy",
                context.getLocale());
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", context.getLocale());
        double totalRevenue = statistics.stream().mapToDouble(day -> day.miningRevenueHistoric().amount()).sum();
        double totalGridCost = statistics.stream().mapToDouble(day -> day.miningGridCost().amount()).sum();
        double totalOpportunityCost = statistics.stream().mapToDouble(day -> day.miningOpportunityCost().amount()).sum();
        boolean complete = statistics.stream().allMatch(PVStatisticDto::miningRevenueComplete);

        StringBuilder html = new StringBuilder();
        html.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<html><head>")
                .append(getBaseCss())
                .append("<style>@page { size: A4 landscape; margin: 12mm; } table { font-size: 8.5px; } th, td { padding: 5px; } .kpi-box-four { display:inline-block; width:23%; background:#f8f9fa; border:1px solid #e9ecef; border-radius:8px; padding:10px; margin-right:1%; box-sizing:border-box; vertical-align:top; } .explanation { background:#f8f9fa; border-left:3px solid #00b978; padding:8px 12px; margin:12px 0; font-size:10px; }</style>")
                .append("</head><body><div class='header'><h1>")
                .append(german ? "Solar Miner – Stromkosten- und Coin-Deckungsreport" : "Solar Miner – Electricity Cost and Coin Coverage Report")
                .append("</h1><p>")
                .append(german ? "Erstellt am: " : "Generated on: ")
                .append("<strong>").append(LocalDate.now(context.getZoneId()).format(dateFormatter)).append("</strong> | ")
                .append(german ? "Zeitraum: " : "Period: ")
                .append("<strong>").append(from.format(dateFormatter)).append(" – ").append(to.format(dateFormatter))
                .append("</strong></p></div>");

        appendCoverageKpi(html, german ? "Mining-Wert" : "Mining value", totalRevenue, context, null);
        appendCoverageKpi(html, german ? "Zu zahlender Netzstrom" : "Payable grid electricity", totalGridCost, context, null);
        appendCoverageKpi(html, german ? "Cash-Ergebnis" : "Cash result", totalRevenue - totalGridCost, context,
                totalRevenue - totalGridCost >= 0 ? "positive" : "negative");
        appendCoverageKpi(html, german ? "Wirtschaftliches Ergebnis" : "Economic result",
                totalRevenue - totalGridCost - totalOpportunityCost, context,
                totalRevenue - totalGridCost - totalOpportunityCost >= 0 ? "positive" : "negative");

        html.append("<div class='explanation'>")
                .append(german
                        ? "<strong>Cash-Sicht:</strong> Nur der während des Minings anteilig bezogene Netzstrom muss bezahlt werden. "
                        + "<strong>Wirtschaftliche Sicht:</strong> Zusätzlich wird die entgangene Einspeisevergütung des fürs Mining genutzten PV-Stroms ausgewiesen. "
                        + "Die Zuordnung erfolgt bei ausreichender Datenabdeckung aus zeitgleichen Minutenintervallen; Batterieentladung wird separat behandelt. Für ältere Daten werden Stundenintervalle und bei Datenlücken eine gekennzeichnete Tageswert-Schätzung verwendet. "
                        + "Batterieenergie wird ausgewiesen, aber ohne Herkunfts- und Verlustmodell weder als direkter Netzbezug noch als PV-Opportunitätskosten bepreist. "
                        + "Die Verkaufsempfehlung nimmt an, dass von allen bewerteten Tageserträgen derselbe Anteil verkauft wird. Sie ist eine Planungsgröße zum historischen Tageswert, keine tatsächlich erfasste Transaktion oder Steuerberatung."
                        : "<strong>Cash view:</strong> Only the share of grid electricity imported during mining produces a payable bill. "
                        + "<strong>Economic view:</strong> The forgone feed-in revenue of PV electricity used for mining is shown separately. "
                        + "With sufficient coverage, energy is allocated from time-aligned minute intervals and battery discharge is handled separately. Older data uses hourly intervals, with a labelled daily-value estimate for gaps. "
                        + "Battery energy is reported but, without an origin and loss model, is priced neither as direct grid import nor as PV opportunity cost. "
                        + "The sale recommendation assumes that the same share of every valued daily reward is sold. It is a planning estimate at the historical daily value, not a recorded transaction or tax advice.")
                .append("</div>");

        html.append("<table><thead><tr>")
                .append(th(german ? "Datum" : "Date"))
                .append(thRight(german ? "Mining-Energie" : "Mining energy"))
                .append(thRight(german ? "davon Netz" : "grid share"))
                .append(thRight(german ? "davon Batterie" : "battery share"))
                .append(thRight(german ? "Datenbasis" : "Data basis"))
                .append(thRight(german ? "Coin-Ertrag" : "Coin reward"))
                .append(thRight(german ? "Mining-Wert" : "Mining value"))
                .append(thRight(german ? "Netzkosten" : "Grid cost"))
                .append(thRight(german ? "Opportunitätskosten" : "Opportunity cost"))
                .append(thRight(german ? "Cash netto" : "Net cash"))
                .append(thRight(german ? "Coins für Stromrechnung" : "Coins for grid bill"))
                .append(thRight(german ? "Coins inkl. Opportunität" : "Coins incl. opportunity"))
                .append("</tr></thead><tbody>");

        Map<YearMonth, List<PVStatisticDto>> byMonth = statistics.stream().collect(Collectors.groupingBy(
                day -> YearMonth.from(day.date()), LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<YearMonth, List<PVStatisticDto>> monthEntry : byMonth.entrySet()) {
            for (PVStatisticDto day : monthEntry.getValue()) {
                appendCoverageRow(html, day.date().format(dateFormatter), List.of(day), context, german, false);
            }
            appendCoverageRow(html,
                    (german ? "Summe " : "Total ") + monthEntry.getKey().format(monthFormatter),
                    monthEntry.getValue(), context, german, true);
        }
        html.append("</tbody></table><div class='footer'>")
                .append(complete
                        ? (german ? "Alle im Zeitraum gemeldeten Mining-Erträge konnten bewertet werden." : "All mining rewards reported for the period could be valued.")
                        : (german ? "* Mindestens ein Pool-Ertrag oder historischer Coin-Kurs fehlt. Betroffene Coin-Deckungswerte sind unvollständig."
                        : "* At least one pool reward or historical coin price is missing. Affected coin coverage figures are incomplete."))
                .append("</div></body></html>");
        return html.toString();
    }

    private void appendCoverageRow(StringBuilder html, String label, List<PVStatisticDto> days,
                                   ReportContext context, boolean german, boolean summary) {
        double energy = days.stream().mapToDouble(PVStatisticDto::minerConsumption).sum();
        double gridKwh = days.stream().mapToDouble(PVStatisticDto::miningGridUsage).sum();
        double batteryKwh = days.stream().mapToDouble(PVStatisticDto::miningBatteryUsage).sum();
        double revenue = days.stream().mapToDouble(value -> value.miningRevenueHistoric().amount()).sum();
        double gridCost = days.stream().mapToDouble(value -> value.miningGridCost().amount()).sum();
        double opportunityCost = days.stream().mapToDouble(value -> value.miningOpportunityCost().amount()).sum();
        boolean complete = days.stream().allMatch(PVStatisticDto::miningRevenueComplete);
        String rowClass = summary ? " class='summary-row'" : "";
        html.append("<tr").append(rowClass).append("><td>").append(escape(label)).append("</td>")
                .append(tdRight(formatNumber(energy, context) + " kWh"))
                .append(tdRight(formatNumber(gridKwh, context) + " kWh"))
                .append(tdRight(formatNumber(batteryKwh, context) + " kWh"))
                .append(tdRight(escape(allocationQuality(days, german))))
                .append(tdRight(escape(coinTotals(days))))
                .append(tdRight(formatMoney(revenue, context) + incompleteMarker(complete)))
                .append(tdRight(formatMoney(gridCost, context)))
                .append(tdRight(formatMoney(opportunityCost, context)))
                .append(tdRightClass(formatMoney(revenue - gridCost, context), revenue - gridCost >= 0 ? "positive" : "negative"))
                .append(tdRight(escape(coverageCoinsWithShare(days, false, german)) + incompleteMarker(complete)))
                .append(tdRight(escape(coverageCoinsWithShare(days, true, german)) + incompleteMarker(complete)))
                .append("</tr>");
    }

    private static String allocationQuality(List<PVStatisticDto> days, boolean german) {
        boolean daily = days.stream().anyMatch(day -> MiningEnergyAllocationService.QUALITY_DAILY_ESTIMATE
                .equals(day.energyAllocationQuality()));
        if (daily) return german ? "Tageswert-Schätzung" : "Daily-value estimate";
        boolean hourly = days.stream().anyMatch(day -> MiningEnergyAllocationService.QUALITY_HOURLY
                .equals(day.energyAllocationQuality()));
        if (hourly) return german ? "Stundenintervalle" : "Hourly intervals";
        return german ? "Minutenintervalle" : "Minute intervals";
    }

    private static String coverageCoinsWithShare(List<PVStatisticDto> days, boolean includeOpportunityCost,
                                                  boolean german) {
        String coins = coverageCoins(days, includeOpportunityCost, german);
        double cost = days.stream().mapToDouble(day -> day.miningGridCost().amount()
                + (includeOpportunityCost ? day.miningOpportunityCost().amount() : 0)).sum();
        double revenue = days.stream().mapToDouble(day -> day.miningRevenueHistoric().amount()).sum();
        if (cost <= 0.000000001 || revenue <= 0.000000001) return coins;
        double share = cost / revenue * 100;
        String percent = String.format(german ? Locale.GERMAN : Locale.ENGLISH, "%.1f %%", share);
        if (share > 100.000001) {
            percent += german ? "; Ertrag reicht nicht" : "; reward insufficient";
        }
        return coins + " (" + percent + ")";
    }

    static String coverageCoins(List<PVStatisticDto> days, boolean includeOpportunityCost, boolean german) {
        Map<String, Double> amounts = new java.util.TreeMap<>();
        boolean uncovered = false;
        for (PVStatisticDto day : days) {
            double cost = day.miningGridCost().amount()
                    + (includeOpportunityCost ? day.miningOpportunityCost().amount() : 0);
            if (cost <= 0.000000001) continue;
            double revenue = day.miningRevenueHistoric().amount();
            if (revenue <= 0.000000001) {
                uncovered = true;
                continue;
            }
            double factor = cost / revenue;
            for (CoinMiningDayDto coin : day.miningCoins()) {
                if (coin.amount() == null || coin.amount() <= 0 || coin.historicValue() == null) continue;
                amounts.merge(coin.symbol(), coin.amount() * factor, Double::sum);
            }
        }
        String result = amounts.entrySet().stream()
                .map(entry -> formatCoinAmount(entry.getValue(), entry.getKey()) + " " + entry.getKey())
                .collect(Collectors.joining(" · "));
        if (uncovered) {
            String unavailable = german ? "nicht vollständig deckbar" : "not fully coverable";
            return result.isEmpty() ? unavailable : result + " · " + unavailable;
        }
        return result.isEmpty() ? "0" : result;
    }

    private static void appendCoverageKpi(StringBuilder html, String label, double value, ReportContext context,
                                          String valueClass) {
        html.append("<div class='kpi-box-four'><div class='kpi-title'>").append(escape(label))
                .append("</div><p class='kpi-value")
                .append(valueClass == null ? "" : " " + valueClass)
                .append("'>").append(formatMoney(value, context)).append("</p></div>");
    }

    private static String th(String value) { return "<th>" + escape(value) + "</th>"; }
    private static String thRight(String value) { return "<th class='text-right'>" + escape(value) + "</th>"; }
    private static String tdRight(String value) { return "<td class='text-right'>" + value + "</td>"; }
    private static String tdRightClass(String value, String cssClass) { return "<td class='text-right " + cssClass + "'>" + value + "</td>"; }
    private static String incompleteMarker(boolean complete) { return complete ? "" : " *"; }

    private static String formatMoney(double value, ReportContext context) {
        return formatNumber(value, context) + " " + context.getCurrency().getSymbol();
    }

    private static String formatNumber(double value, ReportContext context) {
        NumberFormat formatter = NumberFormat.getNumberInstance(context.getLocale());
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        return formatter.format(Double.isFinite(value) ? value : 0);
    }

    private static void appendMonthSummary(StringBuilder sb, java.time.YearMonth month, MonthTotals totals) {
        sb.append("MONTH;").append(month).append(";")
                .append(FormatUtil.formatNumber(totals.gridImport)).append(";")
                .append(FormatUtil.formatNumber(totals.householdGrid)).append(";")
                .append(FormatUtil.formatNumber(totals.miningGrid)).append(";;")
                .append(round(totals.householdCost)).append(";")
                .append(round(totals.miningCost)).append(";")
                .append(round(totals.revenue)).append(";")
                .append(round(totals.revenue - totals.miningCost)).append("\n");
    }

    private static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static final class MonthTotals {
        double gridImport, householdGrid, miningGrid, householdCost, miningCost, revenue;
    }

    private List<SaleReportDto> calculateFifoSales(List<PVStatisticDto> allTimeStats, List<BitcoinSale> sales, CustomCurrency currency) {
        List<MinedTranche> availableCoins = allTimeStats.stream()
                .filter(s -> s.minedBtc() > 0)
                .sorted(Comparator.comparing(PVStatisticDto::date))
                .map(s -> {
                    double fiatVal = calculateHistoricalValues(s, currency).historicalBtcFiatValue().getRawMoneyAmount();
                    return new MinedTranche(s.date(), s.minedBtc(), fiatVal);
                })
                .collect(Collectors.toList());

        List<SaleReportDto> processedSales = new ArrayList<>();

        for (BitcoinSale sale : sales) {
            double remainingToSell = sale.getAmountBtc();
            double saleRevenueFiat = sale.getFiatValue().getRawMoneyAmount();

            List<SaleDetailDto> tranchesUsed = new ArrayList<>();
            double totalAcquisitionCost = 0;
            double totalProfit = 0;

            for (MinedTranche tranche : availableCoins) {
                if (remainingToSell <= 0.000000001) break;
                if (tranche.remainingAmount <= 0) continue;

                double amountTakenFromTranche = Math.min(remainingToSell, tranche.remainingAmount);
                double trancheFraction = amountTakenFromTranche / tranche.originalAmount;

                double acquisitionCost = tranche.originalFiatValue * trancheFraction;
                double saleValueForTranche = saleRevenueFiat * (amountTakenFromTranche / sale.getAmountBtc());
                double profitForTranche = saleValueForTranche - acquisitionCost;

                long holdingDays = ChronoUnit.DAYS.between(tranche.date, sale.getSaleDate());

                tranchesUsed.add(new SaleDetailDto(
                        tranche.date,
                        amountTakenFromTranche,
                        new Money(acquisitionCost, currency),
                        new Money(saleValueForTranche, currency),
                        new Money(profitForTranche, currency),
                        holdingDays
                ));

                tranche.remainingAmount -= amountTakenFromTranche;
                remainingToSell -= amountTakenFromTranche;
                totalAcquisitionCost += acquisitionCost;
                totalProfit += profitForTranche;
            }

            if (remainingToSell > 0.00000001) {
                double saleValueForUnknown = saleRevenueFiat * (remainingToSell / sale.getAmountBtc());
                tranchesUsed.add(new SaleDetailDto(
                        sale.getSaleDate(),
                        remainingToSell,
                        new Money(0, currency),
                        new Money(saleValueForUnknown, currency),
                        new Money(saleValueForUnknown, currency),
                        0
                ));
                totalProfit += saleValueForUnknown;
            }

            processedSales.add(new SaleReportDto(
                    sale.getSaleDate(),
                    sale.getAmountBtc(),
                    sale.getFiatValue(),
                    new Money(totalAcquisitionCost, currency),
                    new Money(totalProfit, currency),
                    tranchesUsed
            ));
        }

        return processedSales;
    }

    private String generateSalesHtml(List<SaleReportDto> processedSales, ReportContext context) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", context.getLocale());
        String generationDate = LocalDate.now(context.getZoneId()).format(dateFormatter);

        StringBuilder html = new StringBuilder();
        html.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        html.append("<html><head>").append(getBaseCss()).append("</head><body>");

        html.append("<div class='header'>")
                .append("<h1>Solar Miner - Crypto Sales &amp; FIFO Report</h1>")
                .append("<p>Generated on: <strong>").append(generationDate).append("</strong> | Methodology: <strong>First-In-First-Out (FIFO)</strong></p>")
                .append("</div>");

        if (processedSales.isEmpty()) {
            html.append("<p>No sales data recorded for this site yet.</p></body></html>");
            return html.toString();
        }

        double allTimeRevenue = processedSales.stream().mapToDouble(s -> s.totalRevenue.getRawMoneyAmount()).sum();
        double allTimeAcqCost = processedSales.stream().mapToDouble(s -> s.totalAcquisitionCost.getRawMoneyAmount()).sum();
        double allTimeProfit = processedSales.stream().mapToDouble(s -> s.totalNetProfit.getRawMoneyAmount()).sum();

        html.append("<div class='kpi-container'>")
                .append("<div class='kpi-box'>")
                .append("<div class='kpi-title'>Total Sales Revenue</div>")
                .append("<p class='kpi-value'>").append(new Money(allTimeRevenue, context.getCurrency())).append("</p>")
                .append("</div>")
                .append("<div class='kpi-box'>")
                .append("<div class='kpi-title'>Total Acq. Cost</div>")
                .append("<p class='kpi-value'>").append(new Money(allTimeAcqCost, context.getCurrency())).append("</p>")
                .append("</div>")
                .append("<div class='kpi-box'>")
                .append("<div class='kpi-title'>Total Realized Profit</div>")
                .append("<p class='kpi-value ").append(allTimeProfit >= 0 ? "positive" : "negative").append("'>")
                .append(new Money(allTimeProfit, context.getCurrency())).append("</p>")
                .append("</div>")
                .append("</div>");

        for (SaleReportDto sale : processedSales) {
            String profitClass = sale.totalNetProfit.getRawMoneyAmount() >= 0 ? "positive" : "negative";

            html.append("<div class='sale-block'>")
                    .append("<h3 class='sale-title'>Sale on ").append(sale.saleDate.format(dateFormatter)).append("</h3>")
                    .append("<p class='sale-summary'>")
                    .append("Total Sold: <strong>").append(FormatUtil.formatBitcoin(sale.totalBtcSold)).append("</strong> &#160;|&#160; ")
                    .append("Revenue: <strong>").append(sale.totalRevenue.toString()).append("</strong> &#160;|&#160; ")
                    .append("Total Profit/Loss: <strong class='").append(profitClass).append("'>").append(sale.totalNetProfit).append("</strong>")
                    .append("</p>");

            html.append("<table class='sub-table'><thead><tr>")
                    .append("<th>Mined Date</th>")
                    .append("<th class='text-right'>BTC Amount</th>")
                    .append("<th class='text-right'>Acq. Cost</th>")
                    .append("<th class='text-right'>Sale Value</th>")
                    .append("<th class='text-right'>Profit / Loss</th>")
                    .append("<th class='text-center'>Holding Period</th>")
                    .append("</tr></thead><tbody>");

            for (SaleDetailDto tranche : sale.tranches) {
                String tProfitClass = tranche.netProfit.getRawMoneyAmount() >= 0 ? "positive" : "negative";

                html.append("<tr>")
                        .append("<td>").append(tranche.minedDate.format(dateFormatter)).append("</td>")
                        .append("<td class='text-right'>").append(FormatUtil.formatBitcoin(tranche.amount)).append("</td>")
                        .append("<td class='text-right'>").append(tranche.acquisitionCost.toString()).append("</td>")
                        .append("<td class='text-right'>").append(tranche.saleValue.toString()).append("</td>")
                        .append("<td class='text-right ").append(tProfitClass).append("'>").append(tranche.netProfit).append("</td>")
                        .append("<td class='text-center'>").append(tranche.holdingDays).append(" Days</td>")
                        .append("</tr>");
            }
            html.append("</tbody></table></div>");
        }

        html.append("<div class='footer'>")
                .append("This document was generated automatically. Tranches are matched chronologically using the First-In-First-Out (FIFO) method. <br/>")
                .append("This document does not constitute legally binding tax advice.")
                .append("</div></body></html>");

        return html.toString();
    }

    private String generateMiningHtml(List<PVStatisticDto> statistics, FinanceKpiDto kpis, ReportContext context) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", context.getLocale());
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", context.getLocale());
        String generationDate = LocalDate.now(context.getZoneId()).format(dateFormatter);
        String dateRange = getRangeString(statistics, dateFormatter);

        double totalMiningValue = 0;
        double totalNetProfit = 0;
        for (PVStatisticDto stat : statistics) {
            totalMiningValue += stat.miningRevenueHistoric().getRawMoneyAmount();
            totalNetProfit += (stat.miningRevenueHistoric().getRawMoneyAmount() - stat.miningCost().getRawMoneyAmount());
        }

        StringBuilder html = new StringBuilder();
        html.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        html.append("<html><head>").append(getBaseCss()).append("</head><body>");

        html.append("<div class='header'>")
                .append("<h1>Solar Miner - Mining Generation Report</h1>")
                .append("<p>Generated on: <strong>").append(generationDate).append("</strong> | Period: <strong>").append(dateRange).append("</strong></p>")
                .append("</div>");

        html.append("<div class='kpi-container'>")
                .append("<div class='kpi-box'>")
                .append("<div class='kpi-title'>Total Mined BTC / XMR / PRL</div>")
                .append("<p class='kpi-value'>").append(escape(coinTotals(statistics))).append("</p>")
                .append("</div>")
                .append("<div class='kpi-box'>")
                .append("<div class='kpi-title'>Historical Mining Value</div>")
                .append("<p class='kpi-value'>").append(new Money(totalMiningValue, context.getCurrency())).append("</p>")
                .append("</div>")
                .append("<div class='kpi-box'>")
                .append("<div class='kpi-title'>Total Net Profit</div>")
                .append("<p class='kpi-value ").append(totalNetProfit >= 0 ? "positive" : "negative").append("'>")
                .append(new Money(totalNetProfit, context.getCurrency())).append("</p>")
                .append("</div>")
                .append("</div>");

        html.append("<table><thead><tr>")
                .append("<th>Date</th>")
                .append("<th class='text-right'>PV Usage</th>")
                .append("<th class='text-right'> Power Cost (" + context.getCurrency().getSymbol() + "/kwh)</th>")
                .append("<th class='text-right'>Grid Import</th>")
                .append("<th class='text-right'>Coin Rewards</th>")
                .append("<th class='text-right'>Mining Value</th>")
                .append("<th class='text-right'>Total Cost</th>")
                .append("<th class='text-right'>Net Profit</th>")
                .append("</tr></thead><tbody>");

        Map<YearMonth, List<PVStatisticDto>> groupedStats = statistics.stream().collect(Collectors.groupingBy(stat -> YearMonth.from(stat.date())));
        List<YearMonth> sortedMonths = groupedStats.keySet().stream().sorted(Comparator.reverseOrder()).toList();

        for (YearMonth month : sortedMonths) {
            List<PVStatisticDto> monthStats = groupedStats.get(month);
            monthStats.sort((a, b) -> b.date().compareTo(a.date()));

            double mPvUsage = 0, mGridUsage = 0, mRevenue = 0, mCost = 0, mProfit = 0;

            for (PVStatisticDto stat : monthStats) {
                double netProfitRaw = stat.miningRevenueHistoric().getRawMoneyAmount() - stat.miningCost().getRawMoneyAmount();

                mPvUsage += stat.miningPvUsage();
                mGridUsage += stat.miningGridUsage();
                mRevenue += stat.miningRevenueHistoric().getRawMoneyAmount();
                mCost += stat.miningCost().getRawMoneyAmount();
                mProfit += netProfitRaw;

                Money netProfit = new Money(netProfitRaw, context.getCurrency());
                String profitClass = netProfitRaw >= 0 ? "positive" : "negative";

                html.append("<tr>")
                        .append("<td>").append(stat.date().format(dateFormatter)).append("</td>")
                        .append("<td class='text-right'>").append(FormatUtil.formatNumber(stat.miningPvUsage())).append(" kWh</td>")
                        .append("<td class='text-right'>").append(FormatUtil.formatNumber(stat.feedInPricePerKwh().getRawMoneyAmount())).append(" " + context.getCurrency().getSymbol() + "</td>")
                        .append("<td class='text-right'>").append(FormatUtil.formatNumber(stat.miningGridUsage())).append(" kWh</td>")
                        .append("<td class='text-right'>").append(escape(coinRewards(stat))).append(stat.miningRevenueComplete() ? "" : " *").append("</td>")
                        .append("<td class='text-right'>").append(stat.miningRevenueHistoric()).append("</td>")
                        .append("<td class='text-right'>").append(stat.miningCost()).append("</td>")
                        .append("<td class='text-right ").append(profitClass).append("'>").append(netProfit).append("</td>")
                        .append("</tr>");
            }
            String mProfitClass = mProfit >= 0 ? "positive" : "negative";
            html.append("<tr class='summary-row'>")
                    .append("<td>Sum ").append(month.format(monthFormatter)).append("</td>")
                    .append("<td class='text-right'>").append(FormatUtil.formatNumber(mPvUsage)).append(" kWh</td>")
                    .append("<td class='text-right'>-</td>")
                    .append("<td class='text-right'>").append(FormatUtil.formatNumber(mGridUsage)).append(" kWh</td>")
                    .append("<td class='text-right'>").append(escape(coinTotals(monthStats))).append("</td>")
                    .append("<td class='text-right'>").append(new Money(mRevenue, context.getCurrency())).append("</td>")
                    .append("<td class='text-right'>").append(new Money(mCost, context.getCurrency())).append("</td>")
                    .append("<td class='text-right ").append(mProfitClass).append("'>").append(new Money(mProfit, context.getCurrency())).append("</td>")
                    .append("</tr>");
        }

        html.append("</tbody></table><div class='footer'>* Kryptex reward history was unavailable for at least one configured XMR/PRL target on that day. Pool balance and wallet balance are not counted as income. This document does not constitute legally binding tax advice.</div></body></html>");
        return html.toString();
    }

    private String generatePvHtml(List<PVStatisticDto> statistics, FinanceKpiDto kpis, ReportContext context) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", context.getLocale());
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", context.getLocale());
        String generationDate = LocalDate.now(context.getZoneId()).format(dateFormatter);
        String dateRange = getRangeString(statistics, dateFormatter);
        String breakEvenText = kpis.estimatedBreakEvenDate() != null ? kpis.estimatedBreakEvenDate().format(dateFormatter) : "N/A";
        double miningRevenue = statistics.stream().mapToDouble(stat -> stat.miningRevenueHistoric().getRawMoneyAmount()).sum();

        StringBuilder html = new StringBuilder();
        html.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<html><head>").append(getBaseCss()).append("</head><body>");

        html.append("<div class='header'>")
                .append("<h1>Solar Miner - PV System &amp; ROI Report</h1>")
                .append("<p>Generated on: <strong>").append(generationDate).append("</strong> | Period: <strong>").append(dateRange).append("</strong></p>")
                .append("</div>");

        html.append("<div class='kpi-container'>")
                .append("<div class='kpi-box'><div class='kpi-title'>Total Investment</div><p class='kpi-value'>").append(kpis.totalInvestment().toString()).append("</p></div>")
                .append("<div class='kpi-box'><div class='kpi-title'>Total Household Savings</div><p class='kpi-value positive'>").append(kpis.totalHouseholdSavings().toString()).append("</p></div>")
                .append("<div class='kpi-box'><div class='kpi-title'>Estimated Break-Even</div><p class='kpi-value'>").append(breakEvenText).append("</p></div>")
                .append("</div>");

        html.append("<h3>Mining revenue (BTC / XMR / PRL)</h3><p>")
                .append(escape(coinTotals(statistics))).append(" · ")
                .append(new Money(miningRevenue, context.getCurrency()))
                .append(". Daily XMR/PRL values come from Kryptex reward history; holdings and unpaid pool balances are excluded.</p>");

        html.append("<h3>Daily PV Performance</h3><table><thead><tr><th>Date</th><th class='text-right'>Total PV Prod.</th><th class='text-right'>Household Usage</th><th class='text-right'>Grid Export</th><th class='text-right'>Savings</th><th class='text-right'>Feed-In Revenue</th></tr></thead><tbody>");

        Map<YearMonth, List<PVStatisticDto>> groupedStats = statistics.stream().collect(Collectors.groupingBy(stat -> YearMonth.from(stat.date())));
        List<YearMonth> sortedMonths = groupedStats.keySet().stream().sorted(Comparator.reverseOrder()).toList();

        for (YearMonth month : sortedMonths) {
            List<PVStatisticDto> monthStats = groupedStats.get(month);
            monthStats.sort((a, b) -> b.date().compareTo(a.date()));
            double mTotalPv = 0, mHouseUsage = 0, mExport = 0, mSavings = 0, mRevenue = 0;

            for (PVStatisticDto stat : monthStats) {
                mTotalPv += stat.totalPvProduction();
                mHouseUsage += stat.householdPvUsage();
                mExport += stat.exportedKwh();
                mSavings += stat.householdSavings().getRawMoneyAmount();
                mRevenue += stat.feedInRevenue().getRawMoneyAmount();

                html.append("<tr><td>").append(stat.date().format(dateFormatter)).append("</td><td class='text-right'>").append(FormatUtil.formatNumber(stat.totalPvProduction())).append(" kWh</td><td class='text-right'>").append(FormatUtil.formatNumber(stat.householdPvUsage())).append(" kWh</td><td class='text-right'>").append(FormatUtil.formatNumber(stat.exportedKwh())).append(" kWh</td><td class='text-right positive'>").append(stat.householdSavings()).append("</td><td class='text-right'>").append(stat.feedInRevenue()).append("</td></tr>");
            }
            html.append("<tr class='summary-row'><td>Sum ").append(month.format(monthFormatter)).append("</td><td class='text-right'>").append(FormatUtil.formatNumber(mTotalPv)).append(" kWh</td><td class='text-right'>").append(FormatUtil.formatNumber(mHouseUsage)).append(" kWh</td><td class='text-right'>").append(FormatUtil.formatNumber(mExport)).append(" kWh</td><td class='text-right positive'>").append(new Money(mSavings, context.getCurrency())).append("</td><td class='text-right'>").append(new Money(mRevenue, context.getCurrency())).append("</td></tr>");
        }

        html.append("</tbody></table><div class='footer'>This document tracks your PV system's financial performance based on your historical electricity tariffs and feed-in rates.</div></body></html>");
        return html.toString();
    }

    private InputStream generateErrorPdf(Exception error) {
        return generateErrorPdf(error, new ReportContext(Locale.GERMAN, CustomCurrency.getInstance("EUR"), ZoneId.systemDefault()));
    }

    private InputStream generateErrorPdf(Exception error, ReportContext context) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            String errorMsg = error.getMessage() != null ? error.getMessage() : error.getClass().getName();
            errorMsg = escape(errorMsg);
            boolean german = "de".equalsIgnoreCase(context.getLocale().getLanguage());
            String html = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<html><body><h2>"
                    + (german ? "Fehler bei der PDF-Generierung" : "PDF generation failed") + "</h2><p>"
                    + (german ? "Es ist ein Fehler aufgetreten: " : "An error occurred: ")
                    + "<strong>" + errorMsg + "</strong></p></body></html>";
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, "");
            builder.toStream(os);
            builder.run();
            return new ByteArrayInputStream(os.toByteArray());
        } catch (Exception ex) {
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    private HistoricalFinanceData calculateHistoricalValues(PVStatisticDto stat, CustomCurrency targetCurrency) {
        CustomCurrency btcCurrency = CustomCurrency.getInstance("BTC");

        double btcPriceRate = globalConstantsService.convertHistorical(1.0, btcCurrency, targetCurrency, stat.date());

        if (btcPriceRate < 0) {
            btcPriceRate = stat.minedBtc() > 0 ? stat.btcLiveValue().getRawMoneyAmount() / stat.minedBtc() : 0.0;
        }

        double histFiatVal = stat.minedBtc() * btcPriceRate;

        double histEffYield = stat.minerConsumption() > 0 ? (histFiatVal / stat.minerConsumption()) : 0.0;

        return new HistoricalFinanceData(
                new Money(histFiatVal, targetCurrency),
                new Money(histEffYield, targetCurrency),
                new Money(btcPriceRate, targetCurrency)
        );
    }

    private static String rewardAmount(PVStatisticDto stat, String coin) {
        return stat.miningCoins().stream().filter(line -> coin.equals(line.coin()) && line.amount() != null)
                .map(line -> Double.toString(line.amount())).collect(Collectors.joining("+"));
    }

    static String coinRewards(PVStatisticDto stat) {
        return stat.miningCoins().stream().filter(line -> line.amount() != null && line.amount() != 0)
                .map(line -> formatCoinAmount(line.amount(), line.symbol()) + " " + line.symbol())
                .collect(Collectors.joining(", "));
    }

    static String coinTotals(List<PVStatisticDto> statistics) {
        Map<String, Double> totals = statistics.stream().flatMap(stat -> stat.miningCoins().stream())
                .filter(line -> line.amount() != null)
                .collect(Collectors.groupingBy(CoinMiningDayDto::symbol, Collectors.summingDouble(CoinMiningDayDto::amount)));
        return totals.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> formatCoinAmount(entry.getValue(), entry.getKey()) + " " + entry.getKey())
                .collect(Collectors.joining(" · "));
    }

    private static String formatCoinAmount(double amount, String symbol) {
        // BTC rewards are recorded in satoshis; retain all eight decimal places when needed.
        String pattern = "BTC".equals(symbol) ? "#,##0.########" : "#,##0.############";
        return new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US)).format(amount);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String getRangeString(List<PVStatisticDto> statistics, DateTimeFormatter formatter) {
        if (statistics.isEmpty()) return "N/A";
        LocalDate startDate = statistics.get(statistics.size() - 1).date();
        LocalDate endDate = statistics.get(0).date();
        return startDate.format(formatter) + " - " + endDate.format(formatter);
    }

    private String getBaseCss() {
        return "<style>" +
                "@page { margin: 20mm; @bottom-right { content: 'Page ' counter(page) ' of ' counter(pages); font-family: sans-serif; font-size: 10px; color: #7f8c8d; } }" +
                "body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; color: #2c3e50; line-height: 1.5; }" +
                ".header { border-bottom: 2px solid #00FFA3; padding-bottom: 10px; margin-bottom: 20px; }" +
                ".header h1 { margin: 0; font-size: 24px; color: #1a252f; }" +
                ".header p { margin: 5px 0 0 0; color: #7f8c8d; font-size: 12px; }" +
                ".kpi-container { width: 100%; margin-bottom: 30px; }" +
                ".kpi-box { display: inline-block; width: 30%; background: #f8f9fa; border: 1px solid #e9ecef; border-radius: 8px; padding: 15px; margin-right: 2%; box-sizing: border-box; }" +
                ".kpi-box:last-child { margin-right: 0; }" +
                ".kpi-title { font-size: 11px; text-transform: uppercase; color: #95a5a6; margin-bottom: 5px; }" +
                ".kpi-value { font-size: 18px; font-weight: bold; color: #2c3e50; margin: 0; }" +
                "table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 11px; page-break-inside: auto; }" +
                "tr { page-break-inside: avoid; page-break-after: auto; }" +
                "th { background-color: #ecf0f1; color: #34495e; text-align: left; padding: 10px; border-bottom: 2px solid #bdc3c7; }" +
                "td { padding: 8px 10px; border-bottom: 1px solid #ecf0f1; color: #2c3e50; }" +
                "tr:nth-child(even) td { background-color: #fafbfc; }" +
                ".summary-row td { background-color: #dbe4eb !important; font-weight: bold; border-top: 2px solid #bdc3c7; border-bottom: 2px solid #bdc3c7; }" +
                ".text-right { text-align: right; } .text-center { text-align: center; }" +
                ".positive { color: #27ae60; font-weight: bold; } .negative { color: #c0392b; font-weight: bold; }" +
                ".sale-block { margin-top: 30px; margin-bottom: 20px; }" +
                ".sale-title { margin-bottom: 0px; color: #2c3e50; font-size: 16px; border-bottom: 1px solid #bdc3c7; padding-bottom: 5px; }" +
                ".sale-summary { font-size: 12px; color: #7f8c8d; margin-top: 5px; margin-bottom: 10px; }" +
                ".sub-table { width: 95%; margin-left: 5%; margin-bottom: 20px; }" +
                ".badge-green { background-color: #27ae60; color: white; padding: 2px 6px; border-radius: 4px; font-size: 9px; font-weight: bold; }" +
                ".badge-red { background-color: #e74c3c; color: white; padding: 2px 6px; border-radius: 4px; font-size: 9px; font-weight: bold; }" +
                ".footer { margin-top: 40px; font-size: 10px; color: #95a5a6; text-align: center; border-top: 1px solid #ecf0f1; padding-top: 10px; }" +
                "</style>";
    }

    private record HistoricalFinanceData(Money historicalBtcFiatValue, Money historicalEffectiveYield, Money btcPrice) {
    }

    private static class MinedTranche {
        LocalDate date;
        double originalAmount;
        double remainingAmount;
        double originalFiatValue;

        MinedTranche(LocalDate date, double amount, double fiatValue) {
            this.date = date;
            this.originalAmount = amount;
            this.remainingAmount = amount;
            this.originalFiatValue = fiatValue;
        }
    }

    private record SaleDetailDto(LocalDate minedDate, double amount, Money acquisitionCost, Money saleValue,
                                 Money netProfit, long holdingDays) {
    }

    private record SaleReportDto(LocalDate saleDate, double totalBtcSold, Money totalRevenue,
                                 Money totalAcquisitionCost, Money totalNetProfit, List<SaleDetailDto> tranches) {
    }
}
