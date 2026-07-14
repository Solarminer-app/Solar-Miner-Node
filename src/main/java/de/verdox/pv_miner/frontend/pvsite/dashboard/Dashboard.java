package de.verdox.pv_miner.frontend.pvsite.dashboard;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.charts.model.FlagItem;
import com.vaadin.flow.component.charts.model.RangeSelectorButton;
import com.vaadin.flow.component.charts.model.RangeSelectorTimespan;
import com.vaadin.flow.component.charts.model.style.SolidColor;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
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
import de.verdox.pv_miner.entity.EntityMonitoringService;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.entity.EntityService;
import de.verdox.pv_miner.frontend.AppMainLayout;
import de.verdox.pv_miner.frontend.FrontendColor;
import de.verdox.pv_miner.frontend.LightningWalletView;
import de.verdox.pv_miner.frontend.components.InfluxChart;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableButton;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableH3;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableSpan;
import de.verdox.pv_miner.frontend.pvsite.dashboard.dto.MinerDashboardItemDTO;
import de.verdox.pv_miner.frontend.user.UserSessionContext;
import de.verdox.pv_miner.globalconstants.GlobalConstantsService;
import de.verdox.pv_miner.lightning.LightningWalletService;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.miningcontroller.MinerClusterService;
import de.verdox.pv_miner.miningcontroller.MinerLock;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteRef;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import de.verdox.pv_miner.statistic.live.EntityStatisticsService;
import de.verdox.pv_miner.util.FormatUtil;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.Disposable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Route(value = "site/:siteId/dashboard", layout = AppMainLayout.class)
@CssImport("./themes/solarminer/dashboard.css")
public class Dashboard extends VerticalLayout implements BeforeEnterObserver, LocaleChangeObserver, HasDynamicTitle {
    private final LightningWalletService walletService;
    private final UserSessionContext sessionContext;
    private final MinerClusterService clusterService;
    private final EntityQueryService entityQueryService;
    private final GlobalConstantsService globalConstantsService;
    private final DashboardFacadeService dashboardFacadeService;
    private final DashboardKpiHeader dashboardKpiHeader;
    private final DailyStatisticsWidget dailyStatisticsWidget = new DailyStatisticsWidget();
    private final EntityService entityService;

    private Disposable subscription;
    private Disposable liveDataSubscription;
    private PVSiteRef pvSiteReference;

    private final Select<String> clusterSelector = new Select<>();
    private String selectedClusterName = "Standard";

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
    public Dashboard(LightningWalletService walletService, UserSessionContext sessionContext, MinerClusterService clusterService, EntityQueryService entityQueryService, GlobalConstantsService globalConstantsService, DashboardFacadeService dashboardFacadeService, EntityService entityService) {
        this.walletService = walletService;
        this.sessionContext = sessionContext;
        this.clusterService = clusterService;
        this.entityQueryService = entityQueryService;
        this.globalConstantsService = globalConstantsService;
        this.dashboardFacadeService = dashboardFacadeService;
        this.controllerDashboardChart = new ControllerDashboardChart(clusterService);

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
        this.entityService = entityService;
    }

    @Override
    public String getPageTitle() {
        return getTranslation("dashboard.page.title");
    }

    private void setup(UI ui) {
        EntityMonitoringService monitoringService = SpringContextHelper.getBean(EntityMonitoringService.class);

        PVSiteEntity pvSiteEntity = pvSiteReference.read();
        var zoneId = sessionContext.getZoneId();

        long pvSiteStartMilli = pvSiteEntity.getSetupDate().atStartOfDay(zoneId).toInstant().toEpochMilli();
        long startTodayMilli = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli();
        long endTodayMilli = LocalDate.now(zoneId).atTime(LocalTime.of(23, 59, 59, 999)).atZone(zoneId).toInstant().toEpochMilli();

        var pvSite = pvSiteReference.read();
        dashboardFacadeService.loadChartData(pvSite, startTodayMilli, endTodayMilli, pvSiteStartMilli)
                .thenAccept(chartData -> {
                    ui.access(() -> {
                        if (liveChart.getConfiguration().getSeries().isEmpty()) {
                            liveChart.createStatisticSeries(getTranslation("dashboard.chart.pv_power"), chartData.pvPower());
                            liveChart.createStatisticSeries(getTranslation("dashboard.chart.import"), chartData.importData());
                            liveChart.createStatisticSeries(getTranslation("dashboard.chart.export"), chartData.exportData());
                            liveChart.createStatisticSeries(getTranslation("dashboard.chart.consumption"), chartData.consumption());
                            liveChart.createStatisticSeries(getTranslation("dashboard.chart.miner_consumption"), chartData.minerConsumption());
                        }
                        liveChart.applyDarkTheme();
                        liveChart.setWidth("98.5%");

                        historyChart.getConfiguration().setSeries(new ArrayList<>());
                        historyChart.createStatisticSeries(getTranslation("dashboard.chart.pv_power_history"), chartData.history());
                        historyChart.drawChart(true);
                        historyChart.applyDarkTheme();
                        historyChart.setWidth("98.5%");
                    });
                });

        if (liveDataSubscription != null) {
            liveDataSubscription.dispose();
        }
        if(subscription != null) {
            subscription.dispose();
        }

        subscription = dashboardFacadeService.subscribeToLiveUpdates(pvSite, sessionContext).subscribe(liveDashboardUpdateDto -> {
            dashboardKpiHeader.update(ui, liveDashboardUpdateDto.kpi());
            dailyStatisticsWidget.update(ui, liveDashboardUpdateDto.financials());
        });

        liveDataSubscription = monitoringService.hookIntoLiveData(pvSite)
                .subscribe(pvSiteData -> {
                    PVSiteEntity fresh = pvSiteReference.read();
                    ui.access(() -> {
                        batterySocCard.setValue(pvSiteData.getBatterySoC() + " %");

                        if (pvSiteData.getBatteryPower() > 0) {
                            batteryPowerCard.setValue("+" + FormatUtil.formatNumber(pvSiteData.getBatteryPower()) + " kW");
                        } else {
                            batteryPowerCard.setValue(FormatUtil.formatNumber(pvSiteData.getBatteryPower()) + " kW");
                        }
                        updateWidgetsLive(fresh);
                    });
                });

        ui.access(() -> {
            updateWidgetsLive(pvSiteEntity);
        });
    }

    private void updateWidgetsLive(PVSiteEntity pvSiteEntity) {
        if (walletBalanceSpan != null) {
            walletBalanceSpan.setText(convertSatsToUserCurrencyString(walletService.getBalanceSat()));
        }
        refreshClusterList(pvSiteEntity);
        updateMinerGridLive(pvSiteEntity);
        updatePoolGridLive(pvSiteEntity);
        controllerDashboardChart.update(pvSiteEntity, selectedClusterName);
    }

    private void updateMinerGridLive(PVSiteEntity pvSite) {
        if (pvSiteReference == null) return;

        var clusterInstance = clusterService.getCluster(pvSiteReference.getId(), selectedClusterName);
        Map<UUID, MinerLock> locks = clusterInstance != null ? clusterInstance.getActiveLocks() : Map.of();

        List<MinerDashboardItemDTO> minerItems = pvSite.getMiners().stream().map(miner -> {
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
        }).sorted(Comparator.comparing(MinerDashboardItemDTO::hashrate)).toList();
        minerGrid.setItems(minerItems);
    }

    private void updatePoolGridLive(PVSiteEntity pvSite) {
        if (pvSiteReference == null) return;

        List<PoolItem> livePools = pvSite.getConnectedMiningPools().stream().map(pool -> {
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
        Tabs chartTabs = new Tabs(liveTab, /*historyTab, */automationTab);

        clusterSelector.setItems(clusterService.getAvailableClusterNames());
        clusterSelector.setValue(selectedClusterName);
        clusterSelector.setVisible(false);

        clusterSelector.addValueChangeListener(event -> {
            if (event.getValue() != null) {
                selectedClusterName = event.getValue();
                if (pvSiteReference != null) {
                    var pvSite = pvSiteReference.read();
                    controllerDashboardChart.update(pvSite, selectedClusterName);
                    updateMinerGridLive(pvSite);
                }
            }
        });

        HorizontalLayout tabsAndSelector = new HorizontalLayout(chartTabs, clusterSelector);
        tabsAndSelector.setWidthFull();
        tabsAndSelector.setAlignItems(Alignment.BASELINE);
        tabsAndSelector.setJustifyContentMode(JustifyContentMode.BETWEEN);
        tabsAndSelector.getStyle().set("margin-bottom", "10px");

        Div chartContainer = new Div(liveChart);
        chartContainer.setWidth("95%");

        chartTabs.addSelectedChangeListener(event -> {
            chartContainer.removeAll();
            if (event.getSelectedTab().equals(liveTab)) {
                clusterSelector.setVisible(false);
                chartContainer.add(liveChart);
            } /*else if (event.getSelectedTab().equals(historyTab)) {
                clusterSelector.setVisible(false);
                chartContainer.add(historyChart);
            } */else {
                clusterSelector.setVisible(true);
                clusterSelector.setItems(clusterService.getAvailableClusterNames());
                if(clusterSelector.getValue() == null && !clusterService.getAvailableClusterNames().isEmpty()) {
                    clusterSelector.setValue(clusterService.getAvailableClusterNames().getFirst());
                }
                chartContainer.add(controllerDashboardChart);
                if (pvSiteReference != null) {
                    controllerDashboardChart.update(pvSiteReference.read(), selectedClusterName);
                }
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

        left.add(chartTitle, tabsAndSelector, chartContainer, minerTitle, gridContainer);
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

    private void refreshClusterList(PVSiteEntity pvSite) {
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
            MinerClusterService.ClusterInstance instance = clusterService.getCluster(pvSiteReference.getId(), clusterName);
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
        if(subscription != null) {
            subscription.dispose();
        }
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {

        String parameter = event.getRouteParameters().get("siteId").orElseThrow();
        try {
            UUID siteUuid = UUID.fromString(parameter);
            this.pvSiteReference = entityService.pvSiteRef(siteUuid);
            setup(event.getUI());
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    public record PoolItem(String url, String status, String hashrate) {
    }
}