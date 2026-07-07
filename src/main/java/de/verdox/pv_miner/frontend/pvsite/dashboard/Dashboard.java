package de.verdox.pv_miner.frontend.pvsite.dashboard;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.charts.model.*;
import com.vaadin.flow.component.charts.model.style.SolidColor;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.Lumo;
import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.dashboard.DashboardFacadeService;
import de.verdox.pv_miner.frontend.pvsite.dashboard.dto.MinerDashboardItemDTO;
import de.verdox.pv_miner.miningcontroller.MinerLock;
import de.verdox.pv_miner.entity.EntityMonitoringService;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.globalconstants.GlobalConstantsService;
import de.verdox.pv_miner.lightning.LightningWalletService;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.miningcontroller.MinerClusterService;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import de.verdox.pv_miner.statistic.live.EntityStatisticsService;
import de.verdox.pv_miner.frontend.user.UserSessionContext;
import de.verdox.pv_miner.util.FormatUtil;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableButton;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableH3;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableSpan;
import de.verdox.pv_miner.frontend.AppMainLayout;
import de.verdox.pv_miner.frontend.FrontendColor;
import de.verdox.pv_miner.frontend.LightningWalletView;
import de.verdox.pv_miner.frontend.components.InfluxChart;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.Disposable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Route(value = "site/:siteId/dashboard", layout = AppMainLayout.class)
@CssImport("./themes/solarminer/dashboard.css")
public class Dashboard extends VerticalLayout implements BeforeEnterObserver, LocaleChangeObserver, HasDynamicTitle {
    private final PVSiteRepository pVSiteRepository;
    private final LightningWalletService walletService;
    private final UserSessionContext sessionContext;
    private final MinerClusterService clusterService;
    private final EntityQueryService entityQueryService;
    private final GlobalConstantsService globalConstantsService;
    private final DashboardFacadeService dashboardFacadeService;
    private final DashboardKpiHeader dashboardKpiHeader;
    private final DailyStatisticsWidget dailyStatisticsWidget = new DailyStatisticsWidget();

    private Disposable liveDataSubscription;
    private PVSiteEntity pvSiteEntity;

    private final KpiCard batterySocCard = new KpiCard("dashboard.kpi.battery_soc", FrontendColor.TEXT_VALUE_YELLOW, VaadinIcon.INPUT);
    private final KpiCard batteryPowerCard = new KpiCard("dashboard.kpi.battery_power", FrontendColor.TEXT_VALUE_WHITE, VaadinIcon.GRID);

    private final InfluxChart liveChart = new InfluxChart();
    private final InfluxChart historyChart = new InfluxChart();
    private final ControllerDashboardChart controllerDashboardChart;

    private final MinerGrid minerGrid = new MinerGrid();
    private final Grid<PoolItem> poolGrid = new Grid<>();

    private Span walletBalanceSpan;
    private VerticalLayout clusterListLayout;

    @Autowired
    public Dashboard(PVSiteRepository pVSiteRepository, LightningWalletService walletService, UserSessionContext sessionContext, MinerClusterService clusterService, EntityQueryService entityQueryService, GlobalConstantsService globalConstantsService, DashboardFacadeService dashboardFacadeService) {
        this.pVSiteRepository = pVSiteRepository;
        this.walletService = walletService;
        this.sessionContext = sessionContext;
        this.clusterService = clusterService;
        this.entityQueryService = entityQueryService;
        this.globalConstantsService = globalConstantsService;
        this.dashboardFacadeService = dashboardFacadeService;
        this.controllerDashboardChart =  new ControllerDashboardChart(clusterService);

        liveChart.applyDarkTheme();
        liveChart.getRangeSelector().addButton(new RangeSelectorButton(RangeSelectorTimespan.MINUTE, 60, "1h"));
        liveChart.getRangeSelector().addButton(new RangeSelectorButton(RangeSelectorTimespan.MINUTE, 60 * 12, "12h"));
        liveChart.getRangeSelector().setSelected(0);

        historyChart.applyDarkTheme();

        getElement().setAttribute("theme", Lumo.DARK);
        setSizeFull();
        setHeight("1200px");
        setPadding(true);
        setSpacing(true);
        getStyle().set("background-color", "#0f0f11");

        HorizontalLayout header = new DashboardHeader();

        dashboardKpiHeader = new DashboardKpiHeader();

        HorizontalLayout contentSplit = new HorizontalLayout();
        contentSplit.addClassName("dashboard-content-split");

        VerticalLayout leftLayout = createLeftLayout();
        leftLayout.addClassName("dashboard-left-panel");

        VerticalLayout rightLayout = createRightLayout();
        rightLayout.addClassName("dashboard-right-panel");

        contentSplit.add(leftLayout, rightLayout);
        add(header, dashboardKpiHeader, contentSplit);

    }

    @Override
    public String getPageTitle() {
        return getTranslation("dashboard.page.title");
    }

    private void setup() {
        EntityMonitoringService monitoringService = SpringContextHelper.getBean(EntityMonitoringService.class);
        EntityStatisticsService statisticsService = SpringContextHelper.getBean(EntityStatisticsService.class);

        var zoneId = sessionContext.getZoneId();

        long startTodayMilli = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli();
        long endTodayMilli = LocalDate.now(zoneId).atTime(LocalTime.of(23, 59, 59, 999)).atZone(zoneId).toInstant().toEpochMilli();

        UI ui = UI.getCurrent();

        var pvPowerFuture = CompletableFuture.supplyAsync(() -> statisticsService.loadStatistic(statisticsService.PV_POWER_DAY_STATISTIC, pvSiteEntity, startTodayMilli, endTodayMilli, false));
        var importFuture = CompletableFuture.supplyAsync(() -> statisticsService.loadStatistic(statisticsService.PV_IMPORT, pvSiteEntity, startTodayMilli, endTodayMilli, false));
        var exportFuture = CompletableFuture.supplyAsync(() -> statisticsService.loadStatistic(statisticsService.PV_GRID_EXPORT, pvSiteEntity, startTodayMilli, endTodayMilli, false));
        var consumptionFuture = CompletableFuture.supplyAsync(() -> statisticsService.loadStatistic(statisticsService.CONSUMPTION, pvSiteEntity, startTodayMilli, endTodayMilli, false));
        var minerConsumptionFuture = CompletableFuture.supplyAsync(() -> statisticsService.loadStatistic(statisticsService.MINER_CONSUMPTION, pvSiteEntity, startTodayMilli, endTodayMilli, false));
        var historyFuture = CompletableFuture.supplyAsync(() -> statisticsService.loadStatistic(statisticsService.PV_POWER_PER_HOUR_STATISTIC, pvSiteEntity, startTodayMilli, endTodayMilli, false));

        CompletableFuture.allOf(pvPowerFuture, importFuture, exportFuture, consumptionFuture, minerConsumptionFuture)
                .thenAccept(v -> {
                    ui.access(() -> {
                        if (liveChart.getConfiguration().getSeries().isEmpty()) {
                            liveChart.createStatisticSeries(getTranslation("dashboard.chart.pv_power"), pvPowerFuture.join());
                            liveChart.createStatisticSeries(getTranslation("dashboard.chart.import"), importFuture.join());
                            liveChart.createStatisticSeries(getTranslation("dashboard.chart.export"), exportFuture.join());
                            liveChart.createStatisticSeries(getTranslation("dashboard.chart.consumption"), consumptionFuture.join());
                            liveChart.createStatisticSeries(getTranslation("dashboard.chart.miner_consumption"), minerConsumptionFuture.join());
                        }
                        liveChart.applyDarkTheme();
                        liveChart.setWidth("98.5%");
                    });
                });
        historyFuture.thenAccept(pvPowerHistory -> {
            ui.access(() -> {
                historyChart.getConfiguration().setSeries(new ArrayList<>());
                historyChart.createStatisticSeries(getTranslation("dashboard.chart.pv_power_history"), pvPowerHistory);
                historyChart.drawChart(true);
                historyChart.applyDarkTheme();
                historyChart.setWidth("98.5%");
            });
        });

        if (liveDataSubscription != null) {
            liveDataSubscription.dispose();
        }

        dashboardFacadeService.subscribeToLiveUpdates(pvSiteEntity, sessionContext).subscribe(liveDashboardUpdateDto -> {
            dashboardKpiHeader.update(ui, liveDashboardUpdateDto.kpi());
            dailyStatisticsWidget.update(ui, liveDashboardUpdateDto.financials());
        });

        liveDataSubscription = monitoringService.hookIntoLiveData(pvSiteEntity)
                .subscribe(pvSiteData -> {
                    ui.access(() -> {
                        batterySocCard.setValue(pvSiteData.getBatterySoC() + " %");

                        if (pvSiteData.getBatteryPower() > 0) {
                            batteryPowerCard.setValue("+" + FormatUtil.formatNumber(pvSiteData.getBatteryPower()) + " kW");
                        } else {
                            batteryPowerCard.setValue(FormatUtil.formatNumber(pvSiteData.getBatteryPower()) + " kW");
                        }
                        updateWidgetsLive();
                    });
                });

        updateWidgetsLive();
    }

    private void updateWidgetsLive() {
        if (walletBalanceSpan != null) {
            walletBalanceSpan.setText(convertSatsToUserCurrencyString(walletService.getBalanceSat()));
        }
        refreshClusterList();
        updateMinerGridLive();
        updatePoolGridLive();
        controllerDashboardChart.update(UI.getCurrent(), pvSiteEntity);
    }

    private static @NonNull FlagItem createFlagForSnapshot(MinerClusterService.ClusterInstance.ClusterStateSnapshot snapshot, long time) {
        FlagItem flag = new FlagItem(time, "!");
        flag.setX(time);
        String desc = snapshot.eventDescription().toLowerCase();

        if (desc.contains("start")) {
            flag.setTitle("▶ Start");
            flag.setColor(new SolidColor("rgba(46, 204, 113, 0.8)"));
            flag.setColor(new SolidColor("#2ecc71"));
        } else if (desc.contains("stop") || desc.contains("drop")) {
            flag.setTitle("⏸ Stop");
            flag.setColor(new SolidColor("rgba(231, 76, 60, 0.8)"));
            flag.setColor(new SolidColor("#e74c3c"));
        } else {
            flag.setTitle("ℹ️ Event");
            flag.setColor(new SolidColor("rgba(52, 152, 219, 0.8)"));
        }

        flag.setText(snapshot.eventDescription() + " (Modus: " + snapshot.activeModeName() + ")");


        flag.setText(snapshot.eventDescription() + " (Mode: " + snapshot.activeModeName() + ")");
        return flag;
    }

    private void updateMinerGridLive() {
        if (pvSiteEntity == null) return;

        var clusterInstance = clusterService.getCluster(pvSiteEntity, "Standard");
        Map<UUID, MinerLock> locks = clusterInstance != null ? clusterInstance.getActiveLocks() : Map.of();

        List<MinerDashboardItemDTO> minerItems = pvSiteEntity.getMiners().stream().map(miner -> {
            MinerStats stats = entityQueryService.getLastResult(miner, MinerStats.DEFAULT);
            MinerLock lock = locks.get(miner.getId());

            long stateRemaining = 0;
            long powerRemaining = 0;
            long controllerPower = 0;

            if (lock != null) {
                Instant now = Instant.now();
                stateRemaining = Math.max(0, java.time.Duration.between(now, lock.runStateUnlockTime()).toSeconds());
                powerRemaining = Math.max(0, java.time.Duration.between(now, lock.powerChangeUnlockTime()).toSeconds());
                controllerPower = (long) lock.expectedPowerWatts();
            }


            return new MinerDashboardItemDTO(
                    stats.minerIdentity().minerModel(),
                    miner.getIP(),
                    stats.miningStatus() != null ? stats.miningStatus().name() : "UNKNOWN",
                    FormatUtil.formatHashrateFromTHs(stats.terahashPerSecond()),
                    FormatUtil.formatNumber(stats.approximatedPowerUsageWatts()) + " W",
                    FormatUtil.formatNumber(stats.temperatureCelsius()) + " °C",
                    "-",
                    stateRemaining,
                    powerRemaining,
                    controllerPower
            );
        }).toList();
        minerGrid.setItems(minerItems);
    }

    private void updatePoolGridLive() {
        if (pvSiteEntity == null) return;

        List<PoolItem> livePools = pvSiteEntity.getConnectedMiningPools().stream().map(pool -> {
            String url = pool.getStratumV1Url() != null ? pool.getStratumV1Url() : getTranslation("dashboard.grid.pool_unknown");
            return new PoolItem(url, "Active", getTranslation("dashboard.grid.pool_live_data_soon"));
        }).toList();

        poolGrid.setItems(livePools);
    }

    private VerticalLayout createLeftLayout() {
        VerticalLayout left = new VerticalLayout();
        left.setPadding(false);
        left.setSpacing(true);

        TranslatableH3 chartTitle = new TranslatableH3("dashboard.charts.title");
        chartTitle.getStyle().set("margin-top", "20px").set("margin-bottom", "10px");

        Tab liveTab = new Tab(new TranslatableSpan("dashboard.tab.live"));
        Tab historyTab = new Tab(new TranslatableSpan("dashboard.tab.history"));
        Tab automationTab = new Tab(new TranslatableSpan("dashboard.tab.automation"));
        Tabs chartTabs = new Tabs(liveTab, historyTab, automationTab);
        chartTabs.getStyle().set("margin-bottom", "10px");

        Div chartContainer = new Div(liveChart);
        chartContainer.setWidth("95%");

        chartTabs.addSelectedChangeListener(event -> {
            chartContainer.removeAll();
            if (event.getSelectedTab().equals(liveTab)) {
                chartContainer.add(liveChart);
            } else if (event.getSelectedTab().equals(historyTab)) {
                chartContainer.add(historyChart);
            } else {
                chartContainer.add(controllerDashboardChart);
            }
        });

        TranslatableH3 minerTitle = new TranslatableH3("dashboard.miner.title");
        minerTitle.getStyle().set("margin-top", "20px").set("margin-bottom", "10px");

        Div gridContainer = new Div(minerGrid);
        gridContainer.setWidthFull();
        gridContainer.getStyle()
                .set("max-height", "400px")
                .set("overflow-y", "auto")
                .set("border-radius", "4px")
                .set("border", "1px solid #222226");

        left.add(chartTitle, chartTabs, chartContainer, minerTitle, gridContainer);
        return left;
    }

    private Component createLightningWidget() {
        VerticalLayout card = createWidgetCard(new TranslatableH3("dashboard.lightning.title"), VaadinIcon.FLASH);

        walletBalanceSpan = new Span(getTranslation("dashboard.lightning.loading"));
        walletBalanceSpan.getStyle().set("font-size", "24px").set("font-weight", "bold").set("color", "#f1c40f");

        TranslatableButton withdrawBtn = new TranslatableButton("dashboard.lightning.withdraw", VaadinIcon.MONEY_WITHDRAW.create());
        withdrawBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        withdrawBtn.addClickListener(event -> UI.getCurrent().navigate(LightningWalletView.class));

        HorizontalLayout bottomRow = new HorizontalLayout(withdrawBtn);
        bottomRow.setWidthFull();
        bottomRow.setJustifyContentMode(JustifyContentMode.END);

        card.add(walletBalanceSpan, bottomRow);
        return card;
    }

    private Component createClusterWidget() {
        VerticalLayout card = createWidgetCard(new TranslatableH3("dashboard.controller.title"), VaadinIcon.CLUSTER);

        clusterListLayout = new VerticalLayout();
        clusterListLayout.setPadding(false);
        clusterListLayout.setSpacing(false);

        card.add(clusterListLayout);
        return card;
    }

    private void refreshClusterList() {
        if (clusterListLayout == null) return;
        clusterListLayout.removeAll();

        List<String> clusters = clusterService.getAvailableClusterNames();
        if (clusters.isEmpty()) {
            TranslatableSpan empty = new TranslatableSpan("dashboard.controller.empty");
            empty.getStyle().set("color", FrontendColor.TEXT_VALUE_GRAY);
            clusterListLayout.add(empty);
            return;
        }

        for (String clusterName : clusters) {
            MinerClusterService.ClusterInstance instance = clusterService.getCluster(pvSiteEntity, clusterName);
            boolean isRunning = instance != null && instance.isRunning();

            HorizontalLayout row = new HorizontalLayout();
            row.setWidthFull();
            row.setAlignItems(Alignment.CENTER);
            row.setJustifyContentMode(JustifyContentMode.BETWEEN);
            row.getStyle().set("padding", "8px 0").set("border-bottom", "1px solid #222226");

            Span name = new Span(clusterName);
            name.getStyle().set("font-weight", "bold");

            Component badge = new StatusBadge(isRunning ? "Running" : "Stopped");

            row.add(name, badge);
            clusterListLayout.add(row);
        }
    }

    private VerticalLayout createWidgetCard(Component titleComponent, VaadinIcon icon) {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(true);
        layout.setWidth("50%");
        layout.getStyle()
                .set("background-color", FrontendColor.CARD_BACKGROUND_COLOR)
                .set("border", "1px solid #222226")
                .set("border-radius", "8px");

        HorizontalLayout header = new HorizontalLayout(icon.create(), titleComponent);
        header.setAlignItems(Alignment.CENTER);
        header.getComponentAt(1).getStyle().set("margin", "0").set("font-size", "16px");
        header.getComponentAt(0).getStyle().set("color", FrontendColor.TEXT_VALUE_GRAY);

        layout.add(header);
        return layout;
    }

    private String convertSatsToUserCurrencyString(long sats) {
        double btc = sats / 100000000.0;
        CustomCurrency userCurrency = sessionContext != null && sessionContext.getCurrency() != null
                ? sessionContext.getCurrency()
                : CustomCurrency.getInstance("EUR");

        double rate = globalConstantsService.getExchangeRate(CustomCurrency.getInstance("BTC"), userCurrency);
        if (rate <= 0.0) {
            return "-1";
        }

        double convertedValue = btc * rate;
        return String.format("%,d sats (%s%,.2f)", sats, userCurrency.getSymbol(sessionContext != null ? sessionContext.getLocale() : null), convertedValue);
    }

    private VerticalLayout createRightLayout() {
        VerticalLayout right = new VerticalLayout();
        right.setPadding(false);
        right.setSpacing(true);

        HorizontalLayout walletAndController = new HorizontalLayout();
        Component lightningWidget = createLightningWidget();
        Component clusterWidget = createClusterWidget();
        walletAndController.add(lightningWidget, clusterWidget);
        walletAndController.setWidthFull();

        TranslatableH3 liveTitle = new TranslatableH3("dashboard.live.title");
        liveTitle.getStyle().set("margin-top", "10px");

        VerticalLayout metricsContainer = new VerticalLayout();
        metricsContainer.setPadding(false);
        metricsContainer.setSpacing(true);

        HorizontalLayout physicalRow1 = new HorizontalLayout();
        physicalRow1.setWidthFull();

        HorizontalLayout financialRow1 = new HorizontalLayout();
        financialRow1.setWidthFull();
        financialRow1.getStyle().set("flex-wrap", "wrap");
        financialRow1.add(batterySocCard, batteryPowerCard);

        metricsContainer.add(physicalRow1, financialRow1);

        TranslatableH3 poolTitle = new TranslatableH3("dashboard.pool.title");
        poolTitle.getStyle().set("margin-top", "20px").set("margin-bottom", "10px");

        poolGrid.addColumn(PoolItem::url).setHeader(new TranslatableSpan("dashboard.grid.pool.url")).setAutoWidth(true);
        poolGrid.addColumn(PoolItem::hashrate).setHeader(new TranslatableSpan("dashboard.grid.pool.hashrate"));
        poolGrid.addColumn(new ComponentRenderer<>(pool -> new StatusBadge(pool.status()))).setHeader(new TranslatableSpan("dashboard.grid.pool.status"));

        styleGrid(poolGrid);
        poolGrid.setHeight("180px");

        TranslatableH3 hwTitle = new TranslatableH3("dashboard.stats.title");
        hwTitle.getStyle().set("margin-top", "20px").set("font-size", "16px");

        right.add(liveTitle, metricsContainer, hwTitle, dailyStatisticsWidget, walletAndController, poolTitle, poolGrid);
        return right;
    }

    private void styleGrid(Grid<?> grid) {
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.getStyle().set("background-color", "transparent").set("border", "none");
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        minerGrid.getDataProvider().refreshAll();
        poolGrid.getDataProvider().refreshAll();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (liveDataSubscription != null) {
            liveDataSubscription.dispose();
        }
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String parameter = event.getRouteParameters().get("siteId").orElseThrow();
        try {
            UUID siteUuid = UUID.fromString(parameter);
            this.pvSiteEntity = pVSiteRepository.findById(siteUuid).orElseThrow();
            setup();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    public record PoolItem(String url, String status, String hashrate) {
    }
}