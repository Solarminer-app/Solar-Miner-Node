package de.verdox.pv_miner.frontend.pvsite.details;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.grid.editor.Editor;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.Lumo;
import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.entity.EntityService;
import de.verdox.pv_miner.frontend.user.*;
import de.verdox.pv_miner.globalconstants.GlobalConstantsService;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.pvsite.HistoricalPrice;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import de.verdox.pv_miner.util.Money;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import de.verdox.pv_miner.pvsite.PVPanels;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableSpan;
import de.verdox.pv_miner.frontend.AppMainLayout;
import de.verdox.pv_miner.frontend.FrontendColor;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Route(value = "site/:siteId/details", layout = AppMainLayout.class)
@CssImport("./themes/solarminer/pvsite-details.css")
public class PVSiteDetailsSubPage extends VerticalLayout implements LocaleChangeObserver, TimeZoneChangeObserver, CurrencyChangeObserver, BeforeEnterObserver {

    private final UserSessionContext userSessionContext;
    private final PVSiteRepository pVSiteRepository;
    private final EntityService entityService;
    private PVSiteEntity pvSiteEntity;
    private PVPanels selectedPanels;

    private final Span totalPeakPowerSpan = new Span("0.00 kWp");
    private final Span totalPanelsCountSpan = new Span("0");
    private final Span groupsCountSpan = new Span("0");

    private final ComboBox<PVPanels> groupSelector = new ComboBox<>();
    private final TextField groupNameField = new TextField();

    private final NumberField latitudeField = new NumberField();
    private final NumberField longitudeField = new NumberField();
    private final Button selectLocationBtn = new Button(VaadinIcon.MAP_MARKER.create());

    private final IntegerField amountField = new IntegerField();
    private final NumberField powerPerPanelField = new NumberField();
    private final CompassField azimuthField = new CompassField();
    private final NumberField slopeField = new NumberField();

    private final Button savePanelsBtn = new Button(VaadinIcon.DISC.create());
    private final Button deletePanelsBtn = new Button(VaadinIcon.TRASH.create());
    private final Binder<PVPanels> panelBinder = new Binder<>(PVPanels.class);

    private final H3 formTitle = new H3();
    private final H3 forecastTitle = new H3();

    private final NumberField pvCostField = new NumberField();
    private final DatePicker setupDatePicker = new DatePicker();
    private final Button savePVConfigBtn = new Button();
    private final H3 pvInvestmentTitle = new H3();

    private final Grid<MinerEntity<?>> minerGrid = new Grid<>();
    private final H3 hardwareCostsTitle = new H3();

    private final Grid<HistoricalPrice> feedInGrid = new Grid<>();
    private final Grid<HistoricalPrice> electricityGrid = new Grid<>();

    private final DatePicker feedInDatePicker = new DatePicker();
    private final NumberField feedInPriceField = new NumberField();
    private final Button addFeedInBtn = new Button(VaadinIcon.PLUS.create());

    private final DatePicker electricityDatePicker = new DatePicker();
    private final NumberField electricityPriceField = new NumberField();
    private final Button addElectricityBtn = new Button(VaadinIcon.PLUS.create());

    public PVSiteDetailsSubPage(UserSessionContext userSessionContext, PVSiteRepository pVSiteRepository, EntityService entityService) {
        this.userSessionContext = userSessionContext;

        getElement().setAttribute("theme", Lumo.DARK);
        setSizeFull();
        addClassName("details-view");
        setPadding(false);

        Div mainLayout = new Div();
        mainLayout.addClassName("details-content-split");

        Div leftLayout = new Div();
        leftLayout.addClassName("details-left-panel");

        Div kpiLayout = new Div();
        kpiLayout.addClassName("mini-kpi-grid");
        kpiLayout.add(createMiniCard(new TranslatableSpan("pv.details.kpi.total_power"), totalPeakPowerSpan, VaadinIcon.FLASH, FrontendColor.TEXT_VALUE_YELLOW));
        kpiLayout.add(createMiniCard(new TranslatableSpan("pv.details.kpi.total_panels"), totalPanelsCountSpan, VaadinIcon.GRID_BIG_O, FrontendColor.TEXT_VALUE_WHITE));
        kpiLayout.add(createMiniCard(new TranslatableSpan("pv.details.kpi.total_groups"), groupsCountSpan, VaadinIcon.ABACUS, FrontendColor.TEXT_VALUE_WHITE));
        leftLayout.add(kpiLayout);

        Div pvConfigCard = new Div();
        applyDashboardCardStyle(pvConfigCard);
        pvInvestmentTitle.getStyle().set("margin-top", "0").set("color", FrontendColor.TEXT_VALUE_WHITE);

        HorizontalLayout pvForm = new HorizontalLayout();
        pvForm.setAlignItems(FlexComponent.Alignment.BASELINE);
        pvCostField.setValue(0.0);

        savePVConfigBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        savePVConfigBtn.addClickListener(e -> {
            if (pvSiteEntity != null) {
                CustomCurrency userCurrency = userSessionContext.getCurrency();
                pvSiteEntity.setPvCost(new Money(pvCostField.getValue(), userCurrency));
                pvSiteEntity.setSetupDate(setupDatePicker.getValue());
                SpringContextHelper.getBean(EntityService.class).save(pvSiteEntity);
                Notification.show(getTranslation("finance.notification.pv_saved"));
            }
        });
        pvForm.add(pvCostField, setupDatePicker, savePVConfigBtn);
        pvConfigCard.add(pvInvestmentTitle, pvForm);
        leftLayout.add(pvConfigCard);

        Div minerCostsCard = new Div();
        applyDashboardCardStyle(minerCostsCard);
        hardwareCostsTitle.getStyle().set("margin-top", "0").set("color", FrontendColor.TEXT_VALUE_WHITE);
        setupMinerGrid();
        minerCostsCard.add(hardwareCostsTitle, minerGrid);
        leftLayout.add(minerCostsCard);

        Div pricesCard = new Div();
        applyDashboardCardStyle(pricesCard);
        H3 pricesTitle = new H3(getTranslation("finance.title.tariffs_history", "Tarife & Strompreise"));
        pricesTitle.getStyle().set("margin-top", "0").set("color", FrontendColor.TEXT_VALUE_WHITE);

        Div pricesGridsLayout = new Div();
        pricesGridsLayout.addClassName("prices-split");

        Div feedInLayout = new Div();
        feedInLayout.addClassName("prices-column");
        H4 feedInTitle = new H4(getTranslation("finance.title.feed_in", "Einspeisevergütung"));
        feedInTitle.getStyle().set("margin", "0 0 16px 0").set("color", FrontendColor.TEXT_VALUE_WHITE);
        setupPriceGrid(feedInGrid, true);
        HorizontalLayout addFeedInLayout = new HorizontalLayout(feedInDatePicker, feedInPriceField, addFeedInBtn);
        addFeedInLayout.setAlignItems(FlexComponent.Alignment.BASELINE);
        addFeedInBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addFeedInBtn.addClickListener(e -> handleAddPrice(true, feedInDatePicker, feedInPriceField, feedInGrid));
        feedInLayout.add(feedInTitle, addFeedInLayout, feedInGrid);

        Div electricityLayout = new Div();
        electricityLayout.addClassName("prices-column");
        H4 electricityTitle = new H4(getTranslation("finance.title.electricity_price", "Strompreis"));
        electricityTitle.getStyle().set("margin", "0 0 16px 0").set("color", FrontendColor.TEXT_VALUE_WHITE);
        setupPriceGrid(electricityGrid, false);
        HorizontalLayout addElectricityLayout = new HorizontalLayout(electricityDatePicker, electricityPriceField, addElectricityBtn);
        addElectricityLayout.setAlignItems(FlexComponent.Alignment.BASELINE);
        addElectricityBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addElectricityBtn.addClickListener(e -> handleAddPrice(false, electricityDatePicker, electricityPriceField, electricityGrid));
        electricityLayout.add(electricityTitle, addElectricityLayout, electricityGrid);

        pricesGridsLayout.add(feedInLayout, electricityLayout);
        pricesCard.add(pricesTitle, pricesGridsLayout);
        leftLayout.add(pricesCard);

        Div rightLayout = new Div();
        rightLayout.addClassName("details-right-panel");

        Div formCard = new Div();
        applyDashboardCardStyle(formCard);
        formCard.getStyle().set("gap", "16px");

        formTitle.getStyle().set("margin", "0").set("color", FrontendColor.TEXT_VALUE_WHITE).set("font-size", "var(--lumo-font-size-l)");
        formCard.add(formTitle);

        HorizontalLayout selectorLayout = new HorizontalLayout();
        selectorLayout.setWidthFull();
        selectorLayout.setAlignItems(FlexComponent.Alignment.BASELINE);

        groupSelector.setWidthFull();
        groupSelector.setItemLabelGenerator(p -> {
            String fallback = getTranslation("pv.details.group.fallback", p.getAmountOfPanels());
            return p.getGroupName() != null ? p.getGroupName() : fallback;
        });
        groupSelector.addValueChangeListener(e -> selectPanelGroup(e.getValue()));

        Button addNewGroupBtn = new Button(VaadinIcon.PLUS.create());
        addNewGroupBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ICON);
        addNewGroupBtn.addClickListener(e -> createNewPanelGroup());

        selectorLayout.add(groupSelector, addNewGroupBtn);
        formCard.add(selectorLayout);

        Hr hr = new Hr();
        hr.getStyle().set("margin", "8px 0").set("border-color", "#222226");
        formCard.add(hr);

        applyModernFieldStyle(groupNameField);
        applyModernFieldStyle(latitudeField);
        applyModernFieldStyle(longitudeField);
        applyModernFieldStyle(amountField);
        applyModernFieldStyle(powerPerPanelField);
        applyModernFieldStyle(azimuthField);
        applyModernFieldStyle(slopeField);

        latitudeField.setReadOnly(true);
        longitudeField.setReadOnly(true);
        selectLocationBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        selectLocationBtn.addClickListener(e -> openMapDialog());

        FormLayout formLayout = new FormLayout();
        formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
        formLayout.add(groupNameField, slopeField);

        HorizontalLayout locationLayout = new HorizontalLayout(latitudeField, longitudeField);
        locationLayout.setWidthFull();
        locationLayout.setAlignItems(FlexComponent.Alignment.BASELINE);
        latitudeField.getStyle().set("flex", "1");
        longitudeField.getStyle().set("flex", "1");

        formLayout.add(locationLayout, 2);
        formLayout.add(selectLocationBtn, 2);
        formLayout.add(amountField, powerPerPanelField);
        formLayout.add(azimuthField, 2);

        formCard.add(formLayout);

        savePanelsBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        savePanelsBtn.addClickListener(e -> savePanelConfiguration());
        savePanelsBtn.getStyle().set("flex", "1");

        deletePanelsBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deletePanelsBtn.addClickListener(e -> deletePanelConfiguration());
        deletePanelsBtn.getStyle().set("flex", "1");

        HorizontalLayout buttonLayout = new HorizontalLayout(savePanelsBtn, deletePanelsBtn);
        buttonLayout.setWidthFull();
        buttonLayout.setSpacing(true);
        formCard.add(buttonLayout);

        rightLayout.add(formCard);

        mainLayout.add(leftLayout, rightLayout);
        add(mainLayout);

        setupBinder();
        setFormEnabled(false);

        updateLabelsAndTexts();
        this.pVSiteRepository = pVSiteRepository;
        this.entityService = entityService;
    }

    private void setupPriceGrid(Grid<HistoricalPrice> grid, boolean isFeedIn) {
        grid.setHeight("200px");
        styleGrid(grid);

        grid.addColumn(hp -> hp.getValidFrom().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
                .setHeader(getTranslation("finance.prices.date", "Ab Datum")).setAutoWidth(true);

        grid.addColumn(hp -> {
            GlobalConstantsService gcs = SpringContextHelper.getBean(GlobalConstantsService.class);
            return gcs.convert(hp.getPrice(), userSessionContext.getCurrency()).toString();
        }).setHeader(getTranslation("finance.prices.amount", "Betrag")).setAutoWidth(true);

        grid.addComponentColumn(hp -> {
            Button deleteBtn = new Button(VaadinIcon.TRASH.create());
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            deleteBtn.addClickListener(e -> {
                if (pvSiteEntity != null) {
                    if (isFeedIn) pvSiteEntity.getFeedInTariffHistory().remove(hp);
                    else pvSiteEntity.getElectricityPriceHistory().remove(hp);

                    SpringContextHelper.getBean(EntityService.class).save(pvSiteEntity);
                    grid.setItems(isFeedIn ? pvSiteEntity.getFeedInTariffHistory() : pvSiteEntity.getElectricityPriceHistory());
                    Notification.show(getTranslation("finance.notification.price_deleted", "Eintrag gelöscht"));
                }
            });
            return deleteBtn;
        }).setAutoWidth(true);
    }

    private void handleAddPrice(boolean isFeedIn, DatePicker datePicker, NumberField priceField, Grid<HistoricalPrice> grid) {
        if (pvSiteEntity == null || datePicker.getValue() == null || priceField.getValue() == null) return;

        Money price = new Money(priceField.getValue(), userSessionContext.getCurrency());
        HistoricalPrice hp = new HistoricalPrice(datePicker.getValue(), price);

        if (isFeedIn) {
            pvSiteEntity.getFeedInTariffHistory().add(hp);
            pvSiteEntity.getFeedInTariffHistory().sort(Comparator.naturalOrder());
        } else {
            pvSiteEntity.getElectricityPriceHistory().add(hp);
            pvSiteEntity.getElectricityPriceHistory().sort(Comparator.naturalOrder());
        }

        SpringContextHelper.getBean(EntityService.class).save(pvSiteEntity);
        grid.setItems(isFeedIn ? pvSiteEntity.getFeedInTariffHistory() : pvSiteEntity.getElectricityPriceHistory());

        datePicker.clear();
        priceField.clear();
        Notification.show(getTranslation("finance.notification.price_added", "Eintrag hinzugefügt"));
    }

    private void applyDashboardCardStyle(Div element) {
        element.addClassName("dashboard-card");
    }

    private void styleGrid(Grid<?> grid) {
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.getStyle().set("background-color", "transparent").set("border", "none");
    }

    private void setupMinerGrid() {
        minerGrid.setWidthFull();
        minerGrid.setHeight("200px");
        styleGrid(minerGrid);

        minerGrid.addColumn(m -> m.getName() != null ? m.getName() : "Miner").setKey("minerName").setAutoWidth(true);

        Binder<MinerEntity<?>> binder = new Binder<>();
        Editor<MinerEntity<?>> editor = minerGrid.getEditor();
        editor.setBinder(binder);
        editor.setBuffered(true);

        NumberField costEditField = new NumberField();
        costEditField.setWidthFull();
        binder.forField(costEditField).bind(
                miner -> miner.getMinerCost().getRawMoneyAmount(),
                (miner, value) -> {
                    CustomCurrency userCurrency = userSessionContext.getCurrency();
                    miner.setMinerCost(new Money(value, userCurrency));
                    entityService.save(miner, pvSiteEntity);
                }
        );

        minerGrid.addColumn(miner -> {
            GlobalConstantsService globalConstantsService = SpringContextHelper.getBean(GlobalConstantsService.class);
            CustomCurrency targetCurrency = userSessionContext.getCurrency();
            Money convertedCost = globalConstantsService.convert(miner.getMinerCost(), targetCurrency);
            return convertedCost.toString();
        }).setKey("minerCost").setEditorComponent(costEditField).setAutoWidth(true);

        Grid.Column<MinerEntity<?>> editColumn = minerGrid.addComponentColumn(miner -> {
            Button editButton = new Button(VaadinIcon.EDIT.create());
            editButton.addClickListener(e -> {
                if (editor.isOpen()) editor.cancel();
                minerGrid.getEditor().editItem(miner);
            });
            return editButton;
        }).setAutoWidth(true);

        Button saveButton = new Button(VaadinIcon.CHECK.create(), e -> {
            MinerEntity<?> editedMiner = editor.getItem();
            SpringContextHelper.getBean(EntityService.class).save(editedMiner, pvSiteEntity);
            editor.save();
            Notification.show(getTranslation("finance.notification.miner_updated"));
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button(VaadinIcon.CLOSE.create(), e -> editor.cancel());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_ERROR);

        HorizontalLayout actions = new HorizontalLayout(saveButton, cancelButton);
        actions.setSpacing(true);
        editColumn.setEditorComponent(actions);
    }

    private void openMapDialog() {
        Dialog mapDialog = new Dialog();
        mapDialog.setHeaderTitle(getTranslation("pv.details.map.title", "Standort auf Karte auswählen"));
        mapDialog.setWidth("80vw");
        mapDialog.setHeight("80vh");
        mapDialog.setResizable(true);

        LeafletMap leafletMap = new LeafletMap();
        leafletMap.getStyle().set("height", "100%").set("width", "100%");

        Double currentLat = latitudeField.getValue();
        Double currentLng = longitudeField.getValue();

        boolean hasLocation = currentLat != null && currentLng != null && currentLat != 0.0 && currentLng != 0.0;
        double initLat = hasLocation ? currentLat : 51.1657;
        double initLng = hasLocation ? currentLng : 10.4515;
        int initZoom = hasLocation ? 16 : 6;

        leafletMap.setInitialView(initLat, initLng, initZoom, hasLocation);

        Double[] selectedCoords = new Double[] { hasLocation ? currentLat : null, hasLocation ? currentLng : null };

        leafletMap.setOnMapClickListener((lat, lng) -> {
            selectedCoords[0] = lat;
            selectedCoords[1] = lng;
        });

        VerticalLayout layout = new VerticalLayout(leafletMap);
        layout.setSizeFull();
        layout.setPadding(false);
        mapDialog.add(layout);

        Button cancelBtn = new Button(getTranslation("btn.cancel", "Abbrechen"), e -> mapDialog.close());

        Button applyBtn = new Button(getTranslation("btn.apply", "Übernehmen"), e -> {
            if (selectedCoords[0] != null && selectedCoords[1] != null) {
                latitudeField.setValue(selectedCoords[0]);
                longitudeField.setValue(selectedCoords[1]);
            }
            mapDialog.close();
        });
        applyBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        mapDialog.getFooter().add(cancelBtn, applyBtn);
        mapDialog.open();
    }

    private void applyModernFieldStyle(com.vaadin.flow.component.HasValue<?, ?> field) {
        if (field instanceof TextField) {
            ((TextField) field).addThemeVariants(TextFieldVariant.LUMO_SMALL);
        } else if (field instanceof NumberField) {
            ((NumberField) field).addThemeVariants(TextFieldVariant.LUMO_SMALL);
        } else if (field instanceof IntegerField) {
            ((IntegerField) field).addThemeVariants(TextFieldVariant.LUMO_SMALL);
        } else if (field instanceof ComboBox) {
            ((ComboBox<?>) field).getElement().getThemeList().add("small");
        }
    }

    private void setupBinder() {
        panelBinder.forField(groupNameField).bind(PVPanels::getGroupName, PVPanels::setGroupName);
        panelBinder.forField(latitudeField).bind(PVPanels::getLatitudeDeg, PVPanels::setLatitudeDeg);
        panelBinder.forField(longitudeField).bind(PVPanels::getLongitudeDeg, PVPanels::setLongitudeDeg);
        panelBinder.forField(amountField).bind(PVPanels::getAmountOfPanels, PVPanels::setAmountOfPanels);
        panelBinder.forField(powerPerPanelField).bind(PVPanels::getPowerPerPanelInWatts, PVPanels::setPowerPerPanelInWatts);
        panelBinder.forField(azimuthField).bind(PVPanels::getPanelAzimuthDegree, PVPanels::setPanelAzimuthDegree);
        panelBinder.forField(slopeField).bind(PVPanels::getPanelSlopeDeg, PVPanels::setPanelSlopeDeg);

        panelBinder.addValueChangeListener(e -> {
            refreshGlobalKpis();
            updateForecastWidget();
        });
    }

    private void updateLabelsAndTexts() {
        CustomCurrency currency = userSessionContext.getCurrency();

        formTitle.setText(getTranslation("pv.details.form.title"));
        groupSelector.setLabel(getTranslation("pv.details.form.group_selector"));
        groupNameField.setLabel(getTranslation("pv.details.form.group_name"));
        latitudeField.setLabel(getTranslation("pv.details.form.latitude"));
        longitudeField.setLabel(getTranslation("pv.details.form.longitude"));
        amountField.setLabel(getTranslation("pv.details.form.amount"));
        powerPerPanelField.setLabel(getTranslation("pv.details.form.power_per_panel"));
        azimuthField.setLabel(getTranslation("pv.details.form.azimuth"));
        slopeField.setLabel(getTranslation("pv.details.form.slope"));
        savePanelsBtn.setText(getTranslation("pv.details.form.save"));
        deletePanelsBtn.setText(getTranslation("pv.details.form.delete"));
        selectLocationBtn.setText(getTranslation("pv.details.form.select_location", "Auswählen"));

        pvInvestmentTitle.setText(getTranslation("finance.title.pv_investment"));
        hardwareCostsTitle.setText(getTranslation("finance.title.hardware_costs"));
        pvCostField.setLabel(getTranslation("finance.form.pv_cost", currency.getSymbol()));
        pvCostField.setPlaceholder(getTranslation("finance.form.pv_cost.placeholder"));
        setupDatePicker.setLabel(getTranslation("finance.form.setup_date"));
        setupDatePicker.setLocale(userSessionContext.getLocale());
        savePVConfigBtn.setText(getTranslation("finance.form.save_btn"));

        minerGrid.getColumnByKey("minerName").setHeader(getTranslation("finance.grid.miner.name"));
        minerGrid.getColumnByKey("minerCost").setHeader(getTranslation("finance.grid.miner.cost", currency.getSymbol()));

        feedInDatePicker.setLabel(getTranslation("finance.prices.date_label", "Gültig ab"));
        feedInPriceField.setLabel(getTranslation("finance.prices.amount_label", currency.getSymbol()));
        electricityDatePicker.setLabel(getTranslation("finance.prices.date_label", "Gültig ab"));
        electricityPriceField.setLabel(getTranslation("finance.prices.amount_label", currency.getSymbol()));
    }

    private void updateForecastWidget() {

    }

    protected void connectPVSiteEntity(PVSiteEntity pvSiteEntity) {
        this.pvSiteEntity = pvSiteEntity;
        refreshGroupSelector();

        if (pvSiteEntity.getPvPanels() != null && !pvSiteEntity.getPvPanels().isEmpty()) {
            groupSelector.setValue(pvSiteEntity.getPvPanels().stream().findFirst().orElseThrow());
        } else {
            setFormEnabled(false);
        }

        GlobalConstantsService globalConstantsService = SpringContextHelper.getBean(GlobalConstantsService.class);
        CustomCurrency targetCurrency = userSessionContext.getCurrency();

        if (pvSiteEntity.getPvCost() != null) {
            Money convertedPvCost = globalConstantsService.convert(pvSiteEntity.getPvCost(), targetCurrency);
            pvCostField.setValue(convertedPvCost.getRawMoneyAmount());
        }
        setupDatePicker.setValue(pvSiteEntity.getSetupDate());
        minerGrid.setItems(pvSiteEntity.getMiners());

        if (pvSiteEntity.getFeedInTariffHistory() != null) {
            feedInGrid.setItems(pvSiteEntity.getFeedInTariffHistory());
        }
        if (pvSiteEntity.getElectricityPriceHistory() != null) {
            electricityGrid.setItems(pvSiteEntity.getElectricityPriceHistory());
        }

        refreshGlobalKpis();
        updateForecastWidget();
    }

    private void refreshGroupSelector() {
        if (pvSiteEntity == null) return;
        List<PVPanels> panels = new ArrayList<>(pvSiteEntity.getPvPanels());
        groupSelector.setItems(panels);
    }

    private void selectPanelGroup(PVPanels panels) {
        this.selectedPanels = panels;
        if (panels != null) {
            panelBinder.readBean(panels);
            setFormEnabled(true);
        } else {
            setFormEnabled(false);
        }
    }

    private void createNewPanelGroup() {
        if (pvSiteEntity == null) return;
        PVPanels newGroup = new PVPanels();
        newGroup.setGroupName(getTranslation("pv.details.group.new_default_name"));
        newGroup.setParentEntity(pvSiteEntity);

        SpringContextHelper.getBean(EntityService.class).save(pvSiteEntity, newGroup);
        refreshGroupSelector();
        groupSelector.setValue(newGroup);
    }

    private void refreshGlobalKpis() {
        if (pvSiteEntity == null || pvSiteEntity.getPvPanels() == null) return;

        double totalKwp = pvSiteEntity.getKwp();
        int totalPanels = pvSiteEntity.getPvPanels().stream().mapToInt(PVPanels::getAmountOfPanels).sum();
        int groupCount = pvSiteEntity.getPvPanels().size();

        totalPeakPowerSpan.setText(String.format(Locale.US, "%.2f kWp", totalKwp));
        totalPanelsCountSpan.setText(String.valueOf(totalPanels));
        groupsCountSpan.setText(String.valueOf(groupCount));
    }

    private void setFormEnabled(boolean enabled) {
        groupNameField.setEnabled(enabled);
        latitudeField.setEnabled(enabled);
        longitudeField.setEnabled(enabled);
        amountField.setEnabled(enabled);
        powerPerPanelField.setEnabled(enabled);
        azimuthField.setEnabled(enabled);
        slopeField.setEnabled(enabled);
        savePanelsBtn.setEnabled(enabled);
        deletePanelsBtn.setEnabled(enabled);
        selectLocationBtn.setEnabled(enabled);
    }

    private void deletePanelConfiguration() {
        if (selectedPanels != null && pvSiteEntity != null) {
            Dialog confirmDialog = new Dialog();
            confirmDialog.setHeaderTitle(getTranslation("pv.details.dialog.delete_title", "Gruppe löschen"));
            confirmDialog.add(new Span(getTranslation("pv.details.dialog.delete_text", "Möchtest du diese PV-Gruppe wirklich löschen?")));

            Button cancelBtn = new Button(getTranslation("btn.cancel", "Abbrechen"), e -> confirmDialog.close());

            Button confirmBtn = new Button(getTranslation("btn.delete", "Löschen"), e -> {
                pvSiteEntity.getPvPanels().remove(selectedPanels);
                SpringContextHelper.getBean(EntityService.class).delete(selectedPanels);
                refreshGroupSelector();

                if (!pvSiteEntity.getPvPanels().isEmpty()) {
                    groupSelector.setValue(pvSiteEntity.getPvPanels().iterator().next());
                } else {
                    groupSelector.clear();
                    panelBinder.readBean(null);
                    setFormEnabled(false);
                }
                refreshGlobalKpis();
                updateForecastWidget();
                Notification.show(getTranslation("pv.details.notification.group_deleted"));
                confirmDialog.close();
            });
            confirmBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);

            confirmDialog.getFooter().add(cancelBtn, confirmBtn);
            confirmDialog.open();
        }
    }

    private void savePanelConfiguration() {
        if (selectedPanels != null && pvSiteEntity != null) {
            if (panelBinder.writeBeanIfValid(selectedPanels)) {
                SpringContextHelper.getBean(EntityService.class).save(pvSiteEntity, selectedPanels);
                refreshGroupSelector();
                groupSelector.setValue(selectedPanels);
                refreshGlobalKpis();
                updateForecastWidget();
                Notification.show(getTranslation("pv.details.notification.group_saved"));
            } else {
                Notification.show(getTranslation("pv.details.notification.validation_error")).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        }
    }

    private Div createMiniCard(TranslatableSpan translatableTitle, Span valueSpan, VaadinIcon icon, String valueColor) {
        Div card = new Div();
        card.addClassName("mini-kpi-card");

        var i = icon.create();
        i.addClassName("mini-kpi-icon");
        i.getStyle().set("color", valueColor);

        Div textCol = new Div();
        textCol.addClassName("mini-kpi-text-col");

        translatableTitle.addClassName("mini-kpi-title");

        valueSpan.addClassName("mini-kpi-value");
        valueSpan.getStyle().set("color", valueColor);

        textCol.add(translatableTitle, valueSpan);
        card.add(i, textCol);
        return card;
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        updateLabelsAndTexts();
        refreshGlobalKpis();
        updateForecastWidget();
    }

    @Override
    public void onTimeZoneChange(TimeZoneChangeEvent event) {
        updateForecastWidget();
    }

    @Override
    public void onCurrencyChange(CurrencyChangeEvent event) {
        updateLabelsAndTexts();
        if (pvSiteEntity != null && pvSiteEntity.getPvCost() != null) {
            GlobalConstantsService globalConstantsService = SpringContextHelper.getBean(GlobalConstantsService.class);
            Money convertedPvCost = globalConstantsService.convert(pvSiteEntity.getPvCost(), userSessionContext.getCurrency());
            pvCostField.setValue(convertedPvCost.getRawMoneyAmount());

            minerGrid.getDataProvider().refreshAll();
            feedInGrid.getDataProvider().refreshAll();
            electricityGrid.getDataProvider().refreshAll();
        }
        updateForecastWidget();
    }

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