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
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import de.verdox.pv_miner.configfetcher.ConfigFetcherService;
import de.verdox.pv_miner.util.FormatUtil;
import de.verdox.pv_miner.frontend.FrontendService;
import de.verdox.pv_miner_extensions.device.modbus.ModbusConfigStorage;
import de.verdox.solarminer.RequiredField;
import de.verdox.solarminer.formula.FormulaEngine;
import de.verdox.solarminer.modbustcp.*;
import de.verdox.vserializer.json.JsonSerializerContext;
import lombok.Getter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Route(value = "config/pv/modbus/tcp")
@PageTitle("Solarminer.app - Modbus Config Editor")
public class ModbusConfigView extends VerticalLayout implements LocaleChangeObserver {
    private final ModbusConfigStorage storage;
    private final ConfigFetcherService configFetcherService;
    private final ListBox<String> configSelection = new ListBox<>();
    private final VerticalLayout editorLayout = new VerticalLayout();

    private final H2 sidebarTitle = new H2("Modbus Profile");
    private final TextField newConfigName = new TextField();

    private final VerticalLayout sectionsContainer = new VerticalLayout();
    private final Map<String, SectionEditorBlock> activeSectionEditors = new HashMap<>();

    private final TestSection testSection = new TestSection();
    private final FingerprintSection fingerprintSection = new FingerprintSection(testSection);

    private String currentConfigName;

    public ModbusConfigView(ModbusConfigStorage storage, ConfigFetcherService configFetcherService) {
        this.storage = storage;
        this.configFetcherService = configFetcherService;
        setSizeFull();
        setPadding(false);
        newConfigName.setPlaceholder("z.B. Fronius Gen24...");

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
            ModbusConfig newConfig = new ModbusConfig(
                    new ModbusConfig.Fingerprint(40000, 1, ModbusParameterType.INT32, ModbusReadOperationType.READ_HOLDING_REGISTER, ByteOrder.BIG_ENDIAN, ""),
                    new HashMap<>()
            );
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
            ModbusConfig config = storage.loadConfig(configName);

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

            fingerprintSection.setFingerprint(config.getFingerprint());
            Details fingerprintDetails = new Details("Geräte-Fingerprint (Identifikation)", fingerprintSection);
            fingerprintDetails.setWidthFull();
            editorLayout.add(fingerprintDetails);

            HorizontalLayout addSectionLayout = new HorizontalLayout();
            addSectionLayout.setAlignItems(Alignment.BASELINE);
            addSectionLayout.getStyle().set("margin-top", "20px");
            addSectionLayout.getStyle().set("padding-top", "20px");
            addSectionLayout.getStyle().set("border-top", "1px solid var(--lumo-contrast-10pct)");

            H4 sectionTitle = new H4("Fähigkeiten (Sektionen):");
            sectionTitle.getStyle().set("margin", "0");

            ComboBox<ModbusConfigCreatorTemplate> templateCombo = new ComboBox<>();
            templateCombo.setItems(ModbusConfigCreatorTemplate.getAll());
            templateCombo.setItemLabelGenerator(ModbusConfigCreatorTemplate::name);
            templateCombo.setPlaceholder("Template wählen...");

            Button addSectionBtn = new Button("Sektion hinzufügen", VaadinIcon.PLUS.create(), e -> {
                ModbusConfigCreatorTemplate selectedTemplate = templateCombo.getValue();
                if (selectedTemplate != null) {
                    // Verwende als Key einfach die template.id() für den Editor
                    if (activeSectionEditors.containsKey(selectedTemplate.id())) {
                        Notification.show("Sektion existiert bereits!");
                        return;
                    }
                    addSectionToView(selectedTemplate, new ModbusConfig.ConfigSection(selectedTemplate.id(), selectedTemplate.name(), new HashMap<>()));
                }
            });
            addSectionBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);

            addSectionLayout.add(sectionTitle, templateCombo, addSectionBtn);
            editorLayout.add(addSectionLayout);
            editorLayout.add(sectionsContainer);

            if (config.getSections() != null) {
                for (Map.Entry<String, ModbusConfig.ConfigSection> entry : config.getSections().entrySet()) {
                    ModbusConfigCreatorTemplate tpl = ModbusConfigCreatorTemplate.byId(entry.getValue().getTemplateId());
                    if (tpl != null) {
                        addSectionToView(tpl, entry.getValue());
                    }
                }
            }

        } catch (Exception e) {
            Notification.show("Fehler beim Öffnen: " + e.getMessage());
        }
    }

    private void addSectionToView(ModbusConfigCreatorTemplate template, ModbusConfig.ConfigSection sectionData) {
        SectionEditorBlock block = new SectionEditorBlock(template, sectionData);
        activeSectionEditors.put(template.id(), block);
        sectionsContainer.add(block);
    }

    private void saveCurrentProfile() {
        ModbusConfig.Fingerprint fingerprint = fingerprintSection.buildFingerprint();

        Map<String, ModbusConfig.ConfigSection> finalSections = new HashMap<>();
        for (Map.Entry<String, SectionEditorBlock> entry : activeSectionEditors.entrySet()) {
            finalSections.put(entry.getKey(), entry.getValue().buildConfigSection());
        }

        ModbusConfig newConfig = new ModbusConfig(fingerprint, finalSections);

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

                Object rawObj = testSection.getClient().read(entry);
                double raw = 0.0;
                if (rawObj instanceof Number n) {
                    raw = n.doubleValue();
                } else if (rawObj instanceof String s) {
                    raw = Double.parseDouble(s.trim());
                }

                return FormulaEngine.evaluate(raw, entry.formula(), name -> getLiveValueForField(name, visited, contextBlock));
            } catch (Exception e) { return 0.0; }
        }).orElse(0.0);
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {

    }

    class SectionEditorBlock extends Details {
        private final ModbusConfigCreatorTemplate template;
        @Getter
        private final List<FieldRow> fieldRows = new ArrayList<>();

        public SectionEditorBlock(ModbusConfigCreatorTemplate template, ModbusConfig.ConfigSection sectionData) {
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

            Span hName = new Span("Feldname"); hName.setWidth("130px");
            Span hAddress = new Span("Register"); hAddress.setWidth("80px");
            Span hSize = new Span("Länge"); hSize.setWidth("70px");
            Span hScale = new Span("Faktor"); hScale.setWidth("70px");
            Span hFormula = new Span("Formel"); hFormula.setWidth("90px");
            Span hType = new Span("Datentyp"); hType.setWidth("100px");
            Span hOp = new Span("Operation"); hOp.setWidth("160px");
            Span hOrder = new Span("Byte Order"); hOrder.setWidth("110px");
            Span hLive = new Span("Live-Wert"); hLive.setWidth("110px");

            for (Span span : List.of(hName, hAddress, hSize, hScale, hFormula, hType, hOp, hOrder, hLive)) {
                span.getStyle().set("font-weight", "bold");
            }
            tableHeader.add(hName, hAddress, hSize, hScale, hFormula, hType, hOp, hOrder, hLive);
            contentLayout.add(tableHeader);

            for (RequiredField reqField : template.requiredFields()) {
                ModbusConfig.Entry<?> existingEntry = null;
                try {
                    existingEntry = sectionData.getEntryForId(reqField.field());
                } catch (NoSuchElementException ignored) {}

                FieldRow row = new FieldRow(reqField, existingEntry, this);
                fieldRows.add(row);
                contentLayout.add(row);
            }

            setContent(contentLayout);
            setOpened(true);
        }

        public void disposeSubscriptions() {
            fieldRows.forEach(FieldRow::disposeSubscription);
        }

        public ModbusConfig.ConfigSection buildConfigSection() {
            Map<String, ModbusConfig.Entry<?>> entries = new HashMap<>();
            for (FieldRow row : fieldRows) {
                var entry = row.buildEntry();
                if (entry != null) {
                    entries.put(row.getFieldName(), entry);
                }
            }
            return new ModbusConfig.ConfigSection(template.id(), template.name(), entries);
        }
    }

    class FieldRow extends HorizontalLayout {
        private final RequiredField requiredField;
        private final SectionEditorBlock parentBlock;
        private final IntegerField startAddressField = new IntegerField();
        private final IntegerField sizeField = new IntegerField();
        private final NumberField scaleFactorField = new NumberField();
        private final TextField formulaField = new TextField();
        private final ComboBox<ModbusParameterType<?>> parameterTypeField = new ComboBox<>();
        private final ComboBox<ModbusReadOperationType> operationType = new ComboBox<>();
        private final ComboBox<ByteOrder> byteOrderField = new ComboBox<>();
        private final TextField liveValueDisplay = new TextField();
        private Disposable subscription;

        public FieldRow(RequiredField requiredField, ModbusConfig.Entry<?> entry, SectionEditorBlock parentBlock) {
            this.requiredField = requiredField;
            this.parentBlock = parentBlock;
            setWidthFull();
            setAlignItems(Alignment.CENTER);
            getStyle().set("border-bottom", "1px solid var(--lumo-contrast-10pct)");

            Span nameLabel = new Span(requiredField.field());
            nameLabel.setWidth("130px");

            startAddressField.setWidth("80px");
            sizeField.setWidth("70px");
            scaleFactorField.setWidth("70px");
            formulaField.setWidth("90px");
            parameterTypeField.setWidth("100px");
            operationType.setWidth("160px");
            byteOrderField.setWidth("110px");
            liveValueDisplay.setWidth("110px");
            liveValueDisplay.setReadOnly(true);

            parameterTypeField.setItems(ModbusParameterType.values());
            parameterTypeField.setItemLabelGenerator(ModbusParameterType::identifier);
            operationType.setItems(ModbusReadOperationType.values());
            operationType.setItemLabelGenerator(ModbusReadOperationType::getId);
            byteOrderField.setItems(ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN);

            if (entry != null) {
                startAddressField.setValue(entry.startAddress());
                sizeField.setValue(entry.size());
                scaleFactorField.setValue((double) entry.scaleFactor());
                formulaField.setValue(entry.formula());
                parameterTypeField.setValue(entry.modbusParameterType());
                operationType.setValue(entry.readOperationType());
                byteOrderField.setValue(entry.byteOrder());
            } else {
                startAddressField.setValue(40001);
                sizeField.setValue(1);
                scaleFactorField.setValue(1.0);
                formulaField.setValue("x");
                parameterTypeField.setValue(ModbusParameterType.INT32);
                operationType.setValue(ModbusReadOperationType.READ_HOLDING_REGISTER);
                byteOrderField.setValue(ByteOrder.BIG_ENDIAN);
            }

            add(nameLabel, startAddressField, sizeField, scaleFactorField, formulaField, parameterTypeField, operationType, byteOrderField, liveValueDisplay);
        }

        @Override
        protected void onAttach(AttachEvent attachEvent) {
            super.onAttach(attachEvent);
            subscription = Flux.defer(() -> Mono.fromRunnable(() -> {
                String displayStr = "---";
                if (testSection.getClient() != null) {
                    try {
                        double rawEvaluatedValue = ModbusConfigView.this.getLiveValueForField(requiredField.field(), new HashSet<>(), parentBlock);
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

        public void disposeSubscription() {
            if (subscription != null && !subscription.isDisposed()) {
                subscription.dispose();
            }
        }

        public String getFieldName() { return requiredField.field(); }

        public ModbusConfig.Entry<?> buildEntry() {
            try {
                return new ModbusConfig.Entry<>(
                        startAddressField.getValue(), sizeField.getValue(), scaleFactorField.getValue().floatValue(),
                        formulaField.getValue(), parameterTypeField.getValue(), operationType.getValue(), byteOrderField.getValue()
                );
            } catch (Exception e) { return null; }
        }
    }

    public static class TestSection extends Div {
        @Getter
        private TCPModbusClient client;
        private final TextField ip = new TextField("IP Address");
        private final IntegerField port = new IntegerField("Port");
        private final IntegerField slaveId = new IntegerField("Slave ID");
        private final Button connect = new Button("Test Connection");

        public TestSection() {
            ip.setWidth("150px"); port.setWidth("80px"); slaveId.setWidth("80px");
            ip.addThemeName("small"); port.addThemeName("small"); slaveId.addThemeName("small");
            ip.setValue("192.168.178.50"); port.setValue(502); slaveId.setValue(1);
            connect.addThemeVariants(ButtonVariant.LUMO_SMALL);
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
                        client = new TCPModbusClient(ip.getValue(), port.getValue(), slaveId.getValue());
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
            HorizontalLayout layout = new HorizontalLayout(ip, port, slaveId, connect);
            layout.setAlignItems(Alignment.BASELINE);
            add(layout);
        }

        @Override
        protected void onDetach(DetachEvent detachEvent) {
            super.onDetach(detachEvent);
            if (client != null) {
                try { client.close(); } catch (Exception ignored) {}
            }
        }
    }

    public static class FingerprintSection extends Div {
        private final IntegerField startAddressField = new IntegerField("Adresse");
        private final IntegerField sizeField = new IntegerField("Länge");
        private final ComboBox<ModbusParameterType<?>> parameterTypeField = new ComboBox<>("Datentyp");
        private final ComboBox<ModbusReadOperationType> operationType = new ComboBox<>("Operation");
        private final ComboBox<ByteOrder> byteOrderField = new ComboBox<>("Byte Order");
        private final TextField expectedValueField = new TextField("Erwarteter Wert");
        private final Button testFingerprintBtn = new Button("Fingerprint Testen");

        public FingerprintSection(TestSection testSection) {
            startAddressField.setWidth("100px"); sizeField.setWidth("80px"); parameterTypeField.setWidth("120px");
            operationType.setWidth("180px"); byteOrderField.setWidth("120px"); expectedValueField.setWidth("150px");

            for (HasTheme comp : List.of(startAddressField, sizeField, parameterTypeField, operationType, byteOrderField, expectedValueField)) comp.addThemeName("small");
            testFingerprintBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);

            parameterTypeField.setItems(ModbusParameterType.values());
            parameterTypeField.setItemLabelGenerator(ModbusParameterType::identifier);
            operationType.setItems(ModbusReadOperationType.values());
            operationType.setItemLabelGenerator(ModbusReadOperationType::getId);
            byteOrderField.setItems(ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN);

            testFingerprintBtn.addClickListener(e -> {
                if (testSection.getClient() == null) {
                    Notification.show("Bitte zuerst Test-Verbindung herstellen!"); return;
                }
                try {
                    ModbusConfig.Fingerprint fp = buildFingerprint();
                    boolean match = testSection.getClient().verifyFingerprint(fp);
                    if (match) Notification.show("Fingerprint passt! Gerät erkannt.", 3000, Notification.Position.MIDDLE);
                    else Notification.show("Fingerprint FEHLSCHLAG. Anderes Gerät?", 3000, Notification.Position.MIDDLE);
                } catch (Exception ex) { Notification.show("Fehler: " + ex.getMessage()); }
            });

            HorizontalLayout layout = new HorizontalLayout(startAddressField, sizeField, parameterTypeField, operationType, byteOrderField, expectedValueField, testFingerprintBtn);
            layout.setAlignItems(Alignment.BASELINE);
            add(layout);
        }

        public void setFingerprint(ModbusConfig.Fingerprint fp) {
            if (fp == null) return;
            startAddressField.setValue(fp.address());
            sizeField.setValue(fp.size());
            parameterTypeField.setValue(fp.parameterType());
            operationType.setValue(fp.operationType());
            byteOrderField.setValue(fp.byteOrder());
            expectedValueField.setValue(fp.expectedValue());
        }

        public ModbusConfig.Fingerprint buildFingerprint() {
            return new ModbusConfig.Fingerprint(startAddressField.getValue(), sizeField.getValue(), parameterTypeField.getValue(), operationType.getValue(), byteOrderField.getValue(), expectedValueField.getValue());
        }
    }
}