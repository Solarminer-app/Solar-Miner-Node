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
import de.verdox.pv_miner.frontend.user.UserSessionContext;
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
public class ModbusConfigView extends VerticalLayout {
    private final ModbusConfigStorage storage;
    private final ConfigFetcherService configFetcherService;
    private final UserSessionContext userSessionContext;
    private final ListBox<String> configSelection = new ListBox<>();
    private final VerticalLayout editorLayout = new VerticalLayout();

    private final H2 sidebarTitle = new H2();
    private final TextField newConfigName = new TextField();
    private final H4 localLabel = new H4();
    private final Button addConfigBtn = new Button(VaadinIcon.PLUS.create());

    private final VerticalLayout sectionsContainer = new VerticalLayout();
    private final Map<String, SectionEditorBlock> activeSectionEditors = new HashMap<>();

    private final TestSection testSection;
    private final FingerprintSection fingerprintSection;
    private final IntegerField addressOffset = new IntegerField();

    private String currentConfigName;

    public ModbusConfigView(ModbusConfigStorage storage, ConfigFetcherService configFetcherService, UserSessionContext userSessionContext) {
        this.storage = storage;
        this.configFetcherService = configFetcherService;
        this.userSessionContext = userSessionContext;
        this.testSection = new TestSection(userSessionContext);
        this.fingerprintSection = new FingerprintSection(testSection, userSessionContext);

        setSizeFull();
        setPadding(false);

        SplitLayout splitLayout = new SplitLayout();
        splitLayout.setSizeFull();
        splitLayout.setSplitterPosition(20);

        VerticalLayout sidebar = new VerticalLayout();
        sidebar.add(sidebarTitle);

        HorizontalLayout addConfigLayout = new HorizontalLayout();
        addConfigLayout.setWidthFull();
        newConfigName.setWidthFull();
        newConfigName.addThemeName("small");

        addConfigBtn.addClickListener(e -> {
            if (!newConfigName.isEmpty()) {
                createNewProfile(newConfigName.getValue());
                newConfigName.clear();
            }
        });
        addConfigBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
        addConfigLayout.add(newConfigName, addConfigBtn);
        sidebar.add(addConfigLayout);

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
        updateTexts();
    }

    private void updateTexts() {
        sidebarTitle.setText(getTranslation("config.modbus.title", "Modbus Profile"));
        newConfigName.setPlaceholder(getTranslation("config.placeholder.new_profile", "z.B. Fronius Gen24..."));
        localLabel.setText(getTranslation("config.sidebar.local_profiles", "Lokale Profile"));
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
            Notification.show(getTranslation("config.error.load", "Fehler beim Laden: ") + e.getMessage());
        }
    }

    private void createNewProfile(String name) {
        try {
            ModbusConfig newConfig = new ModbusConfig(
                    new ModbusConfig.Fingerprint(40000, 1, ModbusParameterType.INT32, ModbusReadOperationType.READ_HOLDING_REGISTER, ByteOrder.BIG_ENDIAN, ""),
                    new HashMap<>(),
                    addressOffset.getValue()
            );
            storage.save(name, newConfig);
            refreshConfigList();
            configSelection.setValue(name);
        } catch (IOException e) {
            Notification.show(getTranslation("config.error.create", "Fehler beim Erstellen."));
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

            H2 title = new H2(getTranslation("config.profile.title", "Profil: ") + configName);
            title.getStyle().set("margin", "0");

            Button saveBtn = new Button(getTranslation("btn.save_profile", "Profil Speichern"), VaadinIcon.CHECK.create(), e -> saveCurrentProfile());
            saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            header.add(title, saveBtn);
            editorLayout.add(header);

            Details testDetails = new Details(getTranslation("config.section.live_test", "Live-Test Verbindung"), testSection);
            testDetails.setWidthFull();
            editorLayout.add(testDetails);

            fingerprintSection.setFingerprint(config.getFingerprint());
            Details fingerprintDetails = new Details(getTranslation("config.section.fingerprint", "Geräte-Fingerprint (Identifikation)"), fingerprintSection);
            fingerprintDetails.setWidthFull();
            editorLayout.add(fingerprintDetails);

            HorizontalLayout addSectionLayout = new HorizontalLayout();
            addSectionLayout.setAlignItems(Alignment.BASELINE);
            addSectionLayout.getStyle().set("margin-top", "20px");
            addSectionLayout.getStyle().set("padding-top", "20px");
            addSectionLayout.getStyle().set("border-top", "1px solid var(--lumo-contrast-10pct)");

            H4 sectionTitle = new H4(getTranslation("config.section.capabilities", "Fähigkeiten (Sektionen):"));
            sectionTitle.getStyle().set("margin", "0");


            ComboBox<ModbusConfigCreatorTemplate> templateCombo = new ComboBox<>();
            templateCombo.setItems(ModbusConfigCreatorTemplate.getAll());
            templateCombo.setItemLabelGenerator(ModbusConfigCreatorTemplate::name);
            templateCombo.setPlaceholder(getTranslation("config.placeholder.select_template", "Template wählen..."));

            Button addSectionBtn = new Button(getTranslation("btn.add_section", "Sektion hinzufügen"), VaadinIcon.PLUS.create(), e -> {
                ModbusConfigCreatorTemplate selectedTemplate = templateCombo.getValue();
                if (selectedTemplate != null) {
                    openNameDialog(selectedTemplate);
                }
            });
            addSectionBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);

            addSectionLayout.add(sectionTitle, templateCombo, addSectionBtn, addressOffset);
            editorLayout.add(addSectionLayout);
            editorLayout.add(sectionsContainer);

            // Bestehende Sektionen laden (Map Key ist die sectionId)
            if (config.getSections() != null) {
                for (Map.Entry<String, ModbusConfig.ConfigSection> entry : config.getSections().entrySet()) {
                    ModbusConfigCreatorTemplate tpl = ModbusConfigCreatorTemplate.byId(entry.getValue().getTemplateId());
                    if (tpl != null) {
                        addSectionToView(entry.getKey(), tpl, entry.getValue());
                    }
                }
            }

        } catch (Exception e) {
            Notification.show(getTranslation("config.error.open", "Fehler beim Öffnen: ") + e.getMessage());
        }
    }

    private void openNameDialog(ModbusConfigCreatorTemplate selectedTemplate) {
        Dialog nameDialog = new Dialog();
        nameDialog.setHeaderTitle(getTranslation("config.dialog.name_title", "Name der Sektion"));

        TextField sectionName = new TextField(getTranslation("config.dialog.name_label", "Bezeichnung (z.B. Batterie 2)"));
        sectionName.setValue(selectedTemplate.name());
        sectionName.setWidthFull();

        Button confirm = new Button(getTranslation("btn.create", "Erstellen"), ev -> {
            String name = sectionName.getValue().isEmpty() ? selectedTemplate.name() : sectionName.getValue();
            String newId = UUID.randomUUID().toString(); // Eindeutige ID für die Map
            addSectionToView(newId, selectedTemplate, new ModbusConfig.ConfigSection(selectedTemplate.id(), name, new HashMap<>()));
            nameDialog.close();
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        nameDialog.add(new VerticalLayout(sectionName));
        nameDialog.getFooter().add(new Button(getTranslation("btn.cancel", "Abbrechen"), ev -> nameDialog.close()), confirm);
        nameDialog.open();
    }

    private void addSectionToView(String sectionId, ModbusConfigCreatorTemplate template, ModbusConfig.ConfigSection sectionData) {
        SectionEditorBlock block = new SectionEditorBlock(sectionId, template, sectionData);
        activeSectionEditors.put(sectionId, block);
        sectionsContainer.add(block);
    }

    private void saveCurrentProfile() {
        ModbusConfig.Fingerprint fingerprint = fingerprintSection.buildFingerprint();

        Map<String, ModbusConfig.ConfigSection> finalSections = new HashMap<>();
        for (Map.Entry<String, SectionEditorBlock> entry : activeSectionEditors.entrySet()) {
            finalSections.put(entry.getKey(), entry.getValue().buildConfigSection());
        }

        ModbusConfig newConfig = new ModbusConfig(fingerprint, finalSections, addressOffset.getValue());

        try {
            storage.save(currentConfigName, newConfig);
            Notification.show(getTranslation("config.success.saved", "Profil erfolgreich gespeichert!"), 2000, Notification.Position.TOP_END);
        } catch (IOException e) {
            Notification.show(getTranslation("config.error.save", "Fehler beim Speichern!"));
        }
    }

    public double getLiveValueForField(String fieldName, Set<String> visited, SectionEditorBlock contextBlock) {
        if (visited.contains(fieldName)) { return 0.0; }
        visited.add(fieldName);

        return contextBlock.getFieldRows().stream().filter(row -> row.getFieldName().equals(fieldName)).findFirst().map(row -> {
            try {
                var entry = row.buildEntry();
                if (entry == null || testSection.getClient() == null) { return 0.0; }

                Object rawObj = testSection.getClient().read(addressOffset.getValue(), entry);
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

    class SectionEditorBlock extends Details {
        private final ModbusConfigCreatorTemplate template;
        private final String sectionId;
        private final TextField sectionNameField;
        @Getter
        private final List<FieldRow> fieldRows = new ArrayList<>();

        public SectionEditorBlock(String sectionId, ModbusConfigCreatorTemplate template, ModbusConfig.ConfigSection sectionData) {
            this.sectionId = sectionId;
            this.template = template;

            this.sectionNameField = new TextField(getTranslation("config.field.section_name", "Anzeigename"));
            this.sectionNameField.setValue(sectionData.getName() != null ? sectionData.getName() : template.name());

            setSummaryText("⚙️ " + this.sectionNameField.getValue());
            this.sectionNameField.addValueChangeListener(e -> setSummaryText("⚙️ " + e.getValue()));

            setWidthFull();
            getStyle().set("margin-top", "10px");
            getStyle().set("border", "1px solid var(--lumo-contrast-20pct)");
            getStyle().set("border-radius", "8px");

            VerticalLayout contentLayout = new VerticalLayout();
            contentLayout.setPadding(true);

            HorizontalLayout sectionHeader = new HorizontalLayout(this.sectionNameField);
            sectionHeader.setAlignItems(Alignment.BASELINE);

            Button removeSectionBtn = new Button(getTranslation("btn.remove_section", "Sektion entfernen"), VaadinIcon.TRASH.create(), e -> {
                disposeSubscriptions();
                activeSectionEditors.remove(this.sectionId);
                sectionsContainer.remove(this);
            });
            removeSectionBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            sectionHeader.add(removeSectionBtn);
            contentLayout.add(sectionHeader);

            HorizontalLayout tableHeader = new HorizontalLayout();
            tableHeader.setWidthFull();
            tableHeader.getStyle().set("border-bottom", "2px solid var(--lumo-contrast-20pct)");

            Span hName = new Span(getTranslation("config.table.field_name", "Feldname")); hName.setWidth("130px");
            Span hAddress = new Span(getTranslation("config.table.register", "Register")); hAddress.setWidth("80px");
            Span hSize = new Span(getTranslation("config.table.size", "Länge")); hSize.setWidth("70px");
            Span hScale = new Span(getTranslation("config.table.scale", "Faktor")); hScale.setWidth("70px");
            Span hFormula = new Span(getTranslation("config.table.formula", "Formel")); hFormula.setWidth("90px");
            Span hType = new Span(getTranslation("config.table.type", "Datentyp")); hType.setWidth("100px");
            Span hOp = new Span(getTranslation("config.table.operation", "Operation")); hOp.setWidth("160px");
            Span hOrder = new Span(getTranslation("config.table.byte_order", "Byte Order")); hOrder.setWidth("110px");
            Span hLive = new Span(getTranslation("config.table.live_value", "Live-Wert")); hLive.setWidth("110px");

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
            // Wirft (templateId, name, entries) zurück - exakt wie im ModbusConfig-Konstruktor definiert
            return new ModbusConfig.ConfigSection(template.id(), this.sectionNameField.getValue(), entries);
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
        private final TextField ip;
        private final IntegerField port;
        private final IntegerField slaveId;
        private final Button connect = new Button();

        public TestSection(UserSessionContext sessionContext) {
            ip = new TextField(getTranslation("config.field.ip", "IP Address"));
            port = new IntegerField(getTranslation("config.field.port", "Port"));
            slaveId = new IntegerField(getTranslation("config.field.slave_id", "Slave ID"));

            ip.setWidth("150px"); port.setWidth("80px"); slaveId.setWidth("80px");
            ip.addThemeName("small"); port.addThemeName("small"); slaveId.addThemeName("small");
            ip.setValue("192.168.178.50"); port.setValue(502); slaveId.setValue(1);

            connect.setText(getTranslation("btn.test_connection", "Test Connection"));
            connect.addThemeVariants(ButtonVariant.LUMO_SMALL);
            connect.setDisableOnClick(true);

            connect.addClickListener(e -> {
                connect.setText(getTranslation("btn.connecting", "Connecting..."));
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
                            connect.setText(getTranslation("btn.connected", "Connected"));
                            connect.setIcon(VaadinIcon.CHECK.create());
                            connect.addThemeVariants(ButtonVariant.LUMO_SUCCESS); connect.setEnabled(true);
                        });
                    } catch (Exception ex) {
                        ui.access(() -> {
                            connect.setText(getTranslation("btn.connection_failed", "Connection Failed"));
                            connect.setIcon(VaadinIcon.CLOSE.create());
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

    public class FingerprintSection extends Div {
        private final IntegerField startAddressField;
        private final IntegerField sizeField;
        private final ComboBox<ModbusParameterType<?>> parameterTypeField;
        private final ComboBox<ModbusReadOperationType> operationType;
        private final ComboBox<ByteOrder> byteOrderField;
        private final TextField expectedValueField;
        private final Button testFingerprintBtn;

        public FingerprintSection(TestSection testSection, UserSessionContext sessionContext) {
            startAddressField = new IntegerField(getTranslation("config.table.address", "Adresse"));
            sizeField = new IntegerField(getTranslation("config.table.size", "Länge"));
            parameterTypeField = new ComboBox<>(getTranslation("config.table.type", "Datentyp"));
            operationType = new ComboBox<>(getTranslation("config.table.operation", "Operation"));
            byteOrderField = new ComboBox<>(getTranslation("config.table.byte_order", "Byte Order"));
            expectedValueField = new TextField(getTranslation("config.field.expected_value", "Erwarteter Wert"));
            testFingerprintBtn = new Button(getTranslation("btn.test_fingerprint", "Fingerprint Testen"));

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
                    Notification.show(getTranslation("config.error.no_connection", "Bitte zuerst Test-Verbindung herstellen!")); return;
                }
                try {
                    ModbusConfig.Fingerprint fp = buildFingerprint();
                    boolean match = testSection.getClient().verifyFingerprint(addressOffset.getValue(), fp);
                    if (match) Notification.show(getTranslation("config.success.fingerprint", "Fingerprint passt! Gerät erkannt."), 3000, Notification.Position.MIDDLE);
                    else Notification.show(getTranslation("config.error.fingerprint", "Fingerprint FEHLSCHLAG. Anderes Gerät?"), 3000, Notification.Position.MIDDLE);
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