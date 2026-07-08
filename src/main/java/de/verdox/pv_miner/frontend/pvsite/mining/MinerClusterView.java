package de.verdox.pv_miner.frontend.pvsite.mining;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.Lumo;
import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.entity.EntityControllerService;
import de.verdox.pv_miner.entity.EntityMonitoringService;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.entity.EntityService;
import de.verdox.pv_miner.frontend.AppMainLayout;
import de.verdox.pv_miner.frontend.FrontendColor;
import de.verdox.pv_miner.frontend.FrontendService;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableButton;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableH2;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableH3;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableSpan;
import de.verdox.pv_miner.frontend.user.UserSessionContext;
import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.miningcontroller.MinerClusterService;
import de.verdox.pv_miner.miningpool.MiningPoolEntity;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteRef;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import de.verdox.pv_miner.util.FormatUtil;
import de.verdox.pv_miner.util.Money;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Route(value = "site/:siteId/clusters", layout = AppMainLayout.class)
@CssImport("./themes/solarminer/miner-cluster.css")
public class MinerClusterView extends VerticalLayout implements BeforeEnterObserver, LocaleChangeObserver, HasDynamicTitle {

    private final PVSiteRepository pvSiteRepository;
    private final MinerClusterService clusterService;
    private final EntityQueryService entityQueryService;
    private final EntityService entityService;
    private final MinerApiClient minerApiClient;
    private final EntityControllerService entityControllerService;
    private final UserSessionContext sessionContext;

    private PVSiteRef pvSiteReference;
    private String selectedClusterName;

    private Disposable liveDataSubscription;

    private final KpiCard totalClustersCard = new KpiCard("cluster.kpi.active", FrontendColor.TEXT_VALUE_WHITE, VaadinIcon.CLUSTER);
    private final KpiCard totalMinersCard = new KpiCard("cluster.kpi.miners", FrontendColor.TEXT_VALUE_WHITE, VaadinIcon.SERVER);
    private final KpiCard totalHashrateCard = new KpiCard("cluster.kpi.hashrate", FrontendColor.TEXT_VALUE_YELLOW, VaadinIcon.DASHBOARD);

    private final Grid<ClusterItem> clusterGrid = new Grid<>();
    private final Grid<MinerEntity<?>> minerGrid = new Grid<>();

    private final TranslatableButton btnStartCluster = new TranslatableButton("cluster.btn.start", VaadinIcon.PLAY.create());
    private final TranslatableButton btnStopCluster = new TranslatableButton("cluster.btn.stop", VaadinIcon.STOP.create());
    private final TranslatableButton btnConfigCluster = new TranslatableButton("cluster.btn.config", VaadinIcon.COG.create());
    private final TranslatableButton btnDeleteCluster = new TranslatableButton("cluster.btn.delete_cluster", VaadinIcon.TRASH.create());

    private final TranslatableButton btnShowDetails = new TranslatableButton("cluster.btn.show_details", VaadinIcon.PLUS.create());
    private final TranslatableButton btnAddMiner = new TranslatableButton("cluster.btn.add_miner", VaadinIcon.PLUS.create());
    private final TranslatableButton btnChangePool = new TranslatableButton("cluster.btn.change_pool", VaadinIcon.EXCHANGE.create());
    private final TranslatableButton btnTogglePower = new TranslatableButton("cluster.btn.toggle_power", VaadinIcon.POWER_OFF.create());
    private final TranslatableButton btnEditPowerTargets = new TranslatableButton("cluster.btn.power_targets", VaadinIcon.BOLT.create());

    // NEU: Button für die Power Locks
    private final TranslatableButton btnEditPowerLocks = new TranslatableButton("cluster.btn.power_locks", VaadinIcon.LOCK.create());

    private final TranslatableButton btnEditMinerCost = new TranslatableButton("cluster.btn.edit_cost", VaadinIcon.MONEY.create());
    private final TranslatableButton btnRemoveMiner = new TranslatableButton("cluster.btn.remove_miner", VaadinIcon.UNLINK.create());
    private final TranslatableButton btnDeleteSystemwide = new TranslatableButton("cluster.btn.delete_systemwide", VaadinIcon.TRASH.create());

    private final TranslatableH3 selectedClusterTitle = new TranslatableH3("cluster.title.selected_miners_fallback");

    @Autowired
    public MinerClusterView(PVSiteRepository pvSiteRepository, MinerClusterService clusterService, EntityQueryService entityQueryService, EntityService entityService, MinerApiClient minerApiClient, EntityControllerService entityControllerService, UserSessionContext sessionContext) {
        this.pvSiteRepository = pvSiteRepository;
        this.clusterService = clusterService;
        this.entityQueryService = entityQueryService;
        this.entityService = entityService;
        this.minerApiClient = minerApiClient;
        this.entityControllerService = entityControllerService;
        this.sessionContext = sessionContext;

        getElement().setAttribute("theme", Lumo.DARK);
        setSizeFull();
        addClassName("cluster-view");
        setPadding(false);
        setSpacing(false);

        add(createHeader());

        Div kpiRow = new Div(totalClustersCard, totalMinersCard, totalHashrateCard);
        kpiRow.addClassName("kpi-grid");
        kpiRow.getStyle().set("flex-wrap", "wrap");
        add(kpiRow);

        Div contentSplit = new Div();
        contentSplit.addClassName("cluster-content-split");

        Div leftLayout = createLeftLayout();
        leftLayout.addClassName("cluster-left-panel");

        Div rightLayout = createRightLayout();
        rightLayout.addClassName("cluster-right-panel");

        contentSplit.add(leftLayout, rightLayout);
        add(contentSplit);

        clusterGrid.addSelectionListener(e -> {
            if (e.getFirstSelectedItem().isPresent()) {
                ClusterItem selected = e.getFirstSelectedItem().get();
                this.selectedClusterName = selected.name();
                selectedClusterTitle.setText("cluster.title.selected_miners_param");
                selectedClusterTitle.setTranslationParameters(selected.name());
                loadMinersForCluster(selected.name());
                enableBulkActions(false);
                updateClusterControlButtons(selected.isRunning());
            } else {
                this.selectedClusterName = null;
                selectedClusterTitle.setText("cluster.title.selected_miners_fallback");
                btnStartCluster.setEnabled(false);
                btnStopCluster.setEnabled(false);
                btnConfigCluster.setEnabled(false);
                btnDeleteCluster.setEnabled(false);
                btnAddMiner.setEnabled(false);
            }
        });

        minerGrid.addSelectionListener(e -> {
            boolean hasSelection = !e.getAllSelectedItems().isEmpty();
            enableBulkActions(hasSelection);
            btnShowDetails.setEnabled(!e.getAllSelectedItems().isEmpty());
        });

        btnStartCluster.setEnabled(false);
        btnStopCluster.setEnabled(false);
        btnConfigCluster.setEnabled(false);
        btnDeleteCluster.setEnabled(false);
        btnAddMiner.setEnabled(false);
        enableBulkActions(false);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("cluster.page.title");
    }

    private HorizontalLayout createHeader() {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);

        TranslatableH2 title = new TranslatableH2("cluster.title.management");
        title.getStyle().set("margin", "0").set("color", FrontendColor.TEXT_VALUE_WHITE);

        TranslatableButton btnNewCluster = new TranslatableButton("cluster.btn.new_cluster", VaadinIcon.PLUS_CIRCLE.create());
        btnNewCluster.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        btnNewCluster.addClickListener(e -> {
            if (pvSiteReference != null) {
                UI.getCurrent().getPage().open("site/" + pvSiteReference.getId() + "/cluster-config/new", "_blank");
            }
        });

        TranslatableButton btnNewPool = new TranslatableButton("cluster.btn.new_pool", VaadinIcon.LINK.create());
        btnNewPool.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        btnNewPool.addClickListener(e -> {
            if (pvSiteReference != null) {
                UI.getCurrent().getPage().open("site/" + pvSiteReference.getId() + "/add-pool", "_blank");
            }
        });

        HorizontalLayout actions = new HorizontalLayout(btnNewPool, btnNewCluster);
        header.add(title, actions);
        return header;
    }

    private Div createLeftLayout() {
        Div left = new Div();
        left.addClassName("cluster-card");

        TranslatableH3 title = new TranslatableH3("cluster.title.available");
        title.getStyle().set("margin-top", "0").set("margin-bottom", "10px").set("color", FrontendColor.TEXT_VALUE_WHITE);

        clusterGrid.addColumn(ClusterItem::name).setHeader(new TranslatableSpan("cluster.grid.cluster.name")).setSortable(true);
        clusterGrid.addColumn(ClusterItem::minerCount).setHeader(new TranslatableSpan("cluster.grid.cluster.miners")).setAutoWidth(true);
        clusterGrid.addColumn(new ComponentRenderer<>(cluster -> createStatusBadge(cluster.status()))).setHeader(new TranslatableSpan("cluster.grid.cluster.status")).setAutoWidth(true);

        styleGrid(clusterGrid);
        clusterGrid.setHeight("400px");
        clusterGrid.addClassName("dynamic-height-grid");

        left.add(title, clusterGrid);
        return left;
    }

    private Div createRightLayout() {
        Div right = new Div();
        right.addClassName("cluster-card");

        right.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("height", "100%")
                .set("min-height", "600px");

        selectedClusterTitle.getStyle().set("margin", "0").set("color", FrontendColor.TEXT_VALUE_WHITE);

        btnStartCluster.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_PRIMARY);
        btnStartCluster.addClickListener(e -> handleStartCluster());

        btnStopCluster.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
        btnStopCluster.addClickListener(e -> handleStopCluster());

        btnConfigCluster.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        btnConfigCluster.addClickListener(e -> {
            if (pvSiteReference != null && selectedClusterName != null) {
                UI.getCurrent().getPage().open("site/" + pvSiteReference.getId() + "/cluster-config/" + selectedClusterName, "_blank");
            } else {
                Notification.show(getTranslation("cluster.notification.select_cluster_first"))
                        .addThemeVariants(NotificationVariant.LUMO_WARNING);
            }
        });

        btnDeleteCluster.addThemeVariants(ButtonVariant.LUMO_ERROR);
        btnDeleteCluster.addClickListener(e -> openDeleteClusterDialog());

        HorizontalLayout clusterControlRow = new HorizontalLayout(btnConfigCluster, btnStartCluster, btnStopCluster, btnDeleteCluster);
        clusterControlRow.getStyle().set("flex-wrap", "wrap");

        HorizontalLayout headerRow = new HorizontalLayout(selectedClusterTitle, clusterControlRow);
        headerRow.setWidthFull();
        headerRow.setJustifyContentMode(JustifyContentMode.BETWEEN);
        headerRow.setAlignItems(Alignment.CENTER);

        headerRow.getStyle().set("margin-bottom", "10px").set("flex-wrap", "wrap").set("flex-shrink", "0");

        Div actionToolbar = new Div();
        actionToolbar.addClassName("action-toolbar");
        actionToolbar.getStyle().set("flex-shrink", "0");

        btnAddMiner.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        btnAddMiner.addClickListener(e -> openAddMinerDialog());

        btnShowDetails.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        btnShowDetails.setEnabled(false);
        btnShowDetails.addClickListener(e -> {
            Set<MinerEntity<?>> selectedMiners = minerGrid.getSelectedItems();
            String idsParam = selectedMiners.stream()
                    .map(miner -> miner.getId().toString())
                    .collect(java.util.stream.Collectors.joining(","));
            UI.getCurrent().getPage().open("miner-details/" + pvSiteReference.getId() + "?miners=" + idsParam, "_blank");
        });

        btnChangePool.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        btnChangePool.addClickListener(e -> openChangePoolDialog());

        btnTogglePower.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        btnTogglePower.addClickListener(event -> handleTogglePower(minerGrid.getSelectedItems()));

        btnEditPowerTargets.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        btnEditPowerTargets.addClickListener(e -> openBulkPowerTargetDialog());

        // NEU: Listener für Power Locks
        btnEditPowerLocks.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        btnEditPowerLocks.addClickListener(e -> openBulkPowerLocksDialog());

        btnEditMinerCost.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        btnEditMinerCost.addClickListener(e -> openBulkMinerCostDialog());

        btnRemoveMiner.addThemeVariants(ButtonVariant.LUMO_ERROR);
        btnRemoveMiner.addClickListener(e -> handleBulkRemove());

        btnDeleteSystemwide.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
        btnDeleteSystemwide.addClickListener(e -> openDeleteSystemwideDialog());

        // NEU: btnEditPowerLocks zur Toolbar hinzugefügt
        actionToolbar.add(btnAddMiner, btnChangePool, btnTogglePower, btnEditPowerTargets, btnEditPowerLocks, btnEditMinerCost, btnRemoveMiner, btnDeleteSystemwide);

        minerGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        setupMinerGridColumns(minerGrid);
        styleGrid(minerGrid);

        minerGrid.setSizeFull();
        minerGrid.getStyle().set("flex-grow", "1");
        minerGrid.removeClassNames("dynamic-height-grid", "miner-grid-height");

        right.add(headerRow, actionToolbar, minerGrid);
        return right;
    }

    private void handleTogglePower(Set<MinerEntity<?>> selected) {
        if (selected.isEmpty()) return;
        try {
            PVSiteEntity freshSite = pvSiteRepository.findById(pvSiteReference.getId()).orElseThrow();
            for (MinerEntity<?> staleMiner : selected) {
                MinerEntity<?> freshMiner = freshSite.getMiners().stream()
                        .filter(m -> m.getId().equals(staleMiner.getId()))
                        .findFirst()
                        .orElse(null);

                if (freshMiner != null) {
                    var controller = entityControllerService.getController(freshMiner);
                    MinerStats minerData = entityQueryService.getLastResult(freshMiner, MinerStats.DEFAULT);

                    if (minerData != null && minerData.miningStatus() != null) {
                        boolean isRunning = minerData.miningStatus().equals(MinerStats.MinerStatus.MINING);
                        if (isRunning) {
                            minerApiClient.pauseMining(controller.os(), controller.details());
                        } else {
                            minerApiClient.resumeMining(controller.os(), controller.details());
                        }

                    } else {
                        throw new IllegalStateException("Could not fetch status of miner");
                    }
                }
            }
            Notification.show(getTranslation("cluster.notification.power_toggled", selected.size()))
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            minerGrid.deselectAll();
            refreshData();
        } catch (Exception ex) {
            Notification.show(getTranslation("cluster.notification.error", ex.getMessage()))
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void setupMinerGridColumns(Grid<MinerEntity<?>> grid) {
        grid.addColumn(new ComponentRenderer<>(miner -> {
            MinerStats stats = entityQueryService.getLastResult(miner, MinerStats.DEFAULT);

            Span nameSpan = new Span(stats.minerIdentity().minerModel());
            nameSpan.getStyle().set("font-weight", "bold").set("color", FrontendColor.TEXT_VALUE_WHITE);

            Span ipSpan = new Span(miner.getIP());
            ipSpan.getStyle().set("font-size", "12px").set("color", FrontendColor.TEXT_VALUE_GRAY);

            Span poolSpan = new Span(miner.getCurrentMiningPoolTarget() != null ? miner.getCurrentMiningPoolTarget() : "No Pool");
            poolSpan.getStyle()
                    .set("font-size", "10px")
                    .set("color", "#bdc3c7")
                    .set("background-color", "rgba(189, 195, 199, 0.1)")
                    .set("padding", "2px 6px")
                    .set("border-radius", "4px")
                    .set("margin-top", "2px");

            VerticalLayout layout = new VerticalLayout(nameSpan, ipSpan, poolSpan);
            layout.setPadding(false);
            layout.setSpacing(false);
            return layout;
        })).setHeader(new TranslatableSpan("cluster.grid.miner.name")).setSortable(true).setAutoWidth(true);

        grid.addColumn(new ComponentRenderer<>(miner -> {
            MinerStats stats = entityQueryService.getLastResult(miner, MinerStats.DEFAULT);
            Component badge = createStatusBadge(stats.miningStatus() != null ? stats.miningStatus().name() : "UNKNOWN");

            Span hashrateSpan = new Span(FormatUtil.formatHashrateFromTHs(stats.terahashPerSecond()));
            hashrateSpan.getStyle().set("font-size", "12px").set("color", FrontendColor.TEXT_VALUE_YELLOW);

            Span temperatureSpan = new Span(stats.temperatureCelsius() + " °C");
            if (stats.temperatureCelsius() >= 90) {
                temperatureSpan.getStyle().set("color", "#e74c3c");
            } else if (stats.temperatureCelsius() >= 80) {
                temperatureSpan.getStyle().set("color", "#f39c12");
            } else {
                temperatureSpan.getStyle().set("color", FrontendColor.TEXT_VALUE_GRAY);
            }
            temperatureSpan.getStyle().set("font-size", "12px").set("margin-left", "8px");

            HorizontalLayout metricsRow = new HorizontalLayout(hashrateSpan, temperatureSpan);
            metricsRow.setSpacing(false);
            metricsRow.setAlignItems(Alignment.CENTER);
            metricsRow.getStyle().set("margin-top", "4px");

            VerticalLayout layout = new VerticalLayout(badge, metricsRow);
            layout.setPadding(false);
            layout.setSpacing(false);
            layout.setAlignItems(Alignment.START);
            return layout;
        })).setHeader(new TranslatableSpan("cluster.grid.miner.status")).setAutoWidth(true);

        grid.addColumn(new ComponentRenderer<>(miner -> {
            MinerStats stats = entityQueryService.getLastResult(miner, MinerStats.DEFAULT);
            boolean supportsScaling = miner.getOS().supportsDynamicPowerScaling();

            Span powerSpan = new Span(stats.approximatedPowerUsageWatts() + " W");
            powerSpan.getStyle()
                    .set("font-weight", "bold")
                    .set("font-size", "16px")
                    .set("color", FrontendColor.TEXT_VALUE_WHITE);

            String hwLimits = (stats.minPowerTarget() == stats.maxPowerTarget())
                    ? stats.minPowerTarget() + "W"
                    : stats.minPowerTarget() + " - " + stats.maxPowerTarget() + "W";

            String customLimits = "-";
            if (supportsScaling) {
                customLimits = (miner.getMinPowerTarget() == miner.getMaxPowerTarget())
                        ? miner.getMinPowerTarget() + "W"
                        : miner.getMinPowerTarget() + " - " + miner.getMaxPowerTarget() + "W";
            } else {
                customLimits = stats.maxPowerTarget() + "W (Fix)";
            }

            Span hwSpan = new Span("HW: " + hwLimits);
            hwSpan.getStyle().set("font-size", "11px").set("color", FrontendColor.TEXT_VALUE_GRAY);

            Span customSpan = new Span("Target: " + customLimits);
            customSpan.getStyle().set("font-size", "11px").set("color", FrontendColor.TEXT_VALUE_YELLOW);

            VerticalLayout layout = new VerticalLayout(powerSpan, hwSpan, customSpan);
            layout.setPadding(false);
            layout.setSpacing(false);
            return layout;
        })).setHeader(new TranslatableSpan("cluster.grid.miner.power")).setAutoWidth(true).setFlexGrow(2);

        grid.addColumn(new ComponentRenderer<>(miner -> {
            HorizontalLayout layout = new HorizontalLayout();
            layout.setSpacing(true);
            layout.setAlignItems(Alignment.CENTER);

            java.util.function.BiConsumer<VaadinIcon, String> addIcon = (iconType, value) -> {
                Icon icon = iconType.create();
                icon.setSize("12px");
                icon.getStyle().set("color", FrontendColor.TEXT_VALUE_GRAY).set("margin-right", "4px");

                Span valSpan = new Span(value != null ? value : "-");
                valSpan.getStyle().set("font-size", "12px").set("color", FrontendColor.TEXT_VALUE_WHITE);

                HorizontalLayout pair = new HorizontalLayout(icon, valSpan);
                pair.setSpacing(false);
                pair.setAlignItems(Alignment.CENTER);
                pair.getElement().setProperty("title", "Override active");
                layout.add(pair);
            };

            if (miner.getPowerStepSizeWatts() != null) addIcon.accept(VaadinIcon.BOLT, miner.getPowerStepSizeWatts() + "W");
            if (miner.getMinRunTimeMinutes() != null) addIcon.accept(VaadinIcon.PLAY, miner.getMinRunTimeMinutes() + "m");
            if (miner.getMinIdleTimeMinutes() != null) addIcon.accept(VaadinIcon.PAUSE, miner.getMinIdleTimeMinutes() + "m");

            // NEU: Anzeige für den Power Change Lock
            if (miner.getPowerChangeLockTimeMinutes() != null) addIcon.accept(VaadinIcon.LOCK, miner.getPowerChangeLockTimeMinutes() + "m");

            if (layout.getComponentCount() == 0) {
                Span defaultSpan = new Span(getTranslation("cluster.grid.default"));
                defaultSpan.getStyle().set("font-size", "12px").set("color", FrontendColor.TEXT_VALUE_GRAY);
                layout.add(defaultSpan);
            }

            return layout;
        })).setHeader(new TranslatableSpan("cluster.grid.miner.locks")).setAutoWidth(true);
    }

    // NEU: Dialog für die Power Locks
    private void openBulkPowerLocksDialog() {
        Set<MinerEntity<?>> selected = minerGrid.getSelectedItems();
        if (selected.isEmpty()) return;

        boolean allHaveSamePowerLock = true;
        Integer commonPowerLock = null;
        boolean isFirst = true;

        for (MinerEntity<?> miner : selected) {
            if (isFirst) {
                commonPowerLock = miner.getPowerChangeLockTimeMinutes();
                isFirst = false;
            } else {
                if (!java.util.Objects.equals(commonPowerLock, miner.getPowerChangeLockTimeMinutes())) {
                    allHaveSamePowerLock = false;
                }
            }
        }

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("cluster.dialog.power_locks.title", selected.size()));
        dialog.setWidth("400px");

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);

        NumberField powerLockField = new NumberField(getTranslation("cluster.dialog.power_locks.time_min"));
        powerLockField.setPlaceholder(getTranslation("cluster.grid.default"));
        powerLockField.setStepButtonsVisible(true);
        powerLockField.setWidthFull();
        if (allHaveSamePowerLock && commonPowerLock != null) {
            powerLockField.setValue(commonPowerLock.doubleValue());
        }

        Span infoSpan = new Span(getTranslation("cluster.dialog.power_locks.description"));
        infoSpan.getStyle().set("color", FrontendColor.TEXT_VALUE_GRAY).set("font-size", "12px");

        layout.add(infoSpan, powerLockField);
        dialog.add(layout);

        Button cancelBtn = new Button(getTranslation("btn.cancel"), e -> dialog.close());
        Button saveBtn = new Button(getTranslation("btn.save"), e -> {
            try {
                PVSiteEntity freshSite = pvSiteRepository.findById(pvSiteReference.getId()).orElseThrow();

                for (MinerEntity<?> staleMiner : selected) {
                    MinerEntity<?> freshMiner = freshSite.getMiners().stream()
                            .filter(m -> m.getId().equals(staleMiner.getId()))
                            .findFirst()
                            .orElse(null);

                    if (freshMiner != null) {
                        freshMiner.setPowerChangeLockTimeMinutes(powerLockField.getValue() != null ? powerLockField.getValue().intValue() : null);
                        entityService.save(freshMiner, freshSite);
                    }
                }

                Notification.show(getTranslation("cluster.notification.power_locks_updated", selected.size()))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                refreshData();
                dialog.close();
            } catch (Exception ex) {
                Notification.show(getTranslation("cluster.notification.error", ex.getMessage()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

    private void openBulkMinerCostDialog() {
        Set<MinerEntity<?>> selected = minerGrid.getSelectedItems();
        if (selected.isEmpty()) return;

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("cluster.dialog.edit_cost.title", selected.size()));
        dialog.setWidth("400px");

        CustomCurrency currentCurrency = sessionContext.getCurrency() != null ? sessionContext.getCurrency() : CustomCurrency.getInstance("EUR");

        NumberField costField = new NumberField(getTranslation("cluster.dialog.edit_cost.label", currentCurrency.getSymbol(sessionContext.getLocale())));
        costField.setWidthFull();
        costField.setPlaceholder("1500.00");

        VerticalLayout layout = new VerticalLayout(costField);
        layout.setPadding(false);
        dialog.add(layout);

        Button cancelBtn = new Button(getTranslation("btn.cancel"), e -> dialog.close());
        Button saveBtn = new Button(getTranslation("btn.save"), e -> {
            Double costVal = costField.getValue();

            if (costVal == null || costVal < 0) {
                Notification.show(getTranslation("cluster.notification.cost_error"))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                PVSiteEntity freshSite = pvSiteRepository.findById(pvSiteReference.getId()).orElseThrow();

                selected.forEach(staleMiner -> {
                    MinerEntity<?> freshMiner = freshSite.getMiners().stream()
                            .filter(m -> m.getId().equals(staleMiner.getId()))
                            .findFirst()
                            .orElse(null);

                    if (freshMiner != null) {
                        freshMiner.setMinerCost(new Money(costVal, currentCurrency));
                        entityService.save(freshMiner, freshSite);
                    }
                });

                Notification.show(getTranslation("cluster.notification.cost_updated", selected.size()))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                refreshData();
                dialog.close();
            } catch (Exception ex) {
                Notification.show(getTranslation("cluster.notification.error", ex.getMessage()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

    private void openChangePoolDialog() {
        Set<MinerEntity<?>> selected = minerGrid.getSelectedItems();
        if (selected.isEmpty()) return;
        var pvSite = pvSiteReference.read();

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("cluster.dialog.change_pool.title", selected.size()));

        ComboBox<MiningPoolEntity<?>> poolCombo = new ComboBox<>(getTranslation("cluster.dialog.change_pool.select"));
        poolCombo.setItems(pvSite.getConnectedMiningPools());
        poolCombo.setItemLabelGenerator(p -> p.getUrlIdentifier() != null ? p.getUrlIdentifier() : "Unknown Pool");
        poolCombo.setWidthFull();

        VerticalLayout layout = new VerticalLayout(poolCombo);
        layout.setPadding(false);
        dialog.add(layout);

        Button cancelBtn = new Button(getTranslation("btn.cancel"), e -> dialog.close());
        Button saveBtn = new Button(getTranslation("btn.save"), e -> {
            MiningPoolEntity<?> selectedPool = poolCombo.getValue();
            if (selectedPool == null) {
                Notification.show(getTranslation("cluster.notification.select_pool_error"))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            UI ui = UI.getCurrent();
            Button sourceButton = e.getSource();
            sourceButton.setEnabled(false);

            Notification infoNotification = Notification.show(getTranslation("cluster.notification.processing", selected.size()));
            infoNotification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);

            CompletableFuture.runAsync(() -> {
                try {
                    PVSiteEntity freshSite = pvSiteRepository.findById(pvSiteReference.getId()).orElseThrow();
                    int successCount = 0;

                    for (MinerEntity<?> staleMiner : selected) {
                        MinerEntity<?> freshMiner = freshSite.getMiners().stream()
                                .filter(m -> m.getId().equals(staleMiner.getId()))
                                .findFirst()
                                .orElse(null);

                        if (freshMiner != null) {
                            var controller = entityControllerService.getController(freshMiner);
                            var poolData = entityQueryService.getLastResult(selectedPool, null);

                            if (poolData == null) {
                                continue;
                            }

                            var minerData = entityQueryService.getLastResult(freshMiner, null);
                            if (minerData == null) {
                                throw new NullPointerException("Miner did not respond lately: " + freshMiner.getName());
                            }

                            minerApiClient.setMiningPoolTarget(controller.os(), controller.details(), selectedPool.getStratumV1Url(), poolData.getDefaultWorkerName(), minerData.minerIdentity());
                            freshMiner.setCurrentMiningPoolTarget(selectedPool.getUrlIdentifier());
                            entityService.save(freshMiner, freshSite);
                            successCount++;
                        }
                    }

                    final int finalSuccessCount = successCount;

                    ui.access(() -> {
                        infoNotification.close();
                        Notification.show(getTranslation("cluster.notification.pool_changed", finalSuccessCount))
                                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                        minerGrid.deselectAll();
                        refreshData();
                        dialog.close();
                    });

                } catch (Exception ex) {
                    ui.access(() -> {
                        infoNotification.close();
                        sourceButton.setEnabled(true);
                        Notification.show(getTranslation("cluster.notification.error", ex.getMessage()))
                                .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    });
                }
            });
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

    private void openDeleteSystemwideDialog() {
        Set<MinerEntity<?>> selected = minerGrid.getSelectedItems();
        if (selected.isEmpty()) return;

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("cluster.dialog.delete_systemwide.title", selected.size()));

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.add(new Span(getTranslation("cluster.dialog.delete_systemwide.msg1", selected.size())));
        layout.add(new Span(getTranslation("cluster.dialog.delete_systemwide.msg2")));
        dialog.add(layout);

        Button cancelBtn = new Button(getTranslation("btn.cancel"), e -> dialog.close());
        Button confirmBtn = new Button(getTranslation("btn.delete_permanent"), e -> {
            try {
                var pvSite = pvSiteReference.read();
                for (MinerEntity<?> staleMiner : selected) {
                    PVSiteEntity freshSite = pvSiteRepository.findById(pvSiteReference.getId()).orElseThrow();
                    MinerEntity<?> freshMiner = freshSite.getMiners().stream()
                            .filter(m -> m.getId().equals(staleMiner.getId()))
                            .findFirst()
                            .orElse(null);

                    if (freshMiner != null) {
                        pvSite.getMiners().remove(freshMiner);
                        clusterService.logoutFromCluster(pvSiteReference.getId(), freshMiner);
                        entityService.delete(freshMiner);
                    }
                }

                Notification.show(getTranslation("cluster.notification.deleted", selected.size()))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                minerGrid.deselectAll();
                refreshData();
                dialog.close();
            } catch (Exception ex) {
                Notification.show(getTranslation("cluster.notification.error", ex.getMessage()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        confirmBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(cancelBtn, confirmBtn);
        dialog.open();
    }

    private void openDeleteClusterDialog() {
        if (selectedClusterName == null) return;

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("cluster.dialog.delete_cluster.title"));

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.add(new Span(getTranslation("cluster.dialog.delete_cluster.msg1", selectedClusterName)));
        layout.add(new Span(getTranslation("cluster.dialog.delete_cluster.msg2")));
        dialog.add(layout);

        Button cancelBtn = new Button(getTranslation("btn.cancel"), e -> dialog.close());
        Button confirmBtn = new Button(getTranslation("btn.delete_permanent"), e -> {
            try {
                clusterService.deleteCluster(selectedClusterName);
                Notification.show(getTranslation("cluster.notification.cluster_deleted", selectedClusterName))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                this.selectedClusterName = null;
                refreshData();
                dialog.close();
            } catch (Exception ex) {
                Notification.show(getTranslation("cluster.notification.error", ex.getMessage()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        confirmBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(cancelBtn, confirmBtn);
        dialog.open();
    }

    private void openBulkPowerTargetDialog() {
        Set<MinerEntity<?>> selected = minerGrid.getSelectedItems();
        if (selected.isEmpty()) return;

        boolean isSingleSelection = selected.size() == 1;

        boolean allSupportScaling = true;
        boolean allHaveSameLimits = true;
        boolean allHaveSameCustomTargets = true;

        Long commonHwMin = null;
        Long commonHwMax = null;
        Long commonHwDef = null;
        Long commonCustomMin = null;
        Long commonCustomMax = null;

        Integer commonStepSize = null;
        Integer commonMinRun = null;
        Integer commonMinIdle = null;
        boolean sameStepSize = true, sameMinRun = true, sameMinIdle = true;

        boolean isFirst = true;

        for (MinerEntity<?> miner : selected) {
            MinerStats stats = entityQueryService.getLastResult(miner, MinerStats.DEFAULT);

            if (!miner.getOS().supportsDynamicPowerScaling()) {
                allSupportScaling = false;
            }

            long hwMin = stats.minPowerTarget();
            long hwMax = stats.maxPowerTarget();
            long hwDef = stats.defaultPowerTarget() > 0 ? stats.defaultPowerTarget() : hwMax;

            long customMin = miner.getMinPowerTarget() > 0 ? miner.getMinPowerTarget() : hwMin;
            long customMax = miner.getMaxPowerTarget() > 0 ? miner.getMaxPowerTarget() : hwMax;

            if (isFirst) {
                commonHwMin = hwMin;
                commonHwMax = hwMax;
                commonHwDef = hwDef;

                commonCustomMin = customMin;
                commonCustomMax = customMax;

                commonStepSize = miner.getPowerStepSizeWatts();
                commonMinRun = miner.getMinRunTimeMinutes();
                commonMinIdle = miner.getMinIdleTimeMinutes();

                isFirst = false;
            } else {
                if (commonHwMin != hwMin || commonHwMax != hwMax || commonHwDef != hwDef) {
                    allHaveSameLimits = false;
                }
                if (commonCustomMin != customMin || commonCustomMax != customMax) {
                    allHaveSameCustomTargets = false;
                }
                if (!java.util.Objects.equals(commonStepSize, miner.getPowerStepSizeWatts())) sameStepSize = false;
                if (!java.util.Objects.equals(commonMinRun, miner.getMinRunTimeMinutes())) sameMinRun = false;
                if (!java.util.Objects.equals(commonMinIdle, miner.getMinIdleTimeMinutes())) sameMinIdle = false;
            }
        }

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation(isSingleSelection ? "cluster.dialog.power_targets.title_single" : "cluster.dialog.power_targets.title_bulk", selected.size()));
        dialog.setWidth("500px");

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        NumberField minPowerField = new NumberField(getTranslation("cluster.dialog.power_targets.min"));
        NumberField maxPowerField = new NumberField(getTranslation("cluster.dialog.power_targets.max"));

        boolean canEditPower = allSupportScaling && allHaveSameLimits;

        if (canEditPower) {
            minPowerField.setWidthFull();
            minPowerField.setMin(commonHwMin);
            minPowerField.setMax(commonHwMax);
            if (allHaveSameCustomTargets && commonCustomMin != null) minPowerField.setValue(commonCustomMin.doubleValue());
            minPowerField.setStepButtonsVisible(true);

            maxPowerField.setWidthFull();
            maxPowerField.setMin(commonHwMin);
            maxPowerField.setMax(commonHwMax);
            if (allHaveSameCustomTargets && commonCustomMax != null) maxPowerField.setValue(commonCustomMax.doubleValue());
            maxPowerField.setStepButtonsVisible(true);

            final long finalHwDef = commonHwDef;
            maxPowerField.addValueChangeListener(e -> {
                if (e.getValue() != null && e.getValue() > finalHwDef) {
                    maxPowerField.getStyle().set("--vaadin-input-field-border-color", "#e74c3c");
                    maxPowerField.setHelperText(getTranslation("cluster.dialog.power_targets.overclocking_warning"));
                } else {
                    maxPowerField.getStyle().remove("--vaadin-input-field-border-color");
                    maxPowerField.setHelperText("");
                }
                if (e.getValue() != null && minPowerField.getValue() != null && e.getValue() < minPowerField.getValue()) {
                    minPowerField.setValue(e.getValue());
                }
            });

            minPowerField.addValueChangeListener(e -> {
                if (e.getValue() != null && maxPowerField.getValue() != null && e.getValue() > maxPowerField.getValue()) {
                    maxPowerField.setValue(e.getValue());
                }
            });

            layout.add(new TranslatableH3("cluster.dialog.power_targets.section_power"));
            layout.add(new HorizontalLayout(minPowerField, maxPowerField));
        } else {
            Span warning = new Span();
            if (!allSupportScaling) {
                warning.setText(getTranslation("cluster.dialog.power_targets.no_scaling"));
                warning.getStyle().set("color", FrontendColor.TEXT_VALUE_YELLOW);
            } else {
                warning.setText(getTranslation("cluster.dialog.power_targets.bulk_warning"));
                warning.getStyle().set("color", FrontendColor.TEXT_VALUE_GRAY).set("font-size", "12px");
            }
            layout.add(warning);
        }

        layout.add(new TranslatableH3("cluster.dialog.power_targets.section_locks"));

        NumberField stepSizeField = new NumberField(getTranslation("cluster.dialog.power_targets.step_size"));
        stepSizeField.setPlaceholder(getTranslation("cluster.grid.default"));
        stepSizeField.setStepButtonsVisible(true);
        if (sameStepSize && commonStepSize != null) stepSizeField.setValue(commonStepSize.doubleValue());

        NumberField minRunTimeField = new NumberField(getTranslation("cluster.dialog.power_targets.run_time_min"));
        minRunTimeField.setPlaceholder(getTranslation("cluster.grid.default"));
        minRunTimeField.setStepButtonsVisible(true);
        if (sameMinRun && commonMinRun != null) minRunTimeField.setValue(commonMinRun.doubleValue());

        NumberField minIdleTimeField = new NumberField(getTranslation("cluster.dialog.power_targets.idle_time_min"));
        minIdleTimeField.setPlaceholder(getTranslation("cluster.grid.default"));
        minIdleTimeField.setStepButtonsVisible(true);
        if (sameMinIdle && commonMinIdle != null) minIdleTimeField.setValue(commonMinIdle.doubleValue());

        HorizontalLayout locksRow = allSupportScaling ? new HorizontalLayout(stepSizeField, minRunTimeField, minIdleTimeField) : new HorizontalLayout(minRunTimeField, minIdleTimeField);
        locksRow.setWidthFull();
        layout.add(locksRow);

        dialog.add(layout);

        Button cancelBtn = new Button(getTranslation("btn.cancel"), e -> dialog.close());
        Button saveBtn = new Button(getTranslation("btn.save"), e -> {
            try {
                PVSiteEntity freshSite = pvSiteRepository.findById(pvSiteReference.getId()).orElseThrow();

                for (MinerEntity<?> staleMiner : selected) {
                    MinerEntity<?> freshMiner = freshSite.getMiners().stream()
                            .filter(m -> m.getId().equals(staleMiner.getId()))
                            .findFirst()
                            .orElse(null);

                    if (freshMiner != null) {
                        if (canEditPower) {
                            if (minPowerField.getValue() != null) freshMiner.setMinPowerTarget(minPowerField.getValue().intValue());
                            if (maxPowerField.getValue() != null) freshMiner.setMaxPowerTarget(maxPowerField.getValue().intValue());
                        }

                        freshMiner.setPowerStepSizeWatts(stepSizeField.getValue() != null ? stepSizeField.getValue().intValue() : null);
                        freshMiner.setMinRunTimeMinutes(minRunTimeField.getValue() != null ? minRunTimeField.getValue().intValue() : null);
                        freshMiner.setMinIdleTimeMinutes(minIdleTimeField.getValue() != null ? minIdleTimeField.getValue().intValue() : null);

                        entityService.save(freshMiner, freshSite);
                    }
                }

                Notification.show(getTranslation("cluster.notification.targets_updated", selected.size()))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                refreshData();
                dialog.close();
            } catch (Exception ex) {
                Notification.show(getTranslation("cluster.notification.error", ex.getMessage()))
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

    private void openAddMinerDialog() {
        if (selectedClusterName == null || pvSiteReference == null) return;

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("cluster.dialog.add_miner.title", selectedClusterName));
        dialog.setWidth("80vw");
        dialog.setHeight("80vh");
        dialog.setResizable(true);

        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);

        HorizontalLayout searchAndAddRow = new HorizontalLayout();
        searchAndAddRow.setWidthFull();
        searchAndAddRow.setAlignItems(Alignment.BASELINE);

        TextField searchField = new TextField();
        searchField.setPlaceholder(getTranslation("cluster.dialog.add_miner.search"));
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setWidthFull();
        searchField.setClearButtonVisible(true);

        Button btnConnectNewMiners = new Button(getTranslation("cluster.dialog.add_miner.connect_new"), VaadinIcon.PLUS_CIRCLE.create());
        btnConnectNewMiners.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_PRIMARY);
        btnConnectNewMiners.addClickListener(e -> {
            UI.getCurrent().getPage().open("site/" + pvSiteReference.getId() + "/add-miner", "_blank");
        });

        searchAndAddRow.add(searchField, btnConnectNewMiners);

        Grid<MinerEntity<?>> unassignedGrid = new Grid<>();
        unassignedGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        setupMinerGridColumns(unassignedGrid);
        styleGrid(unassignedGrid);

        Set<MinerEntity<?>> unassignedMiners = clusterService.getUnassignedMiners(pvSiteReference.read());
        ListDataProvider<MinerEntity<?>> dataProvider = new ListDataProvider<>(new ArrayList<>(unassignedMiners));
        unassignedGrid.setDataProvider(dataProvider);

        searchField.setValueChangeMode(ValueChangeMode.EAGER);
        searchField.addValueChangeListener(e -> {
            dataProvider.setFilter(miner -> {
                if (miner.getClusterName() != null && miner.getClusterName().equals(selectedClusterName)) {
                    return false;
                }
                String searchTerm = e.getValue().trim().toLowerCase();
                if (searchTerm.isEmpty()) return true;
                String name = miner.getName() != null ? miner.getName().toLowerCase() : "";
                String ip = miner.getIP() != null ? miner.getIP().toLowerCase() : "";
                return name.contains(searchTerm) || ip.contains(searchTerm);
            });
        });

        layout.add(searchAndAddRow, unassignedGrid);
        dialog.add(layout);

        Button cancelBtn = new Button(getTranslation("btn.cancel"), e -> dialog.close());
        Button addBtn = new Button(getTranslation("cluster.dialog.add_miner.add_selected", 0), e -> {
            Set<MinerEntity<?>> selected = unassignedGrid.getSelectedItems();
            if (selected.isEmpty()) {
                Notification.show(getTranslation("cluster.notification.select_miners")).addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }

            try {
                MinerClusterService.ClusterInstance cluster = clusterService.getCluster(pvSiteReference.getId(), selectedClusterName);
                cluster.assignMiners(selected);

                Notification.show(getTranslation("cluster.notification.miners_added", selected.size()))
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                refreshData();
                dialog.close();
            } catch (Exception ex) {
                Notification.show(getTranslation("cluster.notification.error", ex.getMessage())).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        unassignedGrid.addSelectionListener(e -> {
            addBtn.setText(getTranslation("cluster.dialog.add_miner.add_selected", e.getAllSelectedItems().size()));
        });

        dialog.getFooter().add(cancelBtn, addBtn);
        dialog.open();
    }

    private void handleStartCluster() {
        if (selectedClusterName == null || pvSiteReference == null) return;

        MinerClusterService.ClusterInstance cluster = clusterService.getCluster(pvSiteReference.getId(), selectedClusterName);
        if (cluster != null && cluster.getAssignedMiners().isEmpty()) {
            Notification.show(getTranslation("cluster.notification.start_error_empty"))
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        try {
            clusterService.startCluster(selectedClusterName, pvSiteReference);
            Notification.show(getTranslation("cluster.notification.started", selectedClusterName)).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refreshData();
        } catch (Exception ex) {
            Notification.show(getTranslation("cluster.notification.error", ex.getMessage()), 5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void handleStopCluster() {
        if (selectedClusterName == null) return;
        try {
            clusterService.stopCluster(pvSiteReference.getId(), selectedClusterName);
            Notification.show(getTranslation("cluster.notification.stopped", selectedClusterName)).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refreshData();
        } catch (Exception ex) {
            Notification.show(getTranslation("cluster.notification.error", ex.getMessage()), 5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void updateClusterControlButtons(boolean isRunning) {
        btnStartCluster.setEnabled(!isRunning);
        btnStopCluster.setEnabled(isRunning);
        btnConfigCluster.setEnabled(true);
        btnDeleteCluster.setEnabled(true);
        btnAddMiner.setEnabled(true);
    }

    private void handleBulkRemove() {
        Set<MinerEntity<?>> selected = minerGrid.getSelectedItems();
        if (selected.isEmpty() || selectedClusterName == null) return;

        try {
            MinerClusterService.ClusterInstance cluster = clusterService.getCluster(pvSiteReference.getId(), selectedClusterName);
            cluster.removeMiners(selected);
            Notification.show(getTranslation("cluster.notification.removed", selected.size())).addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            minerGrid.deselectAll();
            refreshData();
        } catch (Exception e) {
            Notification.show(getTranslation("cluster.notification.error", e.getMessage()), 5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void enableBulkActions(boolean enabled) {
        btnChangePool.setEnabled(enabled);
        btnTogglePower.setEnabled(enabled);
        btnEditPowerTargets.setEnabled(enabled);
        btnEditPowerLocks.setEnabled(enabled);
        btnEditMinerCost.setEnabled(enabled);
        btnRemoveMiner.setEnabled(enabled);
        btnDeleteSystemwide.setEnabled(enabled);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String parameter = event.getRouteParameters().get("siteId").orElseThrow();
        try {
            UUID siteUuid = UUID.fromString(parameter);
            this.pvSiteReference = entityService.pvSiteRef(siteUuid);
            refreshData();
            setupLiveUpdates();
        } catch (IllegalArgumentException e) {
            Notification.show(getTranslation("error.invalid_site"), 3000, Notification.Position.MIDDLE);
        }
    }

    private void setupLiveUpdates() {
        if (liveDataSubscription != null) {
            liveDataSubscription.dispose();
        }
        EntityMonitoringService monitoringService = SpringContextHelper.getBean(EntityMonitoringService.class);

        liveDataSubscription = monitoringService.hookIntoLiveData(pvSiteReference.read()).subscribe(pvSiteData -> {
            FrontendService.scheduleUpdate(getUI(), activeUi -> {
                refreshData();
            });
        });
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (liveDataSubscription != null) {
            liveDataSubscription.dispose();
        }
        super.onDetach(detachEvent);
    }

    private void refreshData() {
        String currentSelectedCluster = this.selectedClusterName;
        Set<UUID> currentSelectedMiners = minerGrid.getSelectedItems().stream()
                .map(MinerEntity::getId)
                .collect(Collectors.toSet());

        List<String> clusterNames = clusterService.getAvailableClusterNames();

        List<ClusterItem> items = clusterNames.stream().map(name -> {
            var instance = clusterService.getCluster(pvSiteReference.getId(), name);
            int count = instance != null ? instance.getAssignedMiners().size() : 0;
            boolean isRunning = instance != null && instance.isRunning();
            return new ClusterItem(name, count, isRunning ? "Running" : "Stopped", isRunning);
        }).collect(Collectors.toList());

        clusterGrid.setItems(items);

        long activeCount = items.stream().filter(ClusterItem::isRunning).count();
        int totalMiners = items.stream().mapToInt(ClusterItem::minerCount).sum();

        double totalHashrate = 0.0;
        for (String name : clusterNames) {
            var instance = clusterService.getCluster(pvSiteReference.getId(), name);
            if (instance != null) {
                for (MinerEntity<?> miner : instance.getAssignedMiners()) {
                    MinerStats stats = entityQueryService.getLastResult(miner, MinerStats.DEFAULT);
                    if (stats != null) {
                        totalHashrate += stats.terahashPerSecond();
                    }
                }
            }
        }

        totalClustersCard.setValue(activeCount + " / " + items.size());
        totalMinersCard.setValue(String.valueOf(totalMiners));
        totalHashrateCard.setValue(FormatUtil.formatHashrateFromTHs(totalHashrate));

        if (currentSelectedCluster != null) {
            items.stream()
                    .filter(c -> c.name().equals(currentSelectedCluster))
                    .findFirst()
                    .ifPresentOrElse(c -> {
                        clusterGrid.select(c);

                        ListDataProvider<MinerEntity<?>> dp = (ListDataProvider<MinerEntity<?>>) minerGrid.getDataProvider();
                        if (dp != null && !currentSelectedMiners.isEmpty()) {
                            Set<MinerEntity<?>> toSelect = dp.getItems().stream()
                                    .filter(m -> currentSelectedMiners.contains(m.getId()))
                                    .collect(Collectors.toSet());
                            if (!toSelect.isEmpty()) {
                                minerGrid.asMultiSelect().setValue(toSelect);
                            }
                        }
                    }, () -> {
                        if (!items.isEmpty()) {
                            clusterGrid.select(items.get(0));
                        } else {
                            minerGrid.setItems(new ArrayList<>());
                        }
                    });
        } else if (!items.isEmpty()) {
            clusterGrid.select(items.get(0));
        }
    }

    private void loadMinersForCluster(String clusterName) {
        MinerClusterService.ClusterInstance instance = clusterService.getCluster(pvSiteReference.getId(), clusterName);
        if (instance != null) {
            List<MinerEntity<?>> sortedMiners = instance.getAssignedMiners().stream()
                    .sorted(java.util.Comparator.comparing(
                            (MinerEntity<?> m) -> m.getName() != null ? m.getName() : (m.getIP() != null ? m.getIP() : ""),
                            String.CASE_INSENSITIVE_ORDER
                    ))
                    .toList();

            minerGrid.setItems(sortedMiners);
        } else {
            minerGrid.setItems(new ArrayList<>());
        }
    }

    private void styleGrid(Grid<?> grid) {
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.getStyle().set("background-color", "transparent").set("border", "none");
    }

    private Component createStatusBadge(String status) {
        Span badge = new Span(getTranslation("status." + status.toLowerCase()));
        badge.getStyle()
                .set("padding", "3px 8px")
                .set("border-radius", "12px")
                .set("font-size", "12px")
                .set("font-weight", "bold");

        if (status.equalsIgnoreCase("Online") || status.equalsIgnoreCase("Active") || status.equalsIgnoreCase("Running") || status.equalsIgnoreCase("MINING")) {
            badge.getStyle().set("background-color", "rgba(46, 204, 113, 0.2)").set("color", "#2ecc71");
        } else if (status.equalsIgnoreCase("Offline") || status.equalsIgnoreCase("Stopped") || status.equalsIgnoreCase("PAUSED") || status.equalsIgnoreCase("ERROR")) {
            badge.getStyle().set("background-color", "rgba(231, 76, 60, 0.2)").set("color", "#e74c3c");
        } else {
            badge.getStyle().set("background-color", "rgba(241, 196, 15, 0.2)").set("color", FrontendColor.TEXT_VALUE_YELLOW);
        }
        return badge;
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        clusterGrid.getDataProvider().refreshAll();
        minerGrid.getDataProvider().refreshAll();
    }

    private static class KpiCard extends HorizontalLayout {
        private final Span value = new Span();
        private final TranslatableSpan title;

        public KpiCard(String titleKey, String valueColor, VaadinIcon cardIcon) {
            setPadding(true);
            setSpacing(false);
            setAlignItems(Alignment.CENTER);
            setJustifyContentMode(JustifyContentMode.BETWEEN);
            getStyle()
                    .set("background-color", FrontendColor.CARD_BACKGROUND_COLOR)
                    .set("border-radius", "8px")
                    .set("border", "1px solid #222226")
                    .set("flex", "1");

            title = new TranslatableSpan(titleKey);
            title.getStyle().set("color", FrontendColor.TEXT_VALUE_GRAY).set("font-size", "12px");
            value.getStyle().set("color", valueColor).set("font-size", "20px").set("font-weight", "bold");

            VerticalLayout textLayout = new VerticalLayout(title, value);
            textLayout.setPadding(false);
            textLayout.setSpacing(false);

            Icon icon = cardIcon.create();
            icon.getStyle().set("color", valueColor);
            icon.setSize("35px");
            add(textLayout, icon);
        }

        public void setValue(String val) {
            this.value.setText(val);
        }
    }

    public record ClusterItem(String name, int minerCount, String status, boolean isRunning) {
    }
}