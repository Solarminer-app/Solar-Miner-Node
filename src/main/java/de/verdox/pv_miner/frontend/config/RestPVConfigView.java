package de.verdox.pv_miner.frontend.config;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.HasTheme;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.listbox.ListBox;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import de.verdox.pv_miner.configfetcher.ConfigFetcherService;
import de.verdox.pv_miner.util.FormatUtil;
import de.verdox.pv_miner.frontend.FrontendService;
import de.verdox.pv_miner_extensions.device.rest.RestConfigStorage;
import de.verdox.solarminer.RequiredField;
import de.verdox.solarminer.formula.FormulaEngine;
import de.verdox.solarminer.rest.*;
import lombok.Getter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

@Route(value = "config/pv/rest")
@PageTitle("Solarminer.app - Rest-API Profile Editor")
public class RestPVConfigView extends VerticalLayout implements LocaleChangeObserver {
    private final RestConfigStorage storage;
    private final ConfigFetcherService configFetcherService;
    private final ListBox<String> configSelection = new ListBox<>();
    private final VerticalLayout editorLayout = new VerticalLayout();

    private final H2 sidebarTitle = new H2("REST-PV Profile");
    private final TextField newConfigName = new TextField();

    private final VerticalLayout sectionsContainer = new VerticalLayout();
    private final Map<String, SectionEditorBlock> activeSectionEditors = new HashMap<>();
    private final TestSection testSection = new TestSection();
    private String currentConfigName;

    public RestPVConfigView(RestConfigStorage storage, ConfigFetcherService configFetcherService) {
        this.storage = storage;
        this.configFetcherService = configFetcherService;
        setSizeFull();
        setPadding(false);
        newConfigName.setPlaceholder("z.B. HomeAssistant...");

        SplitLayout splitLayout = new SplitLayout();
        splitLayout.setSizeFull();
        splitLayout.setSplitterPosition(20);

        VerticalLayout sidebar = new VerticalLayout();
        sidebar.add(sidebarTitle);

        HorizontalLayout addConfigLayout = new HorizontalLayout();
        addConfigLayout.setWidthFull();
        newConfigName.setWidthFull();
        newConfigName.addThemeName("small");
        Button addConfigBtn = new Button(VaadinIcon.PLUS.create(), e -> {
            if (!newConfigName.isEmpty()) {
                createNewProfile(newConfigName.getValue());
                newConfigName.clear();
            }
        });
        addConfigBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
        addConfigLayout.add(newConfigName, addConfigBtn);
        sidebar.add(addConfigLayout);

        H4 localLabel = new H4("Lokale Profile");
        localLabel.getStyle().set("margin-bottom", "0");
        sidebar.add(localLabel);

        configSelection.addValueChangeListener(e -> openProfile(e.getValue()));
        configSelection.setWidthFull();
        sidebar.add(configSelection);

        refreshConfigList();
        splitLayout.addToPrimary(sidebar);

        editorLayout.setPadding(true);
        sectionsContainer.setPadding(false);
        splitLayout.addToSecondary(editorLayout);

        add(splitLayout);
    }

    private void refreshConfigList() {
        try {
            List<String> localConfigs = storage.getSavedConfigs();
            String selected = configSelection.getValue();
            configSelection.setItems(localConfigs);
            if (selected != null && localConfigs.contains(selected)) {
                configSelection.setValue(selected);
            }
        } catch (IOException e) {
            Notification.show("Fehler beim Laden: " + e.getMessage());
        }
    }

    private void createNewProfile(String name) {
        try {
            RestPVConfig newConfig = new RestPVConfig(new HashMap<>());
            storage.save(name, newConfig);
            refreshConfigList();
            configSelection.setValue(name);
        } catch (IOException e) {
            Notification.show("Fehler beim Erstellen.");
        }
    }

    private void openProfile(String configName) {
        if (configName == null) {
            editorLayout.removeAll();
            return;
        }
        this.currentConfigName = configName;
        editorLayout.removeAll();
        sectionsContainer.removeAll();
        activeSectionEditors.values().forEach(SectionEditorBlock::disposeSubscriptions);
        activeSectionEditors.clear();

        try {
            RestPVConfig config = storage.loadConfig(configName);

            HorizontalLayout header = new HorizontalLayout();
            header.setWidthFull();
            header.setAlignItems(Alignment.CENTER);
            header.setJustifyContentMode(JustifyContentMode.BETWEEN);
            H2 title = new H2("Profil: " + configName);
            title.getStyle().set("margin", "0");

            Button saveBtn = new Button("Profil Speichern", VaadinIcon.CHECK.create(), e -> saveCurrentProfile());
            saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            header.add(title, saveBtn);
            editorLayout.add(header);

            Details testDetails = new Details("Live-Test Verbindung", testSection);
            testDetails.setWidthFull();
            editorLayout.add(testDetails);

            HorizontalLayout addSectionLayout = new HorizontalLayout();
            addSectionLayout.setAlignItems(Alignment.BASELINE);
            addSectionLayout.getStyle().set("margin-top", "20px");
            addSectionLayout.getStyle().set("padding-top", "20px");
            addSectionLayout.getStyle().set("border-top", "1px solid var(--lumo-contrast-10pct)");

            H4 sectionTitle = new H4("Fähigkeiten (Sektionen):");
            sectionTitle.getStyle().set("margin", "0");

            ComboBox<RestConfigCreatorTemplate> templateCombo = new ComboBox<>();
            templateCombo.setItems(List.of(RestConfigCreatorTemplate.INVERTER, RestConfigCreatorTemplate.BATTERY, RestConfigCreatorTemplate.SMART_METER, RestConfigCreatorTemplate.HOME_ASSISTANT_PV));
            templateCombo.setItemLabelGenerator(RestConfigCreatorTemplate::name);
            templateCombo.setPlaceholder("Template wählen...");

            Button addSectionBtn = new Button("Sektion hinzufügen", VaadinIcon.PLUS.create(), e -> {
                RestConfigCreatorTemplate selectedTemplate = templateCombo.getValue();
                if (selectedTemplate != null) {
                    if (activeSectionEditors.containsKey(selectedTemplate.id())) {
                        Notification.show("Sektion existiert bereits!");
                        return;
                    }
                    addSectionToView(selectedTemplate, new RestPVConfig.ConfigSection(selectedTemplate.id(), selectedTemplate.name(), new HashMap<>()));
                }
            });
            addSectionBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);

            addSectionLayout.add(sectionTitle, templateCombo, addSectionBtn);
            editorLayout.add(addSectionLayout);
            editorLayout.add(sectionsContainer);

            if (config.getSections() != null) {
                for (Map.Entry<String, RestPVConfig.ConfigSection> entry : config.getSections().entrySet()) {
                    RestConfigCreatorTemplate tpl = getRestTemplateById(entry.getValue().getTemplateId());
                    if (tpl != null) addSectionToView(tpl, entry.getValue());
                }
            }

        } catch (IOException e) {
            Notification.show("Fehler beim Öffnen: " + e.getMessage());
        }
    }

    private RestConfigCreatorTemplate getRestTemplateById(String id) {
        return List.of(RestConfigCreatorTemplate.INVERTER, RestConfigCreatorTemplate.BATTERY, RestConfigCreatorTemplate.SMART_METER, RestConfigCreatorTemplate.HOME_ASSISTANT_PV)
                .stream().filter(t -> t.id().equals(id)).findFirst().orElse(null);
    }

    private void addSectionToView(RestConfigCreatorTemplate template, RestPVConfig.ConfigSection sectionData) {
        SectionEditorBlock block = new SectionEditorBlock(template, sectionData);
        activeSectionEditors.put(template.id(), block);
        sectionsContainer.add(block);
    }

    private void saveCurrentProfile() {
        Map<String, RestPVConfig.ConfigSection> finalSections = new HashMap<>();
        for (Map.Entry<String, SectionEditorBlock> entry : activeSectionEditors.entrySet()) {
            finalSections.put(entry.getKey(), entry.getValue().buildConfigSection());
        }
        RestPVConfig newConfig = new RestPVConfig(finalSections);
        try {
            storage.save(currentConfigName, newConfig);
            Notification.show("Profil erfolgreich gespeichert!", 2000, Notification.Position.TOP_END);
        } catch (IOException e) {
            Notification.show("Fehler beim Speichern!");
        }
    }

    public double getLiveValueForField(String fieldName, Set<String> visited, SectionEditorBlock contextBlock) {
        if (visited.contains(fieldName)) { return 0.0; }
        visited.add(fieldName);
        return contextBlock.getFieldRows().stream().filter(row -> row.getFieldName().equals(fieldName)).findFirst().map(row -> {
            try {
                var entry = row.buildEntry();
                if (entry == null || testSection.getClient() == null) { return 0.0; }
                double raw = testSection.getClient().read(entry);
                return FormulaEngine.evaluate(raw, entry.formula(), name -> getLiveValueForField(name, visited, contextBlock));
            } catch (Exception e) { return 0.0; }
        }).orElse(0.0);
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {

    }

    class SectionEditorBlock extends Details {
        private final RestConfigCreatorTemplate template;
        @Getter
        private final List<CompactRestFieldRow> fieldRows = new ArrayList<>();

        public SectionEditorBlock(RestConfigCreatorTemplate template, RestPVConfig.ConfigSection sectionData) {
            this.template = template;
            setSummaryText("⚙️ Sektion: " + template.name());
            setWidthFull();
            getStyle().set("margin-top", "10px");
            getStyle().set("border", "1px solid var(--lumo-contrast-20pct)");
            getStyle().set("border-radius", "8px");

            VerticalLayout contentLayout = new VerticalLayout();
            contentLayout.setPadding(true);

            Button removeSectionBtn = new Button("Sektion entfernen", VaadinIcon.TRASH.create(), e -> {
                disposeSubscriptions();
                activeSectionEditors.remove(template.id());
                sectionsContainer.remove(this);
            });
            removeSectionBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            contentLayout.add(removeSectionBtn);

            HorizontalLayout tableHeader = new HorizontalLayout();
            tableHeader.setWidthFull();
            tableHeader.getStyle().set("border-bottom", "2px solid var(--lumo-contrast-20pct)");

            Span hName = new Span("Feldname"); hName.setWidth("150px");
            Span hMethod = new Span("Methode"); hMethod.setWidth("90px");
            Span hPath = new Span("URL / Pfad"); hPath.setWidth("240px");
            Span hJson = new Span("JSON Path"); hJson.setWidth("160px");
            Span hScale = new Span("Faktor"); hScale.setWidth("70px");
            Span hFormula = new Span("Formel"); hFormula.setWidth("100px");
            Span hType = new Span("Typ"); hType.setWidth("90px");
            Span hLive = new Span("Live-Wert"); hLive.setWidth("120px");

            for (Span span : List.of(hName, hMethod, hPath, hJson, hScale, hFormula, hType, hLive)) span.getStyle().set("font-weight", "bold");
            tableHeader.add(hName, hMethod, hPath, hJson, hScale, hFormula, hType, hLive);
            contentLayout.add(tableHeader);

            for (RequiredField reqField : template.requiredFields()) {
                RestPVConfig.Entry<?> existingEntry = null;
                try { existingEntry = sectionData.getEntryForId(reqField.field()); } catch (NoSuchElementException ignored) {}
                CompactRestFieldRow row = new CompactRestFieldRow(reqField, existingEntry, this);
                fieldRows.add(row);
                contentLayout.add(row);
            }
            setContent(contentLayout);
            setOpened(true);
        }

        public void disposeSubscriptions() { fieldRows.forEach(CompactRestFieldRow::disposeSubscription); }

        public RestPVConfig.ConfigSection buildConfigSection() {
            Map<String, RestPVConfig.Entry<?>> entries = new HashMap<>();
            for (CompactRestFieldRow row : fieldRows) {
                var entry = row.buildEntry();
                if (entry != null) entries.put(row.getFieldName(), entry);
            }
            return new RestPVConfig.ConfigSection(template.id(), template.name(), entries);
        }
    }

    class CompactRestFieldRow extends HorizontalLayout {
        private final RequiredField requiredField;
        private final SectionEditorBlock parentBlock;
        private final ComboBox<RestHttpMethod> httpMethod = new ComboBox<>();
        private final TextField urlExtensionField = new TextField();
        private final TextField jsonPathField = new TextField();
        private final NumberField scaleFactorField = new NumberField();
        private final TextField formulaField = new TextField();
        private final ComboBox<RestParameterType<?>> parameterTypeField = new ComboBox<>();
        private final TextField liveValueDisplay = new TextField();
        private Disposable subscription;

        public CompactRestFieldRow(RequiredField requiredField, RestPVConfig.Entry<?> entry, SectionEditorBlock parentBlock) {
            this.requiredField = requiredField;
            this.parentBlock = parentBlock;
            setWidthFull();
            setAlignItems(Alignment.CENTER);
            getStyle().set("border-bottom", "1px solid var(--lumo-contrast-10pct)");

            Span nameLabel = new Span(requiredField.field());
            nameLabel.setWidth("150px");

            httpMethod.setItems(RestHttpMethod.values());
            httpMethod.setWidth("90px");
            urlExtensionField.setWidth("240px");
            jsonPathField.setWidth("160px");
            scaleFactorField.setWidth("70px");
            formulaField.setWidth("100px");

            parameterTypeField.setItems(RestParameterType.INT, RestParameterType.LONG, RestParameterType.FLOAT, RestParameterType.DOUBLE);
            parameterTypeField.setItemLabelGenerator(RestParameterType::identifier);
            parameterTypeField.setWidth("90px");

            liveValueDisplay.setReadOnly(true);
            liveValueDisplay.setWidth("120px");

            if (entry != null) {
                httpMethod.setValue(entry.httpMethod());
                urlExtensionField.setValue(entry.urlExtension());
                jsonPathField.setValue(entry.jsonPath());
                scaleFactorField.setValue((double) entry.scaleFactor());
                formulaField.setValue(entry.formula());
                parameterTypeField.setValue(entry.restParameterType());
            } else {
                httpMethod.setValue(RestHttpMethod.GET);
                urlExtensionField.setValue("/api/states/sensor." + requiredField.field());
                jsonPathField.setValue("state");
                scaleFactorField.setValue(1.0);
                formulaField.setValue("x");
                parameterTypeField.setValue(RestParameterType.FLOAT);
            }

            for (HasTheme comp : List.of(httpMethod, urlExtensionField, jsonPathField, scaleFactorField, formulaField, parameterTypeField, liveValueDisplay)) comp.addThemeName("small");
            add(nameLabel, httpMethod, urlExtensionField, jsonPathField, scaleFactorField, formulaField, parameterTypeField, liveValueDisplay);
        }

        @Override
        protected void onAttach(AttachEvent attachEvent) {
            super.onAttach(attachEvent);
            subscription = Flux.defer(() -> Mono.fromRunnable(() -> {
                String displayStr = "---";
                if (testSection.getClient() != null) {
                    try {
                        double rawEvaluatedValue = getLiveValueForField(requiredField.field(), new HashSet<>(), parentBlock);
                        double finalValue = rawEvaluatedValue * scaleFactorField.getValue();
                        displayStr = FormatUtil.formatNumber(finalValue) + " " + requiredField.unit();
                    } catch (Exception ex) {
                        displayStr = "Error";
                    }
                }
                final String result = displayStr;
                FrontendService.scheduleUpdate(getUI(), ui -> liveValueDisplay.setValue(result));
            })).subscribeOn(Schedulers.boundedElastic()).repeat().delayElements(Duration.ofSeconds(2)).subscribe();
        }

        public void disposeSubscription() { if (subscription != null && !subscription.isDisposed()) subscription.dispose(); }
        public String getFieldName() { return requiredField.field(); }
        public RestPVConfig.Entry<?> buildEntry() {
            try {
                return new RestPVConfig.Entry<>(urlExtensionField.getValue(), httpMethod.getValue(), jsonPathField.getValue(), scaleFactorField.getValue().floatValue(), formulaField.getValue(), parameterTypeField.getValue());
            } catch (Exception e) { return null; }
        }
    }

    public static class TestSection extends Div {
        @Getter
        private RestPVClient client;
        private final TextField url = new TextField("Base URL", "http://192.168.178.50:8123", "");
        private final TextField token = new TextField("API Token");
        private final Button connect = new Button("Test Connection");

        public TestSection() {
            url.setWidth("280px"); token.setWidth("250px");
            url.addThemeName("small"); token.addThemeName("small"); connect.addThemeVariants(ButtonVariant.LUMO_SMALL);
            connect.setDisableOnClick(true);

            connect.addClickListener(e -> {
                connect.setText("Connecting...");
                var loadingIcon = VaadinIcon.REFRESH.create();
                loadingIcon.getStyle().set("animation", "spin 1s linear infinite");
                connect.setIcon(loadingIcon);
                connect.removeThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_ERROR);

                var ui = UI.getCurrent();
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        if (client != null) client.close();
                        client = new RestPVClient(url.getValue(), token.getValue());
                        client.ping();
                        ui.access(() -> {
                            connect.setText("Connected"); connect.setIcon(VaadinIcon.CHECK.create());
                            connect.addThemeVariants(ButtonVariant.LUMO_SUCCESS); connect.setEnabled(true);
                        });
                    } catch (Exception ex) {
                        ui.access(() -> {
                            connect.setText("Connection Failed"); connect.setIcon(VaadinIcon.CLOSE.create());
                            connect.addThemeVariants(ButtonVariant.LUMO_ERROR); connect.setEnabled(true);
                        });
                    }
                });
            });
            UI.getCurrent().getElement().executeJs("if (!document.getElementById('spin-style')) { var style = document.createElement('style'); style.id = 'spin-style'; style.type = 'text/css'; style.innerHTML = '@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }'; document.head.appendChild(style); }");
            HorizontalLayout layout = new HorizontalLayout(url, token, connect);
            layout.setAlignItems(Alignment.BASELINE);
            add(layout);
        }
        @Override
        protected void onDetach(DetachEvent detachEvent) { super.onDetach(detachEvent); if (client != null) client.close(); }
    }
}