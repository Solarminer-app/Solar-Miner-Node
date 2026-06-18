package de.verdox.pv_miner.frontend.pvsite.mining;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.Lumo;
import de.verdox.pv_miner.miningcontroller.MinerControllerConfig;
import de.verdox.pv_miner.miningcontroller.MinerControllerConfigStorage;
import de.verdox.pv_miner.miningcontroller.dsl.ControllerDSL;
import de.verdox.pv_miner.miningcontroller.dsl.DSLBuilder;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableButton;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableH2;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableH3;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableSpan;
import de.verdox.pv_miner.frontend.FrontendColor;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

@Route(value = "site/:siteId/cluster-config/:configId")
public class MinerClusterConfigView extends VerticalLayout implements BeforeEnterObserver, LocaleChangeObserver, HasDynamicTitle {

    private final MinerControllerConfigStorage configStorage;
    private final PVSiteRepository pvSiteRepository;

    private PVSiteEntity pvSiteEntity;
    private String configId;
    private boolean isNewConfig;

    private final Map<String, ControllerDSL.OperatingMode> editingModesState = new LinkedHashMap<>();

    private final TextField configNameField = new TextField();
    private final TranslatableH2 configTitle = new TranslatableH2("cluster.config.title.new");
    private final Grid<ControllerDSL.OperatingMode> modeGrid = new Grid<>();
    private final TranslatableButton btnSave = new TranslatableButton("btn.save", VaadinIcon.CHECK.create());
    private final TranslatableButton btnClose = new TranslatableButton("btn.close", VaadinIcon.CLOSE.create());

    @Autowired
    public MinerClusterConfigView(MinerControllerConfigStorage configStorage, PVSiteRepository pvSiteRepository) {
        this.configStorage = configStorage;
        this.pvSiteRepository = pvSiteRepository;

        getElement().setAttribute("theme", Lumo.DARK);
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        getStyle().set("background-color", "#0f0f11");

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.getStyle().set("border-bottom", "1px solid #222226").set("padding-bottom", "var(--lumo-space-m)");

        HorizontalLayout titleLayout = new HorizontalLayout();
        titleLayout.setAlignItems(Alignment.CENTER);

        configTitle.getStyle().set("margin", "0").set("color", FrontendColor.TEXT_VALUE_WHITE);
        configNameField.setWidth("300px");
        configNameField.setVisible(false);

        titleLayout.add(VaadinIcon.COGS.create(), configTitle, configNameField);
        titleLayout.getComponentAt(0).getStyle().set("color", FrontendColor.TEXT_VALUE_GRAY).set("font-size", "24px");

        btnSave.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        btnSave.addClickListener(e -> saveConfig());

        btnClose.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        btnClose.addClickListener(e -> UI.getCurrent().getPage().executeJs("window.close();"));

        HorizontalLayout actionsLayout = new HorizontalLayout(btnClose, btnSave);
        header.add(titleLayout, actionsLayout);

        Div card = new Div();
        card.setWidth("97%");
        card.setHeightFull();
        card.getStyle()
                .set("display", "flex").set("flex-direction", "column")
                .set("background-color", FrontendColor.CARD_BACKGROUND_COLOR)
                .set("border", "1px solid #222226").set("border-radius", "8px")
                .set("padding", "var(--lumo-space-l)");

        HorizontalLayout gridHeader = new HorizontalLayout();
        gridHeader.setWidthFull();
        gridHeader.setJustifyContentMode(JustifyContentMode.BETWEEN);
        gridHeader.setAlignItems(Alignment.CENTER);

        TranslatableH3 rulesTitle = new TranslatableH3("cluster.config.rules.title");
        rulesTitle.getStyle().set("margin-top", "0").set("color", FrontendColor.TEXT_VALUE_WHITE);

        TranslatableButton btnAddMode = new TranslatableButton("cluster.config.btn.add_mode", VaadinIcon.PLUS_CIRCLE.create(), e -> {
            openOperatingModeDialog(null, newMode -> {
                editingModesState.put(newMode.modeName(), newMode);
                refreshGrid();
            });
        });
        btnAddMode.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        gridHeader.add(rulesTitle, btnAddMode);

        setupGrid();

        card.add(gridHeader, modeGrid);
        add(header, card);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("cluster.config.page.title");
    }

    private void setupGrid() {
        modeGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        modeGrid.getStyle().set("background-color", "transparent");

        modeGrid.addColumn(ControllerDSL.OperatingMode::modeName).setHeader(new TranslatableSpan("cluster.config.grid.mode_name")).setSortable(true);

        modeGrid.addColumn(m -> {
            var act = getMainAction(m);
            return act != null ? getTranslation("enum.MinerDistributionStrategy." + act.strategy().name()) : "-";
        }).setHeader(new TranslatableSpan("cluster.config.grid.strategy"));

        modeGrid.addColumn(m -> {
            var act = getMainAction(m);
            return act != null ? act.stepSizeWatts() + " W" : "-";
        }).setHeader(new TranslatableSpan("cluster.config.grid.step_size"));

        modeGrid.addColumn(m -> getTranslation("cluster.config.grid.locks.format", m.minRunTime().toMinutes(), m.minIdleTime().toMinutes())).setHeader(new TranslatableSpan("cluster.config.grid.locks"));

        modeGrid.addComponentColumn(mode -> {
            HorizontalLayout actions = new HorizontalLayout();

            Button editBtn = new Button(VaadinIcon.EDIT.create(), e -> {
                openOperatingModeDialog(mode, updatedMode -> {
                    editingModesState.remove(mode.modeName());
                    editingModesState.put(updatedMode.modeName(), updatedMode);
                    refreshGrid();
                });
            });
            editBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

            Button deleteBtn = new Button(VaadinIcon.TRASH.create(), e -> {
                editingModesState.remove(mode.modeName());
                refreshGrid();
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

            actions.add(editBtn, deleteBtn);
            return actions;
        }).setHeader(new TranslatableSpan("cluster.config.grid.actions")).setAutoWidth(true).setFlexGrow(0);
    }

    private void refreshGrid() {
        modeGrid.setItems(editingModesState.values());
    }

    private void saveConfig() {
        String finalName;
        if (isNewConfig) {
            if (configNameField.isEmpty()) {
                Notification.show(getTranslation("cluster.config.notification.empty_name")).addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            finalName = configNameField.getValue().trim();
        } else {
            finalName = configId;
        }

        try {
            configStorage.save(finalName, new MinerControllerConfig(new LinkedHashMap<>(editingModesState)));
            Notification.show(getTranslation("cluster.config.notification.saved", finalName), 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            UI.getCurrent().getPage().executeJs("setTimeout(function() { window.close(); }, 1500);");
        } catch (IOException ex) {
            Notification.show(getTranslation("cluster.config.notification.error", ex.getMessage())).addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String siteParam = event.getRouteParameters().get("siteId").orElseThrow();
        this.configId = event.getRouteParameters().get("configId").orElseThrow();

        try {
            this.pvSiteEntity = pvSiteRepository.findById(UUID.fromString(siteParam)).orElseThrow();
            this.isNewConfig = configId.equalsIgnoreCase("new");

            if (isNewConfig) {
                configTitle.setText("cluster.config.title.new");
                configNameField.setVisible(true);
            } else {
                configTitle.setText("cluster.config.title.edit");
                configTitle.setTranslationParameters(configId);
                configNameField.setVisible(false);

                MinerControllerConfig config = configStorage.get(configId);
                editingModesState.clear();
                editingModesState.putAll(config.getConfigEntries());
                refreshGrid();
            }
        } catch (Exception e) {
            Notification.show(getTranslation("error.invalid_config"));
            e.printStackTrace();
        }
    }

    private void openOperatingModeDialog(ControllerDSL.OperatingMode existingMode, java.util.function.Consumer<ControllerDSL.OperatingMode> onSave) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(existingMode == null ? getTranslation("cluster.config.dialog.title.new") : getTranslation("cluster.config.dialog.title.edit", existingMode.modeName()));
        dialog.setWidth("1200px");
        dialog.getElement().getThemeList().add("dark");

        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.setPadding(false);
        dialogLayout.setSpacing(true);

        TextField nameField = new TextField(getTranslation("cluster.config.dialog.name"));
        nameField.setWidth("40%");
        NumberField minRunTime = new NumberField(getTranslation("cluster.config.dialog.min_run_time"));
        minRunTime.setWidth("30%");
        NumberField minIdleTime = new NumberField(getTranslation("cluster.config.dialog.min_idle_time"));
        minIdleTime.setWidth("30%");

        HorizontalLayout baseLayout = new HorizontalLayout(nameField, minRunTime, minIdleTime);
        baseLayout.setWidthFull();

        ConditionBuilder startConditionBuilder = new ConditionBuilder(existingMode != null ? existingMode.startCondition() : null);
        ConditionBuilder stopConditionBuilder = new ConditionBuilder(existingMode != null ? existingMode.stopCondition() : null);

        ComboBox<ControllerDSL.PVSiteVariableType> dynamicVarCombo = new ComboBox<>(getTranslation("cluster.config.dialog.sensor_target"));
        dynamicVarCombo.setItems(ControllerDSL.PVSiteVariableType.values());
        dynamicVarCombo.setItemLabelGenerator(v -> getTranslation("enum.PVSiteVariableType." + v.name()));
        dynamicVarCombo.setWidth("45%");

        NumberField multiplierField = new NumberField(getTranslation("cluster.config.dialog.multiplier"));
        multiplierField.setWidth("25%");

        NumberField stepSizeField = new NumberField(getTranslation("cluster.config.dialog.step_size_watt"));
        stepSizeField.setWidth("30%");

        HorizontalLayout actionLayout = new HorizontalLayout(dynamicVarCombo, multiplierField, stepSizeField);
        actionLayout.setWidthFull();

        ComboBox<ControllerDSL.ActionTargetType> targetCombo = new ComboBox<>(getTranslation("cluster.config.dialog.action_target"));
        targetCombo.setItems(ControllerDSL.ActionTargetType.values());
        targetCombo.setItemLabelGenerator(t -> getTranslation("enum.ActionTargetType." + t.name()));
        targetCombo.setWidthFull();

        ComboBox<ControllerDSL.MinerDistributionStrategy> strategyCombo = new ComboBox<>(getTranslation("cluster.config.dialog.distribution_strategy"));
        strategyCombo.setItems(ControllerDSL.MinerDistributionStrategy.values());
        strategyCombo.setItemLabelGenerator(s -> getTranslation("enum.MinerDistributionStrategy." + s.name()));
        strategyCombo.setWidthFull();

        VerticalLayout expertLayout = new VerticalLayout(targetCombo, strategyCombo);
        expertLayout.setPadding(false);
        Details expertDetails = new Details(getTranslation("cluster.config.dialog.advanced_settings"), expertLayout);

        dialogLayout.add(
                new H3(getTranslation("cluster.config.dialog.header.1")), baseLayout,
                new H3(getTranslation("cluster.config.dialog.header.2")),
                new Span(getTranslation("cluster.config.dialog.start_cond")), startConditionBuilder,
                new Span(getTranslation("cluster.config.dialog.stop_cond")), stopConditionBuilder,
                new H3(getTranslation("cluster.config.dialog.header.3")), actionLayout,
                expertDetails
        );
        dialog.add(dialogLayout);

        if (existingMode != null) {
            nameField.setValue(existingMode.modeName());
            minRunTime.setValue((double) existingMode.minRunTime().toMinutes());
            minIdleTime.setValue((double) existingMode.minIdleTime().toMinutes());

            var act = getMainAction(existingMode);
            if (act != null) {
                targetCombo.setValue(act.targetType());
                strategyCombo.setValue(act.strategy());
                stepSizeField.setValue((double) act.stepSizeWatts());

                if (act.valueExpression() instanceof ControllerDSL.ValueExpression.DynamicVariable dyn) {
                    dynamicVarCombo.setValue(dyn.variable());
                    multiplierField.setValue(dyn.multiplier());
                }
            }
        } else {
            minRunTime.setValue(15.0);
            minIdleTime.setValue(5.0);
            dynamicVarCombo.setValue(ControllerDSL.PVSiteVariableType.POTENTIAL_PV_SURPLUS);
            multiplierField.setValue(1.0);
            stepSizeField.setValue(100.0);
            targetCombo.setValue(ControllerDSL.ActionTargetType.CLUSTER_DYNAMIC);
            strategyCombo.setValue(ControllerDSL.MinerDistributionStrategy.SEQUENTIAL);
        }

        Button btnApply = new Button(getTranslation("btn.apply"), e -> {
            if (nameField.isEmpty()) {
                Notification.show(getTranslation("cluster.config.notification.empty_name"));
                return;
            }

            ControllerDSL.ValueExpression expr = new ControllerDSL.ValueExpression.DynamicVariable(
                    dynamicVarCombo.getValue(), DSLBuilder.Conditions.median(5), multiplierField.getValue(), 0.0
            );

            ControllerDSL.ControllerAction targetAction = new ControllerDSL.ControllerAction(
                    ControllerDSL.ControllerActionType.SET_POWER_TARGET,
                    targetCombo.getValue(), strategyCombo.getValue(), expr, stepSizeField.getValue().intValue()
            );

            ControllerDSL.ControllerAction resumeAction = new ControllerDSL.ControllerAction(
                    ControllerDSL.ControllerActionType.RESUME,
                    targetCombo.getValue(),
                    ControllerDSL.MinerDistributionStrategy.EQUAL_DISTRIBUTION,
                    new ControllerDSL.ValueExpression.Constant(1.0),
                    0
            );

            ControllerDSL.Condition startCond = startConditionBuilder.buildCondition();
            ControllerDSL.Condition stopCond = stopConditionBuilder.buildCondition();

            if (startCond == null || stopCond == null) {
                Notification.show(getTranslation("cluster.config.notification.empty_cond"));
                return;
            }

            ControllerDSL.OperatingMode mode = DSLBuilder.create(nameField.getValue())
                    .startWhen(startCond).stopWhen(stopCond)
                    .execute(resumeAction)
                    .execute(targetAction)
                    .withHardwareLocks(
                            Duration.ofMinutes(minRunTime.getValue().intValue()),
                            Duration.ofMinutes(minIdleTime.getValue().intValue())
                    ).build();

            onSave.accept(mode);
            dialog.close();
        });
        btnApply.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(new Button(getTranslation("btn.cancel"), cb -> dialog.close()), btnApply);
        dialog.open();
    }

    private ControllerDSL.ControllerAction getMainAction(ControllerDSL.OperatingMode mode) {
        if (mode.actions() == null || mode.actions().isEmpty()) return null;
        return mode.actions().stream()
                .filter(a -> a.controllerActionType() == ControllerDSL.ControllerActionType.SET_POWER_TARGET)
                .findFirst()
                .orElse(mode.actions().get(0));
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        configNameField.setPlaceholder(getTranslation("cluster.config.name.placeholder"));
        modeGrid.getDataProvider().refreshAll();
    }

    private class ConditionBuilder extends VerticalLayout {
        private final ComboBox<ControllerDSL.Condition.LogicalOperator> operatorCombo;
        private final VerticalLayout rowsLayout;
        private final List<PredicateRow> rows = new ArrayList<>();

        public ConditionBuilder(ControllerDSL.Condition existingCondition) {
            setPadding(false);
            setSpacing(false);
            setWidthFull();

            operatorCombo = new ComboBox<>();
            operatorCombo.setItems(ControllerDSL.Condition.LogicalOperator.AND, ControllerDSL.Condition.LogicalOperator.OR);
            operatorCombo.setItemLabelGenerator(o -> getTranslation("enum.LogicalOperator." + o.name()));
            operatorCombo.setValue(ControllerDSL.Condition.LogicalOperator.AND);
            operatorCombo.setWidthFull();
            operatorCombo.getStyle().set("margin-bottom", "10px");

            rowsLayout = new VerticalLayout();
            rowsLayout.setPadding(false);
            rowsLayout.setSpacing(true);

            TranslatableButton btnAdd = new TranslatableButton("cluster.config.dialog.add_condition", VaadinIcon.PLUS.create(), e -> addRow(null));
            btnAdd.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

            add(operatorCombo, rowsLayout, btnAdd);

            if (existingCondition instanceof ControllerDSL.Condition.LogicalCondition logCond) {
                operatorCombo.setValue(logCond.operator());
                for (ControllerDSL.Condition sub : logCond.subConditions()) {
                    if (sub instanceof ControllerDSL.Condition.Predicate pred) addRow(pred);
                }
            } else if (existingCondition instanceof ControllerDSL.Condition.Predicate pred) {
                addRow(pred);
            } else {
                addRow(null);
            }
        }

        private void addRow(ControllerDSL.Condition.Predicate pred) {
            PredicateRow row = new PredicateRow(pred, this::removeRow);
            rows.add(row);
            rowsLayout.add(row);
            updateOperatorVisibility();
        }

        private void removeRow(PredicateRow row) {
            if (rows.size() > 1) {
                rows.remove(row);
                rowsLayout.remove(row);
                updateOperatorVisibility();
            }
        }

        private void updateOperatorVisibility() {
            operatorCombo.setVisible(rows.size() > 1);
        }

        public ControllerDSL.Condition buildCondition() {
            if (rows.isEmpty()) return null;
            if (rows.size() == 1) return rows.get(0).buildPredicate();

            List<ControllerDSL.Condition> subConditions = rows.stream().map(row -> (ControllerDSL.Condition) row.buildPredicate()).toList();
            return new ControllerDSL.Condition.LogicalCondition(operatorCombo.getValue(), subConditions);
        }
    }

    private class PredicateRow extends HorizontalLayout {
        private final ComboBox<ControllerDSL.PVSiteVariableType> varCombo = new ComboBox<>();
        private final ComboBox<String> timeScopeCombo = new ComboBox<>();
        private final ComboBox<ControllerDSL.Comparator> compCombo = new ComboBox<>();
        private final NumberField valueField = new NumberField();

        public PredicateRow(ControllerDSL.Condition.Predicate existing, java.util.function.Consumer<PredicateRow> onDelete) {
            setWidthFull();
            setAlignItems(FlexComponent.Alignment.BASELINE);

            varCombo.setItems(ControllerDSL.PVSiteVariableType.values());
            varCombo.setItemLabelGenerator(v -> getTranslation("enum.PVSiteVariableType." + v.name()));
            varCombo.setWidth("35%");
            varCombo.setPlaceholder(getTranslation("cluster.config.dialog.sensor"));

            timeScopeCombo.setItems("LIVE", "MEDIAN (5m)", "MEDIAN (30m)");
            timeScopeCombo.setWidth("25%");
            timeScopeCombo.setPlaceholder(getTranslation("cluster.config.dialog.timescope"));

            compCombo.setItems(ControllerDSL.Comparator.values());
            compCombo.setItemLabelGenerator(c -> getTranslation("enum.Comparator." + c.name()));
            compCombo.setWidth("15%");

            valueField.setWidth("15%");
            valueField.setPlaceholder(getTranslation("cluster.config.dialog.value"));

            Button btnDelete = new Button(VaadinIcon.CLOSE.create(), e -> onDelete.accept(this));
            btnDelete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
            btnDelete.setWidth("10%");

            add(varCombo, timeScopeCombo, compCombo, valueField, btnDelete);

            if (existing != null) {
                varCombo.setValue(existing.pvSiteVariableType());
                compCombo.setValue(existing.comparator());
                valueField.setValue(existing.value());
                int time = existing.scope().timeValue();
                if (time == 30) timeScopeCombo.setValue("MEDIAN (30m)");
                else if (time == 5) timeScopeCombo.setValue("MEDIAN (5m)");
                else timeScopeCombo.setValue("LIVE");
            } else {
                varCombo.setValue(ControllerDSL.PVSiteVariableType.POTENTIAL_PV_SURPLUS_RATIO);
                timeScopeCombo.setValue("MEDIAN (5m)");
                compCombo.setValue(ControllerDSL.Comparator.GREATER_OR_EQUAL);
                valueField.setValue(0.15);
            }
        }

        public ControllerDSL.Condition buildPredicate() {
            String selectedTime = timeScopeCombo.getValue() != null ? timeScopeCombo.getValue() : "LIVE";
            ControllerDSL.ValueAdjustment scope;

            if (selectedTime.equals("MEDIAN (30m)")) scope = DSLBuilder.Conditions.median(30);
            else if (selectedTime.equals("MEDIAN (5m)")) scope = DSLBuilder.Conditions.median(5);
            else scope = DSLBuilder.Conditions.live();

            return new ControllerDSL.Condition.Predicate(
                    varCombo.getValue(), scope, compCombo.getValue(), valueField.getValue() != null ? valueField.getValue() : 0.0
            );
        }
    }
}