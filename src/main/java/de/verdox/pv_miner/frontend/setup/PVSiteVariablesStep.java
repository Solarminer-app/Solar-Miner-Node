package de.verdox.pv_miner.frontend.setup;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import de.verdox.pv_miner.pvsite.PVPanels;
import de.verdox.pv_miner.frontend.user.UserSessionContext;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import de.verdox.pv_miner.frontend.pvsite.details.CompassField;
import de.verdox.pv_miner.frontend.pvsite.details.LeafletMap;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PVSiteVariablesStep extends VerticalLayout implements WizardStep {

    @org.jetbrains.annotations.NotNull
    private final UserSessionContext sessionContext;
    private final Runnable onValidationChange;

    private final DatePicker setupDatePicker = new DatePicker("Inbetriebnahme / Preis-Gültigkeit ab");
    private final ComboBox<CustomCurrency> currencyComboBox = new ComboBox<>("Währung");
    private final NumberField pvCostField = new NumberField("Anschaffungskosten Anlage");
    private final NumberField electricityPriceField = new NumberField("Strompreis");
    private final NumberField feedInTariffField = new NumberField("Einspeisevergütung (Optional)");

    private final Grid<PVPanels> panelsGrid = new Grid<>(PVPanels.class, false);
    private final List<PVPanels> panelsList = new ArrayList<>();

    private final TextField groupNameField = new TextField("Gruppenname");
    private final NumberField latitudeField = new NumberField("Breitengrad");
    private final NumberField longitudeField = new NumberField("Längengrad");
    private final IntegerField amountField = new IntegerField("Anzahl Panels");
    private final NumberField powerPerPanelField = new NumberField("Leistung pro Panel (W)");
    private final CompassField azimuthField = new CompassField();
    private final NumberField slopeField = new NumberField("Neigung (Grad)");
    private final Button selectLocationBtn = new Button("Standort wählen", VaadinIcon.MAP_MARKER.create());
    private final Button addPanelBtn = new Button("Gruppe hinzufügen", VaadinIcon.PLUS.create());

    public PVSiteVariablesStep(UserSessionContext sessionContext, Runnable onValidationChange) {
        this.sessionContext = sessionContext;
        this.onValidationChange = onValidationChange;

        setPadding(false);
        setSpacing(true);
        setWidthFull();

        setupTopSection();

        HorizontalLayout bottomSplit = new HorizontalLayout();
        bottomSplit.setWidthFull();
        bottomSplit.setAlignItems(Alignment.STRETCH);
        bottomSplit.setSpacing(true);

        VerticalLayout leftSide = new VerticalLayout();
        leftSide.setPadding(false);
        leftSide.setWidth("45%");

        panelsGrid.addColumn(PVPanels::getGroupName).setHeader("Name");
        panelsGrid.addColumn(PVPanels::getAmountOfPanels).setHeader("Anzahl");
        panelsGrid.addColumn(p -> p.getPowerPerPanelInWatts() + " W").setHeader("Leistung");
        panelsGrid.addComponentColumn(panel -> {
            Button delBtn = new Button(VaadinIcon.TRASH.create(), e -> {
                panelsList.remove(panel);
                panelsGrid.getDataProvider().refreshAll();
                onValidationChange.run();
            });
            delBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            return delBtn;
        }).setAutoWidth(true);
        panelsGrid.setItems(panelsList);
        panelsGrid.setHeight("100%");
        panelsGrid.getStyle().set("border", "1px solid #222226").set("border-radius", "8px");

        leftSide.add(new H3("Erstellte PV-Gruppen"), panelsGrid);

        Div rightCard = new Div();
        rightCard.setWidth("55%");
        rightCard.getStyle()
                .set("background-color", "var(--lumo-contrast-5pct)")
                .set("border", "1px solid #222226")
                .set("border-radius", "8px")
                .set("padding", "var(--lumo-space-l)");

        H3 formTitle = new H3("Neue Gruppe konfigurieren");
        formTitle.getStyle().set("margin-top", "0");

        applyModernFieldStyle(groupNameField);
        applyModernFieldStyle(latitudeField);
        applyModernFieldStyle(longitudeField);
        applyModernFieldStyle(amountField);
        applyModernFieldStyle(powerPerPanelField);
        applyModernFieldStyle(slopeField);

        azimuthField.setLabel("Azimut (Grad)");

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

        addPanelBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        addPanelBtn.setWidthFull();
        addPanelBtn.getStyle().set("margin-top", "var(--lumo-space-m)");
        addPanelBtn.addClickListener(e -> handleAddGroup());

        rightCard.add(formTitle, formLayout, addPanelBtn);

        bottomSplit.add(leftSide, rightCard);

        add(new H3("Anlagendaten & Tarife"));
        add(new HorizontalLayout(setupDatePicker, currencyComboBox));
        add(new HorizontalLayout(pvCostField, electricityPriceField, feedInTariffField));
        add(new Hr());
        add(bottomSplit);

        updateCurrencyLabels(currencyComboBox.getValue());
    }

    private void setupTopSection() {
        currencyComboBox.setItems(CustomCurrency.getAvailableCurrencies().stream()
                .filter(c -> c.getCurrencyCode().equals("EUR") || c.getCurrencyCode().equals("USD") || c.getCurrencyCode().equals("CHF"))
                .toList());
        currencyComboBox.setItemLabelGenerator(CustomCurrency::getCurrencyCode);

        CustomCurrency sessionCurr = sessionContext.getCurrency() != null ? sessionContext.getCurrency() : CustomCurrency.getInstance("EUR");
        currencyComboBox.setValue(sessionCurr);
        currencyComboBox.addValueChangeListener(e -> {
            updateCurrencyLabels(e.getValue());
            onValidationChange.run();
        });

        setupDatePicker.setValue(LocalDate.now());
        setupDatePicker.addValueChangeListener(e -> onValidationChange.run());

        electricityPriceField.setRequiredIndicatorVisible(true);
        electricityPriceField.addValueChangeListener(e -> onValidationChange.run());
    }

    private void handleAddGroup() {
        if (amountField.getValue() != null && powerPerPanelField.getValue() != null) {
            PVPanels newPanel = new PVPanels();
            newPanel.setGroupName(groupNameField.getValue());
            newPanel.setAmountOfPanels(amountField.getValue());
            newPanel.setPowerPerPanelInWatts(powerPerPanelField.getValue());

            Double az = azimuthField.getValue() instanceof Number ? ((Number) azimuthField.getValue()).doubleValue() : 0.0;
            newPanel.setPanelAzimuthDegree(az);

            newPanel.setPanelSlopeDeg(slopeField.getValue() != null ? slopeField.getValue() : 0.0);
            newPanel.setLatitudeDeg(latitudeField.getValue() != null ? latitudeField.getValue() : 0.0);
            newPanel.setLongitudeDeg(longitudeField.getValue() != null ? longitudeField.getValue() : 0.0);

            panelsList.add(newPanel);
            panelsGrid.getDataProvider().refreshAll();

            groupNameField.clear();
            amountField.clear();
            powerPerPanelField.clear();
            slopeField.clear();
            azimuthField.clear();

            onValidationChange.run();
        } else {
            amountField.setInvalid(true);
            powerPerPanelField.setInvalid(true);
        }
    }

    private void updateCurrencyLabels(CustomCurrency currency) {
        String sym = currency != null ? currency.getSymbol(sessionContext.getLocale()) : "EUR";
        pvCostField.setLabel("Anschaffungskosten (" + sym + ")");
        electricityPriceField.setLabel("Strompreis (" + sym + ")");
        feedInTariffField.setLabel("Einspeisevergütung (" + sym + ")");
    }

    private void applyModernFieldStyle(com.vaadin.flow.component.HasValue<?, ?> field) {
        if (field instanceof TextField tf) tf.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        else if (field instanceof NumberField nf) nf.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        else if (field instanceof IntegerField inf) inf.addThemeVariants(TextFieldVariant.LUMO_SMALL);
    }

    private void openMapDialog() {
        Dialog mapDialog = new Dialog();
        mapDialog.setHeaderTitle(getTranslation("setup.map.dialog.title", "Standort auf Karte auswählen"));
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

    @Override
    public String getTitleTranslationKey() {
        return "setup.step.variables.title";
    }

    @Override
    public boolean isValid() {
        return !panelsList.isEmpty() &&
                electricityPriceField.getValue() != null &&
                setupDatePicker.getValue() != null &&
                currencyComboBox.getValue() != null;
    }

    public VariablesData getVariablesData() {
        return new VariablesData(
                setupDatePicker.getValue(),
                currencyComboBox.getValue(),
                pvCostField.getValue() != null ? pvCostField.getValue() : 0.0,
                electricityPriceField.getValue(),
                feedInTariffField.getValue() != null ? feedInTariffField.getValue() : 0.0,
                panelsList
        );
    }

    public record VariablesData(
            LocalDate setupDate,
            CustomCurrency currency,
            Double pvCost,
            Double electricityPrice,
            Double feedInTariff,
            List<PVPanels> panels
    ) {}
}