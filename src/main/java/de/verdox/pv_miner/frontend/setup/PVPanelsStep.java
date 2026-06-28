package de.verdox.pv_miner.frontend.setup;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableButton;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableH3;
import de.verdox.pv_miner.pvsite.PVPanels;
import de.verdox.pv_miner.frontend.pvsite.details.CompassField;
import de.verdox.pv_miner.frontend.pvsite.details.LeafletMap;

import java.util.ArrayList;
import java.util.List;

public class PVPanelsStep extends VerticalLayout implements WizardStep {

    private final Runnable onValidationChange;

    private final Grid<PVPanels> panelsGrid = new Grid<>(PVPanels.class, false);
    private final List<PVPanels> panelsList = new ArrayList<>();

    private final TextField groupNameField = new TextField();
    private final NumberField latitudeField = new NumberField();
    private final NumberField longitudeField = new NumberField();
    private final IntegerField amountField = new IntegerField();
    private final NumberField powerPerPanelField = new NumberField();
    private final CompassField azimuthField = new CompassField();
    private final NumberField slopeField = new NumberField();

    private final Button selectLocationBtn;
    private final Button addPanelBtn;

    public PVPanelsStep(Runnable onValidationChange) {
        this.onValidationChange = onValidationChange;

        setPadding(false);
        setSpacing(true);
        setWidthFull();
        setAlignItems(Alignment.CENTER);


        selectLocationBtn = new TranslatableButton("setup.panels.btn_location", VaadinIcon.MAP_MARKER.create());
        addPanelBtn = new TranslatableButton("setup.panels.btn_add_group", VaadinIcon.PLUS.create());

        Div splitContainer = new Div();
        splitContainer.setWidthFull();
        splitContainer.setMaxWidth("100%");
        splitContainer.getStyle().set("display", "flex").set("flex-wrap", "wrap").set("gap", "20px");

        VerticalLayout leftSide = new VerticalLayout();
        leftSide.setPadding(false);
        leftSide.getStyle().set("flex", "1 1 400px");

        panelsGrid.addColumn(PVPanels::getGroupName).setHeader(getTranslation("setup.panels.grid.name"));
        panelsGrid.addColumn(PVPanels::getAmountOfPanels).setHeader(getTranslation("setup.panels.grid.amount"));
        panelsGrid.addColumn(p -> p.getPowerPerPanelInWatts() + " W").setHeader(getTranslation("setup.panels.grid.power"));
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
        panelsGrid.setHeight("350px");
        panelsGrid.getStyle().set("border", "1px solid #222226").set("border-radius", "8px");

        leftSide.add(new TranslatableH3("setup.panels.title.created"), panelsGrid);

        Div rightCard = new Div();
        rightCard.getStyle().set("flex", "1 1 400px").set("background-color", "var(--lumo-contrast-5pct)")
                .set("border", "1px solid #222226").set("border-radius", "8px").set("padding", "var(--lumo-space-l)");

        TranslatableH3 formTitle = new TranslatableH3("setup.panels.title.new");
        formTitle.getStyle().set("margin-top", "0");


        groupNameField.setLabel(getTranslation("setup.panels.group_name"));
        latitudeField.setLabel(getTranslation("setup.panels.latitude"));
        longitudeField.setLabel(getTranslation("setup.panels.longitude"));
        amountField.setLabel(getTranslation("setup.panels.amount"));
        powerPerPanelField.setLabel(getTranslation("setup.panels.power"));
        slopeField.setLabel(getTranslation("setup.panels.slope"));
        azimuthField.setLabel(getTranslation("setup.panels.azimuth"));

        applyModernFieldStyle(groupNameField);
        applyModernFieldStyle(latitudeField);
        applyModernFieldStyle(longitudeField);
        applyModernFieldStyle(amountField);
        applyModernFieldStyle(powerPerPanelField);
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

        addPanelBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        addPanelBtn.setWidthFull();
        addPanelBtn.getStyle().set("margin-top", "var(--lumo-space-m)");
        addPanelBtn.addClickListener(e -> handleAddGroup());

        rightCard.add(formTitle, formLayout, addPanelBtn);
        splitContainer.add(leftSide, rightCard);

        add(splitContainer);
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

    private void applyModernFieldStyle(com.vaadin.flow.component.HasValue<?, ?> field) {
        if (field instanceof TextField tf) tf.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        else if (field instanceof NumberField nf) nf.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        else if (field instanceof IntegerField inf) inf.addThemeVariants(TextFieldVariant.LUMO_SMALL);
    }

    private void openMapDialog() {
        Dialog mapDialog = new Dialog();
        mapDialog.setHeaderTitle(getTranslation("setup.map.dialog.title"));
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

        Button cancelBtn = new TranslatableButton("btn.cancel", e -> mapDialog.close());
        Button applyBtn = new TranslatableButton("btn.apply", e -> {
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

    public List<PVPanels> getPanelsList() {
        return panelsList;
    }

    @Override
    public String getTitleTranslationKey() { return "setup.step.variables.title"; }

    @Override
    public boolean isValid() { return !panelsList.isEmpty(); }
}