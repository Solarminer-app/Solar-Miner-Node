package de.verdox.pv_miner.finance;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import de.verdox.pv_miner.dto.FinanceKpiDto;
import de.verdox.pv_miner.dto.PVStatisticDto;
import de.verdox.pv_miner.globalconstants.GlobalConstantsService;
import de.verdox.pv_miner.pvsite.BitcoinSale;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.frontend.user.UserSessionContext;
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
import java.util.ArrayList;
import java.util.Comparator;
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

        private static ReportContext from(UserSessionContext context) {
            return new ReportContext(context.getLocale(), context.getCurrency(), context.getZoneId());
        }
    }

    private final PVFinanceService pvFinanceService;
    private final GlobalConstantsService globalConstantsService;

    public TaxReportService(PVFinanceService pvFinanceService, GlobalConstantsService globalConstantsService) {
        this.pvFinanceService = pvFinanceService;
        this.globalConstantsService = globalConstantsService;
    }

    public InputStream generateMiningPdfReport(PVSiteEntity pvSite, LocalDate from, LocalDate to, UserSessionContext context) {
        return generateMiningPdfReport(pvSite, from, to, ReportContext.from(context));
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

    public InputStream generatePvPdfReport(PVSiteEntity pvSite, LocalDate from, LocalDate to, UserSessionContext context) {
        return generatePvPdfReport(pvSite, from, to, ReportContext.from(context));
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

    public InputStream generateSalesPdfReport(PVSiteEntity pvSite, UserSessionContext context) {
        return generateSalesPdfReport(pvSite, ReportContext.from(context));
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

    public InputStream generateCsvReport(PVSiteEntity pvSite, LocalDate from, LocalDate to, UserSessionContext context) {
        return generateCsvReport(pvSite, from, to, ReportContext.from(context));
    }

    public InputStream generateCsvReport(PVSiteEntity pvSite, LocalDate from, LocalDate to, ReportContext context) {
        try {
            List<PVStatisticDto> statistics = pvFinanceService.getFinanceData(pvSite, from, to, context.getZoneId(), context.getCurrency());
            StringBuilder sb = new StringBuilder();
            sb.append("Date;Total PV (kWh);Household Usage (kWh);Grid Export (kWh);Household Savings;Feed-In Revenue;BTC Mined;BTC Price (Historical);BTC Value (Historical);Mining Cost;Net Profit\n");

            for (PVStatisticDto stat : statistics) {
                HistoricalFinanceData histData = calculateHistoricalValues(stat, context.getCurrency());
                double netProfitRaw = histData.historicalBtcFiatValue().getRawMoneyAmount() - stat.miningCost().getRawMoneyAmount();
                Money netProfit = new Money(netProfitRaw, context.getCurrency());

                sb.append(stat.date()).append(";")
                        .append(FormatUtil.formatNumber(stat.totalPvProduction())).append(";")
                        .append(FormatUtil.formatNumber(stat.householdPvUsage())).append(";")
                        .append(FormatUtil.formatNumber(stat.exportedKwh())).append(";")
                        .append(stat.householdSavings().getRawMoneyAmount()).append(";")
                        .append(stat.feedInRevenue().getRawMoneyAmount()).append(";")
                        .append(FormatUtil.formatBitcoin(stat.minedBtc())).append(";")
                        .append(histData.btcPrice().getRawMoneyAmount()).append(";") // NEU: Tageskurs
                        .append(histData.historicalBtcFiatValue().getRawMoneyAmount()).append(";")
                        .append(stat.miningCost().getRawMoneyAmount()).append(";")
                        .append(netProfit.getRawMoneyAmount()).append("\n");
            }
            return new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new ByteArrayInputStream(("Error in CSV Export: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
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

        double totalHistBtcValue = 0;
        double totalNetProfit = 0;
        for (PVStatisticDto stat : statistics) {
            HistoricalFinanceData hData = calculateHistoricalValues(stat, context.getCurrency());
            totalHistBtcValue += hData.historicalBtcFiatValue().getRawMoneyAmount();
            totalNetProfit += (hData.historicalBtcFiatValue().getRawMoneyAmount() - stat.miningCost().getRawMoneyAmount());
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
                .append("<div class='kpi-title'>Total Mined BTC</div>")
                .append("<p class='kpi-value'>").append(FormatUtil.formatBitcoin(kpis.allTimeMinedBtc())).append("</p>")
                .append("</div>")
                .append("<div class='kpi-box'>")
                .append("<div class='kpi-title'>Historical BTC Value</div>")
                .append("<p class='kpi-value'>").append(new Money(totalHistBtcValue, context.getCurrency())).append("</p>")
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
                .append("<th class='text-right'>BTC Mined</th>")
                .append("<th class='text-right'>BTC Price</th>")
                .append("<th class='text-right'>BTC Value</th>")
                .append("<th class='text-right'>Total Cost</th>")
                .append("<th class='text-right'>Net Profit</th>")
                .append("</tr></thead><tbody>");

        Map<YearMonth, List<PVStatisticDto>> groupedStats = statistics.stream().collect(Collectors.groupingBy(stat -> YearMonth.from(stat.date())));
        List<YearMonth> sortedMonths = groupedStats.keySet().stream().sorted(Comparator.reverseOrder()).toList();

        for (YearMonth month : sortedMonths) {
            List<PVStatisticDto> monthStats = groupedStats.get(month);
            monthStats.sort((a, b) -> b.date().compareTo(a.date()));

            double mPvUsage = 0, mGridUsage = 0, mBtcMined = 0, mBtcVal = 0, mCost = 0, mProfit = 0;

            for (PVStatisticDto stat : monthStats) {
                HistoricalFinanceData histData = calculateHistoricalValues(stat, context.getCurrency());
                double netProfitRaw = histData.historicalBtcFiatValue().getRawMoneyAmount() - stat.miningCost().getRawMoneyAmount();

                mPvUsage += stat.miningPvUsage();
                mGridUsage += stat.miningGridUsage();
                mBtcMined += stat.minedBtc();
                mBtcVal += histData.historicalBtcFiatValue().getRawMoneyAmount();
                mCost += stat.miningCost().getRawMoneyAmount();
                mProfit += netProfitRaw;

                Money netProfit = new Money(netProfitRaw, context.getCurrency());
                String profitClass = netProfitRaw >= 0 ? "positive" : "negative";

                html.append("<tr>")
                        .append("<td>").append(stat.date().format(dateFormatter)).append("</td>")
                        .append("<td class='text-right'>").append(FormatUtil.formatNumber(stat.miningPvUsage())).append(" kWh</td>")
                        .append("<td class='text-right'>").append(FormatUtil.formatNumber(stat.feedInPricePerKwh().getRawMoneyAmount())).append(" " + context.getCurrency().getSymbol() + "</td>")
                        .append("<td class='text-right'>").append(FormatUtil.formatNumber(stat.miningGridUsage())).append(" kWh</td>")
                        .append("<td class='text-right'>").append(FormatUtil.formatBitcoin(stat.minedBtc())).append("</td>")
                        .append("<td class='text-right'>").append(histData.btcPrice().toString()).append("</td>") // TAGESKURS
                        .append("<td class='text-right'>").append(histData.historicalBtcFiatValue()).append("</td>")
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
                    .append("<td class='text-right'>").append(FormatUtil.formatBitcoin(mBtcMined)).append("</td>")
                    .append("<td class='text-right'>-</td>")
                    .append("<td class='text-right'>").append(new Money(mBtcVal, context.getCurrency())).append("</td>")
                    .append("<td class='text-right'>").append(new Money(mCost, context.getCurrency())).append("</td>")
                    .append("<td class='text-right ").append(mProfitClass).append("'>").append(new Money(mProfit, context.getCurrency())).append("</td>")
                    .append("</tr>");
        }

        html.append("</tbody></table><div class='footer'>This document does not constitute legally binding tax advice.</div></body></html>");
        return html.toString();
    }

    private String generatePvHtml(List<PVStatisticDto> statistics, FinanceKpiDto kpis, ReportContext context) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", context.getLocale());
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", context.getLocale());
        String generationDate = LocalDate.now(context.getZoneId()).format(dateFormatter);
        String dateRange = getRangeString(statistics, dateFormatter);
        String breakEvenText = kpis.estimatedBreakEvenDate() != null ? kpis.estimatedBreakEvenDate().format(dateFormatter) : "N/A";

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
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            String errorMsg = error.getMessage() != null ? error.getMessage() : error.getClass().getName();
            errorMsg = errorMsg.replace("<", "&lt;").replace(">", "&gt;").replace("&", "&amp;");
            String html = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<html><body><h2>Fehler bei der PDF-Generierung</h2><p>Es ist ein Fehler aufgetreten: <strong>" + errorMsg + "</strong></p></body></html>";
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
