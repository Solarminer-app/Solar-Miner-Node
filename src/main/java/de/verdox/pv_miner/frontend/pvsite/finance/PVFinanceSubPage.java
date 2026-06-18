package de.verdox.pv_miner.frontend.pvsite.finance;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.grid.FooterRow;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.theme.lumo.Lumo;
import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.entity.EntityService;
import de.verdox.pv_miner.featuretracking.FeatureTrackingService;
import de.verdox.pv_miner.finance.FinanceKpiDto;
import de.verdox.pv_miner.finance.PVFinanceService;
import de.verdox.pv_miner.finance.PVStatisticDto;
import de.verdox.pv_miner.frontend.user.*;
import de.verdox.pv_miner.globalconstants.GlobalConstantsService;
import de.verdox.pv_miner.pvsite.BitcoinSale;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import de.verdox.pv_miner.taxreport.TaxReportService;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.util.FormatUtil;
import de.verdox.pv_miner.util.Money;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableButton;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableSpan;
import de.verdox.pv_miner.frontend.AppMainLayout;
import de.verdox.pv_miner.frontend.FrontendColor;
import de.verdox.pv_miner.frontend.components.Blur;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@Route(value = "site/:siteId/finance", layout = AppMainLayout.class)
@CssImport("./themes/solarminer/finance.css")
public class PVFinanceSubPage extends VerticalLayout implements LocaleChangeObserver, CurrencyChangeObserver, TimeZoneChangeObserver, BeforeEnterObserver {

    private final EntityService entityService;
    private final UserSessionContext userSessionContext;
    private final PVSiteRepository pVSiteRepository;
    private final PVFinanceService pvFinanceService;
    private final TaxReportService taxReportService;
    private final FeatureTrackingService featureTrackingService;

    private PVSiteEntity pvSiteEntity;

    private final ProgressBar loadingIndicator = new ProgressBar();

    private final Span totalInvestmentSpan = new Span();
    private final Span breakEvenSpan = new Span();
    private final Span estimatedBreakEvenSpan = new Span();

    private final H3 filteredSectionTitle = new H3();
    private final Span realizedProfitSpan = new Span();
    private final Span exportRevenue = new Span();
    private final Span householdSavingsSpan = new Span();
    private final Span unrealizedValueSpan = new Span();

    private final Tabs tabs = new Tabs();
    private final Div contentArea = new Div();

    private final DatePicker datePickerFrom = new DatePicker();
    private final DatePicker datePickerTo = new DatePicker();
    private final Button calculateStatistics = new Button(VaadinIcon.REFRESH.create());

    private final Grid<PVStatisticDto> historyGrid = new Grid<>();
    private final List<PVStatisticDto> cachedStatistics = new ArrayList<>();
    private FooterRow historyFooterRow;

    private final Grid<BitcoinSale> ledgerGrid = new Grid<>();
    private final DatePicker saleDatePicker = new DatePicker();
    private final NumberField saleBtcField = new NumberField();
    private final NumberField saleFiatField = new NumberField();
    private final Button addSaleBtn = new TranslatableButton("finance.sellbtc.button", VaadinIcon.PLUS.create());

    private record AsyncDataResult(List<PVStatisticDto> filteredStats, FinanceKpiDto filteredKpis, FinanceKpiDto allTimeKpis) {}

    public PVFinanceSubPage(EntityService entityService,
                            UserSessionContext userSessionContext,
                            PVSiteRepository pVSiteRepository,
                            PVFinanceService pvFinanceService,
                            TaxReportService taxReportService,
                            FeatureTrackingService featureTrackingService) {

        this.entityService = entityService;
        this.userSessionContext = userSessionContext;
        this.pVSiteRepository = pVSiteRepository;
        this.pvFinanceService = pvFinanceService;
        this.taxReportService = taxReportService;
        this.featureTrackingService = featureTrackingService;

        getElement().setAttribute("theme", Lumo.DARK);
        addClassName("finance-page");
        setSizeFull();

        loadingIndicator.setIndeterminate(true);
        loadingIndicator.setVisible(false);
        loadingIndicator.setWidthFull();
        add(loadingIndicator);

        H3 globalTitle = new H3("Global Amortization & ROI (All-Time)");
        globalTitle.getStyle().set("margin-top", "0");
        globalTitle.getStyle().set("margin-bottom", "0");
        globalTitle.getStyle().set("color", "var(--text-value-white)");
        globalTitle.getStyle().set("font-size", "1.2rem");
        add(globalTitle);

        HorizontalLayout globalStatsLayout = new HorizontalLayout();
        globalStatsLayout.addClassName("finance-kpi-grid");
        globalStatsLayout.getStyle().set("flex-wrap", "wrap");

        globalStatsLayout.add(createStatCard(new TranslatableSpan("finance.kpi.total_investment"), totalInvestmentSpan, VaadinIcon.WALLET, FrontendColor.TEXT_VALUE_WHITE));
        globalStatsLayout.add(createStatCard(new TranslatableSpan("finance.kpi.break_even_roi"), breakEvenSpan, VaadinIcon.CHART_3D, FrontendColor.TEXT_VALUE_WHITE));

        Div estimatedBreakEvenCard = new Div();
        estimatedBreakEvenCard.addClassName("stat-card");
        var breakEvenIcon = VaadinIcon.CALENDAR_CLOCK.create();
        breakEvenIcon.addClassName("stat-card-icon");
        breakEvenIcon.getStyle().set("color", FrontendColor.TEXT_VALUE_GREEN);

        Div breakEvenTextGroup = new Div();
        breakEvenTextGroup.addClassName("stat-card-text-group");
        TranslatableSpan breakEvenTitle = new TranslatableSpan("finance.kpi.estimated_break_even");
        breakEvenTitle.addClassName("stat-card-title");
        estimatedBreakEvenSpan.addClassName("stat-card-value");
        estimatedBreakEvenSpan.getStyle().set("color", FrontendColor.TEXT_VALUE_GREEN);

        if (!featureTrackingService.hasProLicense()) {
            Blur blurValue = new Blur(estimatedBreakEvenSpan, "🔒 Pro", clickEvent -> showProNotification());
            breakEvenTextGroup.add(breakEvenTitle, blurValue);
        } else {
            breakEvenTextGroup.add(breakEvenTitle, estimatedBreakEvenSpan);
        }

        estimatedBreakEvenCard.add(breakEvenIcon, breakEvenTextGroup);
        globalStatsLayout.add(estimatedBreakEvenCard);
        add(globalStatsLayout);


        Tab historyTab = new Tab(VaadinIcon.TIME_BACKWARD.create(), new TranslatableSpan("finance.tab.history"));
        Tab ledgerTab = new Tab(VaadinIcon.BOOK_DOLLAR.create(), new TranslatableSpan("finance.tab.ledger"));

        tabs.add(historyTab, ledgerTab);
        tabs.addClassName("finance-tabs");
        add(tabs);

        contentArea.setSizeFull();
        contentArea.addClassName("finance-content-area");
        add(contentArea);

        setupHistoryTab();
        setupLedgerTab();

        Div historyLayout = (Div) historyGrid.getParent().orElseThrow();
        Div ledgerLayout = (Div) ledgerGrid.getParent().orElseThrow();

        tabs.addSelectedChangeListener(event -> {
            historyLayout.setVisible(event.getSelectedTab().equals(historyTab));
            ledgerLayout.setVisible(event.getSelectedTab().equals(ledgerTab));
        });

        historyLayout.setVisible(true);
        ledgerLayout.setVisible(false);

        updateLabelsAndTexts();
    }

    private void applyDashboardCardStyle(Div element) { element.addClassName("dashboard-card"); }

    private void styleGrid(Grid<?> grid) {
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.addClassName("finance-grid");
    }

    private Div createStatCard(TranslatableSpan translatableTitle, Span valueSpan, VaadinIcon icon, String valueColor) {
        Div card = new Div();
        card.addClassName("stat-card");
        var iconComp = icon.create();
        iconComp.addClassName("stat-card-icon");
        iconComp.getStyle().set("color", valueColor);
        Div textGroup = new Div();
        textGroup.addClassName("stat-card-text-group");
        translatableTitle.addClassName("stat-card-title");
        valueSpan.addClassName("stat-card-value");
        valueSpan.getStyle().set("color", valueColor);
        textGroup.add(translatableTitle, valueSpan);
        card.add(iconComp, textGroup);
        return card;
    }

    private void showProNotification() {
        Notification notification = Notification.show(
                getTranslation("notification.premium_feature", "Diese Funktion ist ein Premium Feature. Upgrade auf die Pro Edition, um sie freizuschalten."),
                4000,
                Notification.Position.MIDDLE
        );
        notification.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
    }

    private void setupHistoryTab() {
        Div layout = new Div();
        layout.setWidth("97%");
        layout.setHeightFull();
        applyDashboardCardStyle(layout);
        layout.getStyle().set("display", "flex").set("flex-direction", "column");


        HorizontalLayout topRow = new HorizontalLayout();
        topRow.setWidthFull();
        topRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        topRow.setAlignItems(FlexComponent.Alignment.BASELINE);
        topRow.getStyle().set("flex-wrap", "wrap");
        topRow.getStyle().set("gap", "16px");

        calculateStatistics.addClickListener(e -> refresh());
        Div datePickersWrapper = new Div(datePickerFrom, datePickerTo);
        datePickersWrapper.getStyle().set("flex-wrap", "wrap");

        datePickersWrapper.getStyle().set("display", "flex");
        datePickersWrapper.getStyle().set("gap", "16px");

        if (!featureTrackingService.hasProLicense()) {
            datePickerFrom.setReadOnly(true);
            datePickerTo.setReadOnly(true);
            datePickersWrapper.getElement().addEventListener("click", e -> showProNotification());
        }

        HorizontalLayout leftControls = new HorizontalLayout(datePickersWrapper, calculateStatistics);
        leftControls.setAlignItems(FlexComponent.Alignment.BASELINE);
        leftControls.getStyle().set("flex-wrap", "wrap");
        Button exportCsvBtn = new TranslatableButton("export.csv", VaadinIcon.FILE_TEXT.create());
        Anchor csvAnchor = new Anchor(createCsvResource(), "");
        csvAnchor.getElement().setAttribute("download", true);
        csvAnchor.add(exportCsvBtn);

        Button exportMiningPdfBtn = new TranslatableButton("export.pdf.mining", VaadinIcon.FILE_PRESENTATION.create());
        exportMiningPdfBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Anchor miningPdfAnchor = new Anchor(createMiningPdfResource(), "");
        miningPdfAnchor.getElement().setAttribute("download", true);
        miningPdfAnchor.add(exportMiningPdfBtn);

        Button exportPvPdfBtn = new TranslatableButton("export.pdf.pv", VaadinIcon.CHART_LINE.create());
        exportPvPdfBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        Anchor pvPdfAnchor = new Anchor(createPvPdfResource(), "");
        pvPdfAnchor.getElement().setAttribute("download", true);
        pvPdfAnchor.add(exportPvPdfBtn);

        HorizontalLayout rightControls = new HorizontalLayout();
        rightControls.setAlignItems(FlexComponent.Alignment.BASELINE);

        if (!featureTrackingService.hasProLicense()) {
            HorizontalLayout exportLayout = new HorizontalLayout(csvAnchor, miningPdfAnchor, pvPdfAnchor);
            exportLayout.getStyle().set("flex-wrap", "wrap");
            Blur blurredExport = new Blur(exportLayout, "🔒 Pro Feature", clickEvent -> showProNotification());
            rightControls.add(blurredExport);
        } else {
            rightControls.add(csvAnchor, miningPdfAnchor, pvPdfAnchor);
            rightControls.getStyle().set("flex-wrap", "wrap");
        }

        topRow.add(leftControls, rightControls);


        filteredSectionTitle.getStyle().set("margin-top", "24px");
        filteredSectionTitle.getStyle().set("margin-bottom", "8px");
        filteredSectionTitle.getStyle().set("color", "var(--text-value-white)");
        filteredSectionTitle.getStyle().set("font-size", "1.2rem");

        HorizontalLayout filteredStatsLayout = new HorizontalLayout();
        filteredStatsLayout.addClassName("finance-kpi-grid");
        filteredStatsLayout.getStyle().set("flex-wrap", "wrap");
        filteredStatsLayout.getStyle().set("margin-bottom", "16px");

        filteredStatsLayout.add(createStatCard(new TranslatableSpan("finance.kpi.realized_profit"), realizedProfitSpan, VaadinIcon.PIGGY_BANK_COIN, FrontendColor.TEXT_VALUE_WHITE));
        filteredStatsLayout.add(createStatCard(new TranslatableSpan("finance.kpi.exportRevenue"), exportRevenue, VaadinIcon.PIGGY_BANK_COIN, FrontendColor.TEXT_VALUE_WHITE));
        filteredStatsLayout.add(createStatCard(new TranslatableSpan("finance.kpi.household_savings"), householdSavingsSpan, VaadinIcon.HOME, FrontendColor.TEXT_VALUE_WHITE));
        filteredStatsLayout.add(createStatCard(new TranslatableSpan("finance.kpi.unrealized_value"), unrealizedValueSpan, VaadinIcon.DIAMOND, FrontendColor.TEXT_VALUE_YELLOW));


        historyGrid.setSizeFull();
        styleGrid(historyGrid);

        historyGrid.addColumn(item -> item.date().format(DateTimeFormatter.ofPattern("dd.MM.yyyy", userSessionContext.getLocale()))).setHeader(new TranslatableSpan("finance.prices.date")).setAutoWidth(true);
        historyGrid.addColumn(item -> FormatUtil.formatNumber(item.totalPvProduction()) + " kWh").setHeader(new TranslatableSpan("finance.grid.total_production")).setAutoWidth(true);
        historyGrid.addColumn(item -> FormatUtil.formatNumber(item.householdPvUsage()) + " kWh").setHeader(new TranslatableSpan("finance.grid.power_usage")).setAutoWidth(true);
        historyGrid.addColumn(item -> FormatUtil.formatNumber(item.exportedKwh()) + " kWh").setHeader(new TranslatableSpan("finance.grid.exported")).setAutoWidth(true);
        historyGrid.addColumn(item -> FormatUtil.formatNumber(item.miningPvUsage()) + " kWh").setHeader(new TranslatableSpan("finance.grid.power")).setAutoWidth(true);
        historyGrid.addColumn(item -> FormatUtil.formatNumber(item.miningGridUsage()) + " kWh").setHeader(new TranslatableSpan("finance.grid.import")).setAutoWidth(true);
        historyGrid.addColumn(item -> item.miningCost().toString()).setHeader(new TranslatableSpan("finance.grid.opex")).setAutoWidth(true);
        historyGrid.addColumn(item -> FormatUtil.formatBitcoin(item.minedBtc())).setHeader(new TranslatableSpan("finance.grid.mined")).setAutoWidth(true);
        historyGrid.addColumn(item -> item.btcLiveValue().toString()).setHeader(new TranslatableSpan("finance.grid.value.live")).setAutoWidth(true);
        historyGrid.addColumn(item -> item.btcHistoricValue().toString()).setHeader(new TranslatableSpan("finance.grid.value.historical")).setAutoWidth(true);

        historyGrid.addComponentColumn(item -> {
            Span badge = new Span(item.effectiveYieldPerKwh().toString() + "/kWh");
            badge.addClassName("yield-badge");

            Money currentFeedIn = pvFinanceService.getPriceForDate(pvSiteEntity.getFeedInTariffHistory(), LocalDate.now(userSessionContext.getZoneId()));
            double feedInUserCurrency = SpringContextHelper.getBean(GlobalConstantsService.class).convert(currentFeedIn, userSessionContext.getCurrency()).getRawMoneyAmount();

            if (item.effectiveYieldPerKwh().getRawMoneyAmount() > feedInUserCurrency) {
                badge.addClassName("positive");
            } else {
                badge.addClassName("negative");
            }

            if (!featureTrackingService.hasProLicense()) {
                return new Blur(badge, "🔒 Pro", clickEvent -> showProNotification());
            }

            return badge;
        }).setHeader(new TranslatableSpan("finance.grid.effective_revenue")).setAutoWidth(true);

        historyFooterRow = historyGrid.appendFooterRow();


        layout.add(topRow, filteredSectionTitle, filteredStatsLayout, historyGrid);
        contentArea.add(layout);
    }

    private void setupLedgerTab() {
        Div layout = new Div();
        layout.setWidth("97%");
        layout.setHeightFull();
        applyDashboardCardStyle(layout);
        layout.getStyle().set("display", "flex").set("flex-direction", "column");


        HorizontalLayout ledgerHeaderRow = new HorizontalLayout();
        ledgerHeaderRow.setWidthFull();
        ledgerHeaderRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        ledgerHeaderRow.setAlignItems(FlexComponent.Alignment.BASELINE);

        H3 title = new H3("Bitcoin Ledger & Sales");
        title.addClassName("ledger-title");

        Button exportSalesPdfBtn = new Button("Crypto Sales (FIFO) PDF", VaadinIcon.FILE_PRESENTATION.create());
        exportSalesPdfBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_CONTRAST);
        Anchor salesPdfAnchor = new Anchor(createSalesPdfResource(), "");
        salesPdfAnchor.getElement().setAttribute("download", true);
        salesPdfAnchor.add(exportSalesPdfBtn);

        if (!featureTrackingService.hasProLicense()) {
            Blur blurredExport = new Blur(salesPdfAnchor, "🔒 Pro Feature", clickEvent -> showProNotification());
            ledgerHeaderRow.add(title, blurredExport);
        } else {
            ledgerHeaderRow.add(title, salesPdfAnchor);
        }

        HorizontalLayout formLayout = new HorizontalLayout(saleDatePicker, saleBtcField, saleFiatField, addSaleBtn);
        formLayout.addClassName("finance-filter-row");

        addSaleBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addSaleBtn.addClickListener(e -> handleAddSale());

        ledgerGrid.setSizeFull();
        styleGrid(ledgerGrid);

        ledgerGrid.addColumn(s -> s.getSaleDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy", userSessionContext.getLocale()))).setHeader(new TranslatableSpan("finance.prices.date")).setAutoWidth(true);
        ledgerGrid.addColumn(s -> FormatUtil.formatBitcoin(s.getAmountBtc())).setHeader(new TranslatableSpan("finance.grid.sold")).setAutoWidth(true);
        ledgerGrid.addColumn(s -> s.getFiatValue().toString()).setHeader(new TranslatableSpan("finance.grid.revenue_fiat")).setAutoWidth(true);

        ledgerGrid.addComponentColumn(sale -> {
            Button delBtn = new Button(VaadinIcon.TRASH.create(), e -> {
                pvSiteEntity.getBitcoinSales().remove(sale);
                entityService.save(pvSiteEntity);
                ledgerGrid.setItems(pvSiteEntity.getBitcoinSales());
                refresh();
                Notification.show(getTranslation("finance.sale.delete"));
            });
            delBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            return delBtn;
        }).setAutoWidth(true);

        layout.add(ledgerHeaderRow, formLayout, ledgerGrid);
        contentArea.add(layout);
    }

    private void handleAddSale() {
        if (saleDatePicker.getValue() == null || saleBtcField.getValue() == null || saleFiatField.getValue() == null) {
            Notification.show(getTranslation("finance.notification.fill_all_fields", "Bitte alle Felder ausfüllen")).addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        double btcToSell = saleBtcField.getValue();
        double fiatReceived = saleFiatField.getValue();

        if (btcToSell <= 0 || fiatReceived < 0) {
            Notification.show(getTranslation("finance.notification.invalid_amount", "Bitte gültige Werte eingeben!")).addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        double allTimeMined = pvFinanceService.fetchAllTimeMinedBtc(pvSiteEntity, userSessionContext.getZoneId());
        double alreadySold = pvSiteEntity.getBitcoinSales().stream().mapToDouble(BitcoinSale::getAmountBtc).sum();
        double availableBtc = allTimeMined - alreadySold;

        if (btcToSell > (availableBtc + 0.00000001)) {
            Notification.show(getTranslation("finance.notification.btc_warning", "Hinweis: Du verkaufst mehr BTC, als laut System gemint wurden. (" + FormatUtil.formatBitcoin(availableBtc) + " verfügbar)."))
                    .addThemeVariants(NotificationVariant.LUMO_WARNING);
        }

        Money fiatVal = new Money(fiatReceived, userSessionContext.getCurrency());
        BitcoinSale sale = new BitcoinSale(saleDatePicker.getValue(), btcToSell, fiatVal);

        pvSiteEntity.getBitcoinSales().add(sale);
        pvSiteEntity.getBitcoinSales().sort(Comparator.naturalOrder());
        entityService.save(pvSiteEntity);

        ledgerGrid.setItems(pvSiteEntity.getBitcoinSales());
        saleDatePicker.clear(); saleBtcField.clear(); saleFiatField.clear();
        refresh();
        Notification.show(getTranslation("finance.notification.sale_added")).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void updateLabelsAndTexts() {
        Locale locale = userSessionContext.getLocale();
        datePickerFrom.setLabel(getTranslation("finance.filter.date_label", "Anzeigen ab"));
        datePickerFrom.setLocale(locale);

        datePickerTo.setLabel(getTranslation("finance.filter.date_to_label", "Anzeigen bis"));
        datePickerTo.setLocale(locale);


        calculateStatistics.setText(getTranslation("finance.filter.refresh_btn"));

        saleFiatField.setLabel(getTranslation("finance.sellbtc.got_in_fiat"));
        saleBtcField.setLabel(getTranslation("finance.sellbtc.amount"));
        saleDatePicker.setLabel(getTranslation("finance.sellbtc.date"));
    }

    protected void connectPVSiteEntity(PVSiteEntity pvSiteEntity) {
        this.pvSiteEntity = pvSiteEntity;
        ZoneId zoneId = userSessionContext.getZoneId();

        datePickerFrom.setMin(pvSiteEntity.getSetupDate());
        datePickerFrom.setMax(LocalDate.now());

        datePickerTo.setMin(pvSiteEntity.getSetupDate());
        datePickerTo.setMax(LocalDate.now());

        if (!featureTrackingService.hasProLicense()) {
            LocalDate today = LocalDate.now(zoneId);
            datePickerFrom.setValue(today.withDayOfMonth(1));
            datePickerTo.setValue(today);
            datePickerFrom.setReadOnly(true);
            datePickerTo.setReadOnly(true);
        } else {
            datePickerFrom.setValue(pvSiteEntity.getSetupDate());
            datePickerTo.setValue(LocalDate.now(zoneId));
        }

        if (pvSiteEntity.getBitcoinSales() != null) {
            ledgerGrid.setItems(pvSiteEntity.getBitcoinSales());
        }
        refresh();
    }

    protected void refresh() {
        if (this.pvSiteEntity == null) return;

        calculateStatistics.setEnabled(false);
        loadingIndicator.setVisible(true);

        ZoneId zone = userSessionContext.getZoneId();
        CustomCurrency currency = userSessionContext.getCurrency();

        LocalDate filterDateFrom = datePickerFrom.getValue() != null ? datePickerFrom.getValue() : pvSiteEntity.getSetupDate();
        LocalDate filterDateTo = datePickerTo.getValue() != null ? datePickerTo.getValue() : LocalDate.now(zone);

        LocalDate allTimeFrom = pvSiteEntity.getSetupDate() != null ? pvSiteEntity.getSetupDate() : LocalDate.now(zone).minusYears(10);
        LocalDate allTimeTo = LocalDate.now(zone);

        UI ui = UI.getCurrent();

        CompletableFuture.supplyAsync(() -> {

            List<PVStatisticDto> filteredStats = pvFinanceService.getFinanceData(pvSiteEntity, filterDateFrom, filterDateTo, zone, currency);
            FinanceKpiDto filteredKpis = pvFinanceService.calculateKPIs(pvSiteEntity, filteredStats, currency, zone);


            List<PVStatisticDto> allTimeStats = pvFinanceService.getFinanceData(pvSiteEntity, allTimeFrom, allTimeTo, zone, currency);
            FinanceKpiDto allTimeKpis = pvFinanceService.calculateKPIs(pvSiteEntity, allTimeStats, currency, zone);

            return new AsyncDataResult(filteredStats, filteredKpis, allTimeKpis);

        }).whenComplete((result, exception) -> {
            ui.access(() -> {
                loadingIndicator.setVisible(false);
                calculateStatistics.setEnabled(true);

                if (exception != null) {
                    Notification.show(getTranslation("finance.notification.error_loading", "Fehler beim Laden der Finanzdaten!"), 3000, Notification.Position.MIDDLE)
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    exception.printStackTrace();
                    return;
                }

                this.cachedStatistics.clear();
                this.cachedStatistics.addAll(result.filteredStats());

                historyGrid.setDataProvider(DataProvider.fromCallbacks(
                        query -> {
                            int offset = query.getOffset();
                            int limit = query.getLimit();
                            int end = Math.min(offset + limit, cachedStatistics.size());
                            if (offset > cachedStatistics.size()) return Stream.empty();
                            return cachedStatistics.subList(offset, end).stream();
                        },
                        query -> cachedStatistics.size()
                ));

                updateFootersAndKPIs(result.filteredKpis(), result.allTimeKpis());
            });
        });
    }

    private void updateFootersAndKPIs(FinanceKpiDto filteredKpis, FinanceKpiDto allTimeKpis) {
        CustomCurrency targetCurrency = userSessionContext.getCurrency();
        Locale locale = userSessionContext.getLocale();


        double totalPvProductionSum = 0, totalHouseholdPvUsage = 0, totalExportedKwh = 0, totalPvUsage = 0, totalGridUsage = 0, totalBtc = 0, totalCost = 0, totalLiveValue = 0;
        for (PVStatisticDto stat : cachedStatistics) {
            totalPvProductionSum += stat.totalPvProduction();
            totalHouseholdPvUsage += stat.householdPvUsage();
            totalExportedKwh += stat.exportedKwh();
            totalPvUsage += stat.miningPvUsage();
            totalGridUsage += stat.miningGridUsage();
            totalBtc += stat.minedBtc();
            totalCost += stat.miningCost().getRawMoneyAmount();
            totalLiveValue += stat.btcLiveValue().getRawMoneyAmount();
        }

        if (!cachedStatistics.isEmpty()) {
            historyFooterRow.getCell(historyGrid.getColumns().get(0)).setComponent(new TranslatableSpan("finance.grid.whole_time"));
            historyFooterRow.getCell(historyGrid.getColumns().get(1)).setText(FormatUtil.formatNumber(totalPvProductionSum) + " kWh");
            historyFooterRow.getCell(historyGrid.getColumns().get(2)).setText(FormatUtil.formatNumber(totalHouseholdPvUsage) + " kWh");
            historyFooterRow.getCell(historyGrid.getColumns().get(3)).setText(FormatUtil.formatNumber(totalExportedKwh) + " kWh");
            historyFooterRow.getCell(historyGrid.getColumns().get(4)).setText(FormatUtil.formatNumber(totalPvUsage) + " kWh");
            historyFooterRow.getCell(historyGrid.getColumns().get(5)).setText(FormatUtil.formatNumber(totalGridUsage) + " kWh");
            historyFooterRow.getCell(historyGrid.getColumns().get(6)).setText(new Money(totalCost, targetCurrency).toString());
            historyFooterRow.getCell(historyGrid.getColumns().get(7)).setText(FormatUtil.formatBitcoin(totalBtc));
            historyFooterRow.getCell(historyGrid.getColumns().get(8)).setText(new Money(totalLiveValue, targetCurrency).toString());

            double avgEffYield = (totalPvUsage + totalGridUsage) > 0 ? (totalLiveValue / (totalPvUsage + totalGridUsage)) : 0.0;

            if (!featureTrackingService.hasProLicense()) {
                Span dummySpan = new Span("0.00 EUR avg");
                Blur footerBlur = new Blur(dummySpan, "🔒 Pro", click -> showProNotification());
                historyFooterRow.getCell(historyGrid.getColumns().get(9)).setComponent(footerBlur);
            } else {
                historyFooterRow.getCell(historyGrid.getColumns().get(9)).setText(new Money(avgEffYield, targetCurrency).toString() + " avg");
            }
        }


        totalInvestmentSpan.setText(allTimeKpis.totalInvestment().toString());

        breakEvenSpan.setText(String.format(locale, "%.2f %%", allTimeKpis.roiProgressPercent()));
        if (allTimeKpis.roiProgressPercent() >= 100) {
            breakEvenSpan.addClassName("break-even-success");
            breakEvenSpan.removeClassName("break-even-default");
        } else {
            breakEvenSpan.addClassName("break-even-default");
            breakEvenSpan.removeClassName("break-even-success");
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", locale);
        if (allTimeKpis.estimatedBreakEvenDate() != null) {
            estimatedBreakEvenSpan.setText(allTimeKpis.estimatedBreakEvenDate().format(formatter));
        } else {
            estimatedBreakEvenSpan.setText("N/A");
        }


        String fromStr = datePickerFrom.getValue() != null ? datePickerFrom.getValue().format(formatter) : "";
        String toStr = datePickerTo.getValue() != null ? datePickerTo.getValue().format(formatter) : "";
        filteredSectionTitle.setText(getTranslation("finance.kpi.dashboard_title", "Performance Dashboard") + " (" + fromStr + " - " + toStr + ")");

        realizedProfitSpan.setText(filteredKpis.realizedProfit().toString());
        exportRevenue.setText(filteredKpis.totalFeedInRevenue().toString());
        householdSavingsSpan.setText(filteredKpis.totalHouseholdSavings().toString());

        unrealizedValueSpan.removeAll();
        unrealizedValueSpan.add(new com.vaadin.flow.component.Text(filteredKpis.unrealizedValue().toString()));
        Span btcAmountSpan = new Span(" (" + FormatUtil.formatBitcoin(filteredKpis.unsoldBtc()) + ")");
        btcAmountSpan.addClassName("btc-amount-span");
        unrealizedValueSpan.add(btcAmountSpan);
    }

    private StreamResource createCsvResource() {
        return new StreamResource("finance_data.csv", () -> {
            LocalDate from = datePickerFrom.getValue() != null ? datePickerFrom.getValue() : pvSiteEntity.getSetupDate();
            LocalDate to = datePickerTo.getValue() != null ? datePickerTo.getValue() : LocalDate.now(userSessionContext.getZoneId());
            return taxReportService.generateCsvReport(pvSiteEntity, from, to, userSessionContext);
        });
    }

    private StreamResource createMiningPdfResource() {
        return new StreamResource("mining_tax_report.pdf", () -> {
            LocalDate from = datePickerFrom.getValue() != null ? datePickerFrom.getValue() : pvSiteEntity.getSetupDate();
            LocalDate to = datePickerTo.getValue() != null ? datePickerTo.getValue() : LocalDate.now(userSessionContext.getZoneId());
            return taxReportService.generateMiningPdfReport(pvSiteEntity, from, to, userSessionContext);
        });
    }

    private StreamResource createPvPdfResource() {
        return new StreamResource("pv_roi_report.pdf", () -> {
            LocalDate from = datePickerFrom.getValue() != null ? datePickerFrom.getValue() : pvSiteEntity.getSetupDate();
            LocalDate to = datePickerTo.getValue() != null ? datePickerTo.getValue() : LocalDate.now(userSessionContext.getZoneId());
            return taxReportService.generatePvPdfReport(pvSiteEntity, from, to, userSessionContext);
        });
    }

    private StreamResource createSalesPdfResource() {
        return new StreamResource("crypto_sales_fifo_report.pdf", () -> {
            return taxReportService.generateSalesPdfReport(pvSiteEntity, userSessionContext);
        });
    }

    @Override
    public void localeChange(LocaleChangeEvent event) { updateLabelsAndTexts(); refresh(); }
    @Override
    public void onCurrencyChange(CurrencyChangeEvent event) { updateLabelsAndTexts(); refresh(); }
    @Override
    public void onTimeZoneChange(TimeZoneChangeEvent event) { refresh(); }
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String parameter = event.getRouteParameters().get("siteId").orElseThrow();
        try {
            UUID siteUuid = UUID.fromString(parameter);
            connectPVSiteEntity(pVSiteRepository.findById(siteUuid).orElseThrow());
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }
}