package de.verdox.pv_miner.controller;

import de.verdox.pv_miner.dto.FinancePageRequests.BitcoinSaleRequest;
import de.verdox.pv_miner.dto.PVStatisticDto;
import de.verdox.pv_miner.entity.EntityService;
import de.verdox.pv_miner.finance.PVFinanceService;
import de.verdox.pv_miner.finance.TaxReportService;
import de.verdox.pv_miner.finance.TaxReportService.ReportContext;
import de.verdox.pv_miner.dto.FinanceKpiDto;
import de.verdox.pv_miner.dto.FinancePageDto;
import de.verdox.pv_miner.dto.MoneyDto;
import de.verdox.pv_miner.pvsite.BitcoinSale;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import de.verdox.pv_miner.util.Money;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/pv-site/{siteId}/finance")
@CrossOrigin(origins = "http://localhost:3000", exposedHeaders = HttpHeaders.CONTENT_DISPOSITION)
public class FinanceController {
    private final PVSiteRepository pvSiteRepository;
    private final PVFinanceService financeService;
    private final EntityService entityService;
    private final TaxReportService taxReportService;

    public FinanceController(PVSiteRepository pvSiteRepository,
                             PVFinanceService financeService,
                             EntityService entityService,
                             TaxReportService taxReportService) {
        this.pvSiteRepository = pvSiteRepository;
        this.financeService = financeService;
        this.entityService = entityService;
        this.taxReportService = taxReportService;
    }

    @GetMapping("/export/{reportType}")
    public ResponseEntity<byte[]> exportReport(
            @PathVariable UUID siteId,
            @PathVariable String reportType,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "Europe/Berlin") String timeZone,
            @RequestParam(defaultValue = "EUR") String currency,
            @RequestParam(defaultValue = "en") String locale
    ) throws IOException {
        PVSiteEntity site = findSite(siteId);
        ZoneId zoneId = parseZoneId(timeZone);
        LocalDate today = LocalDate.now(zoneId);
        LocalDate selectedFrom = from == null
                ? (site.getSetupDate() == null ? today : site.getSetupDate())
                : from;
        LocalDate selectedTo = to == null ? today : to;
        if (selectedFrom.isAfter(selectedTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The start date must not be after the end date");
        }

        ReportContext context = new ReportContext(
                Locale.forLanguageTag(locale),
                CustomCurrency.getInstance(currency),
                zoneId
        );

        InputStream report;
        String filename;
        MediaType mediaType;
        switch (reportType) {
            case "csv" -> {
                report = taxReportService.generateCsvReport(site, selectedFrom, selectedTo, context);
                filename = "finance_" + selectedFrom + "_" + selectedTo + ".csv";
                mediaType = MediaType.parseMediaType("text/csv;charset=UTF-8");
            }
            case "mining-pdf" -> {
                report = taxReportService.generateMiningPdfReport(site, selectedFrom, selectedTo, context);
                filename = "mining_tax_report_" + selectedFrom + "_" + selectedTo + ".pdf";
                mediaType = MediaType.APPLICATION_PDF;
            }
            case "pv-pdf" -> {
                report = taxReportService.generatePvPdfReport(site, selectedFrom, selectedTo, context);
                filename = "pv_roi_report_" + selectedFrom + "_" + selectedTo + ".pdf";
                mediaType = MediaType.APPLICATION_PDF;
            }
            case "sales-pdf" -> {
                report = taxReportService.generateSalesPdfReport(site, context);
                filename = "crypto_sales_fifo_report.pdf";
                mediaType = MediaType.APPLICATION_PDF;
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported finance report type");
        }

        try (report) {
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(report.readAllBytes());
        }
    }

    @GetMapping
    public FinancePageDto getFinancePage(
            @PathVariable UUID siteId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "Europe/Berlin") String timeZone,
            @RequestParam(defaultValue = "EUR") String currency
    ) {
        PVSiteEntity site = findSite(siteId);
        ZoneId zoneId = parseZoneId(timeZone);
        CustomCurrency targetCurrency = CustomCurrency.getInstance(currency);
        LocalDate today = LocalDate.now(zoneId);
        LocalDate setupDate = site.getSetupDate() == null ? today : site.getSetupDate();
        LocalDate selectedFrom = from == null ? setupDate : from;
        LocalDate selectedTo = to == null ? today : to;

        if (selectedFrom.isAfter(selectedTo)) {
            throw new IllegalArgumentException("Das Startdatum muss vor dem Enddatum liegen");
        }

        List<PVStatisticDto> filtered = financeService.getFinanceData(
                site, selectedFrom, selectedTo, zoneId, targetCurrency
        );
        List<PVStatisticDto> allTime = selectedFrom.equals(setupDate) && selectedTo.equals(today)
                ? filtered
                : financeService.getFinanceData(site, setupDate, today, zoneId, targetCurrency);

        List<FinancePageDto.BitcoinSaleDto> sales = site.getBitcoinSales().stream()
                .sorted(Comparator.naturalOrder())
                .map(sale -> new FinancePageDto.BitcoinSaleDto(
                        sale.getSaleDate(),
                        sale.getAmountBtc(),
                        MoneyDto.from(sale.getFiatValue())
                ))
                .toList();

        FinanceKpiDto filteredKpis = financeService.calculateKPIs(site, filtered, targetCurrency, zoneId);
        FinanceKpiDto allTimeKpis = financeService.calculateKPIs(site, allTime, targetCurrency, zoneId);

        return new FinancePageDto(
                setupDate,
                selectedFrom,
                selectedTo,
                filteredKpis,
                allTimeKpis,
                financeService.calculateInsights(site, filtered, filteredKpis, targetCurrency, zoneId),
                financeService.calculateInsights(site, allTime, allTimeKpis, targetCurrency, zoneId),
                filtered,
                sales
        );
    }

    @PostMapping("/sales")
    public ResponseEntity<Void> addSale(@PathVariable UUID siteId,
                                        @RequestBody BitcoinSaleRequest request) {
        if (request.saleDate() == null
                || request.amountBtc() <= 0
                || request.fiatAmount() < 0
                || request.currency() == null) {
            return ResponseEntity.badRequest().build();
        }

        PVSiteEntity site = findSite(siteId);
        site.getBitcoinSales().add(new BitcoinSale(
                request.saleDate(),
                request.amountBtc(),
                new Money(request.fiatAmount(), CustomCurrency.getInstance(request.currency()))
        ));
        site.getBitcoinSales().sort(Comparator.naturalOrder());
        entityService.save(site);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sales")
    public ResponseEntity<Void> deleteSale(@PathVariable UUID siteId,
                                           @RequestBody BitcoinSaleRequest request) {
        PVSiteEntity site = findSite(siteId);
        boolean removed = site.getBitcoinSales().removeIf(sale ->
                sale.getSaleDate().equals(request.saleDate())
                        && Double.compare(sale.getAmountBtc(), request.amountBtc()) == 0
                        && Double.compare(sale.getFiatValue().getRawMoneyAmount(), request.fiatAmount()) == 0
                        && sale.getFiatValue().getCurrency().getCurrencyCode().equals(request.currency())
        );

        if (!removed) {
            return ResponseEntity.notFound().build();
        }
        entityService.save(site);
        return ResponseEntity.noContent().build();
    }

    private PVSiteEntity findSite(UUID siteId) {
        return pvSiteRepository.findById(siteId)
                .orElseThrow(() -> new IllegalArgumentException("PV-Site nicht gefunden"));
    }

    private ZoneId parseZoneId(String timeZone) {
        try {
            return ZoneId.of(timeZone);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Ungültige Zeitzone", exception);
        }
    }

}
