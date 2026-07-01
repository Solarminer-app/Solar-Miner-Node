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
import de.verdox.pv_miner.formula.FormulaEngine;
import de.verdox.pv_miner.util.FormatUtil;
import de.verdox.pv_miner.frontend.FrontendService;
import de.verdox.pv_miner_extensions.inverter.modbustcp.TCPModbusClient;
import de.verdox.pv_miner_extensions.inverter.modbustcp.config.*;
import de.verdox.vserializer.json.JsonSerializerContext;
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
    private final VerticalLayout communityLayout = new VerticalLayout();
    private final VerticalLayout editorLayout = new VerticalLayout();

    private final H2 sidebarTitle = new H2();
    private final ComboBox<ModbusConfigCreatorTemplate> templateSelection = new ComboBox<>();
    private final TextField newConfigName = new TextField();
    private final Span uploadDropLabel = new Span();
    private final Button uploadButton = new Button();

    private final TestSection testSection = new TestSection();
    private final FingerprintSection fingerprintSection = new FingerprintSection(testSection);

    private String currentConfigName;
    private ModbusConfigCreatorTemplate currentTemplate;
    private final List<FieldRow> currentFieldRows = new ArrayList<>();

    public ModbusConfigView(ModbusConfigStorage storage, ConfigFetcherService configFetcherService) {
        this.storage = storage;
        this.configFetcherService = configFetcherService;
        setSizeFull();
        setPadding(false);

        updateTexts();

        SplitLayout splitLayout = new SplitLayout();
        splitLayout.setSizeFull();
        splitLayout.setSplitterPosition(20);

        VerticalLayout sidebar = new VerticalLayout();
        sidebar.add(sidebarTitle);

        templateSelection.setItems(ModbusConfigCreatorTemplate.getAll());
        templateSelection.setItemLabelGenerator(ModbusConfigCreatorTemplate::name);
        templateSelection.setWidthFull();
        templateSelection.addThemeName("small");
        templateSelection.setValue(ModbusConfigCreatorTemplate.PV_SITE);
        sidebar.add(templateSelection);

        HorizontalLayout addConfigLayout = new HorizontalLayout();
        addConfigLayout.setWidthFull();
        newConfigName.setWidthFull();
        newConfigName.addThemeName("small");
        Button addConfigBtn = new Button(VaadinIcon.PLUS.create(), e -> {
            if (!newConfigName.isEmpty() && templateSelection.getValue() != null) {
                createNewConfig(templateSelection.getValue(), newConfigName.getValue());
                newConfigName.clear();
            }
        });
        addConfigBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
        addConfigLayout.add(newConfigName, addConfigBtn);
        sidebar.add(addConfigLayout);

        H4 localLabel = new H4("Lokale Konfigurationen");
        localLabel.getStyle().set("margin-bottom", "0");
        sidebar.add(localLabel);

        configSelection.addValueChangeListener(e -> openConfig(e.getValue()));
        configSelection.setWidthFull();
        sidebar.add(configSelection);

        H4 communityLabel = new H4("Community Konfigurationen");
        communityLabel.getStyle().set("margin-bottom", "0");
        communityLayout.setPadding(false);
        communityLayout.setSpacing(false);
        sidebar.add(communityLabel, communityLayout);

        refreshConfigList();

        MemoryBuffer memoryBuffer = new MemoryBuffer();
        Upload upload = new Upload(memoryBuffer);
        upload.setAcceptedFileTypes("application/json", ".json");
        upload.setDropLabel(uploadDropLabel);
        uploadButton.setIcon(VaadinIcon.UPLOAD.create());
        uploadButton.setWidthFull();
        uploadButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
        upload.setUploadButton(uploadButton);
        upload.setWidthFull();

        var ui = UI.getCurrent();
        upload.addSucceededListener(event -> {
            if (templateSelection.getValue() == null) {
                Notification.show(getTranslation("msg.import.error.no_template"), 3000, Notification.Position.MIDDLE);
                return;
            }
            String importedConfigName = event.getFileName().replaceAll("(?i)\\.json$", "");
            try {
                String jsonContent = new String(memoryBuffer.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                JsonSerializerContext context = new JsonSerializerContext();
                var serializationElement = context.fromJsonString(jsonContent);
                ModbusConfig importedConfig = ModbusConfig.SERIALIZER.deserialize(serializationElement);

                storage.save(templateSelection.getValue(), importedConfigName, importedConfig);

                ui.access(this::refreshConfigList);
                configSelection.setValue(importedConfigName);
                Notification.show(getTranslation("msg.import.success", importedConfigName), 3000, Notification.Position.TOP_END);
            } catch (Exception ex) {
                Notification.show(getTranslation("msg.import.error", ex.getMessage()), 5000, Notification.Position.MIDDLE);
            } finally {
                upload.clearFileList();
            }
        });
        sidebar.add(upload);

        splitLayout.addToPrimary(sidebar);

        editorLayout.setPadding(true);
        splitLayout.addToSecondary(editorLayout);

        add(splitLayout);
    }

    private void updateTexts() {
        sidebarTitle.setText(getTranslation("modbus.sidebar.title", "Modbus-PV Konfigurator"));
        templateSelection.setPlaceholder(getTranslation("modbus.sidebar.template.placeholder", "Template wählen..."));
        newConfigName.setPlaceholder(getTranslation("modbus.sidebar.new_config.placeholder", "Name..."));
        uploadDropLabel.setText(getTranslation("sidebar.upload.drop"));
        uploadButton.setText(getTranslation("sidebar.upload.btn"));
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        updateTexts();
        if (currentConfigName != null) {
            openConfig(currentConfigName);
        }
    }

    private void refreshConfigList() {
        if (templateSelection.getValue() == null) return;
        try {
            List<String> localConfigs = storage.getSavedConfigs(templateSelection.getValue());
            String selected = configSelection.getValue();
            configSelection.setItems(localConfigs);
            if (selected != null && localConfigs.contains(selected)) {
                configSelection.setValue(selected);
            }
            refreshCommunityList(localConfigs);
        } catch (IOException e) {
            Notification.show(getTranslation("msg.error.load", e.getMessage()));
        }
    }

    private void refreshCommunityList(List<String> localConfigs) {
        communityLayout.removeAll();
        for (var profile : configFetcherService.getCachedProfiles()) {
            if (!profile.supportedProtocols().contains("Modbus-TCP")) continue;

            HorizontalLayout row = new HorizontalLayout();
            row.setWidthFull();
            row.setAlignItems(Alignment.CENTER);
            row.setJustifyContentMode(JustifyContentMode.BETWEEN);

            Span name = new Span(profile.name());
            name.getStyle().set("font-size", "0.9em");

            Button actionBtn = new Button();
            actionBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

            if (localConfigs.contains(profile.name())) {
                actionBtn.setIcon(VaadinIcon.CHECK.create());
                actionBtn.setEnabled(false);
                actionBtn.setTooltipText("Bereits lokal gespeichert");
            } else {
                actionBtn.setIcon(VaadinIcon.DOWNLOAD.create());
                actionBtn.setTooltipText("Lokal speichern & bearbeiten");
                actionBtn.addClickListener(e -> downloadCommunityConfig(profile.name()));
            }

            row.add(name, actionBtn);
            communityLayout.add(row);
        }
    }

    private void downloadCommunityConfig(String profileName) {
        configFetcherService.getModbusConfig(profileName).ifPresentOrElse(config -> {
            try {
                storage.save(templateSelection.getValue(), profileName, config);
                Notification.show("Konfiguration lokal gespeichert", 2000, Notification.Position.TOP_END);
                refreshConfigList();
                configSelection.setValue(profileName);
            } catch (IOException ex) {
                Notification.show("Fehler beim Speichern: " + ex.getMessage());
            }
        }, () -> Notification.show("Fehler: Konfiguration nicht im Cache gefunden", 3000, Notification.Position.MIDDLE));
    }

    private void createNewConfig(ModbusConfigCreatorTemplate template, String name) {
        try {
            this.currentTemplate = template;
            ModbusConfig newConfig = template.createTemplateConfig();
            storage.save(template, name, newConfig);
            refreshConfigList();
            configSelection.setValue(name);
        } catch (IOException e) {
            Notification.show(getTranslation("msg.error.create"));
        }
    }

    public double getLiveValueForField(String fieldName, Set<String> visited) {
        if (visited.contains(fieldName)) { return 0.0; }
        visited.add(fieldName);
        return currentFieldRows.stream().filter(row -> row.getFieldName().equals(fieldName)).findFirst().map(row -> {
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

                return FormulaEngine.evaluate(raw, entry.formula(), name -> getLiveValueForField(name, visited));
            } catch (Exception e) { return 0.0; }
        }).orElse(0.0);
    }

    private void openConfig(String configName) {
        if (configName == null || templateSelection.getValue() == null) {
            editorLayout.removeAll();
            return;
        }
        this.currentConfigName = configName;
        this.currentTemplate = templateSelection.getValue();
        editorLayout.removeAll();
        currentFieldRows.forEach(FieldRow::disposeSubscription);
        currentFieldRows.clear();

        try {
            ModbusConfig config = storage.loadConfig(currentTemplate, configName);

            HorizontalLayout header = new HorizontalLayout();
            header.setWidthFull();
            header.setAlignItems(Alignment.CENTER);
            header.setJustifyContentMode(JustifyContentMode.BETWEEN);

            H2 title = new H2(configName + " (" + currentTemplate.name() + ")");
            title.getStyle().set("margin", "0");

            HorizontalLayout actions = new HorizontalLayout();

            StreamResource resource = new StreamResource(configName + ".json", () -> {
                try {
                    ModbusConfig configToExport = storage.loadConfig(currentTemplate, configName);
                    JsonSerializerContext ctx = new JsonSerializerContext();
                    String json = ctx.toJsonString(ModbusConfig.SERIALIZER.serialize(ctx, configToExport));
                    return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
                } catch (Exception ex) {
                    return new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));
                }
            });

            Anchor hiddenDownloadLink = new Anchor(resource, "");
            hiddenDownloadLink.getElement().setAttribute("download", configName + ".json");
            hiddenDownloadLink.getStyle().set("display", "none");

            Button exportBtn = new Button(getTranslation("btn.export"), VaadinIcon.DOWNLOAD.create());
            exportBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            exportBtn.addClickListener(e -> hiddenDownloadLink.getElement().callJsFunction("click"));

            Button deleteBtn = new Button(getTranslation("btn.delete"), VaadinIcon.TRASH.create(), e -> showDeleteDialog());
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);

            Button saveBtn = new Button(getTranslation("btn.save"), VaadinIcon.CHECK.create(), e -> saveCurrentConfig());
            saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

            actions.add(hiddenDownloadLink, exportBtn, deleteBtn, saveBtn);
            header.add(title, actions);
            editorLayout.add(header);

            Details testDetails = new Details(getTranslation("test.title", "Live-Test Modbus-Verbindung"), testSection);
            testDetails.setWidthFull();
            editorLayout.add(testDetails);

            fingerprintSection.setFingerprint(config.getFingerprint());
            Details fingerprintDetails = new Details("Geräte-Fingerprint (Identifikation)", fingerprintSection);
            fingerprintDetails.setWidthFull();
            editorLayout.add(fingerprintDetails);

            HorizontalLayout tableHeader = new HorizontalLayout();
            tableHeader.setWidthFull();
            tableHeader.setSpacing(true);
            tableHeader.getStyle().set("border-bottom", "2px solid var(--lumo-contrast-20pct)");
            tableHeader.getStyle().set("padding-bottom", "4px");
            tableHeader.getStyle().set("margin-top", "15px");

            Span hName = new Span(getTranslation("modbus.header.name", "Feldname")); hName.setWidth("130px");
            Span hAddress = new Span(getTranslation("modbus.header.address", "Register")); hAddress.setWidth("80px");
            Span hSize = new Span(getTranslation("modbus.header.size", "Länge")); hSize.setWidth("70px");
            Span hScale = new Span(getTranslation("modbus.header.scale", "Faktor")); hScale.setWidth("70px");
            Span hFormula = new Span(getTranslation("modbus.header.formula", "Formel")); hFormula.setWidth("90px");
            Span hType = new Span(getTranslation("modbus.header.type", "Datentyp")); hType.setWidth("100px");
            Span hOp = new Span(getTranslation("modbus.header.operation", "Operation")); hOp.setWidth("160px");
            Span hOrder = new Span(getTranslation("modbus.header.order", "Byte Order")); hOrder.setWidth("110px");
            Span hLive = new Span(getTranslation("modbus.header.live", "Live-Wert")); hLive.setWidth("110px");

            for (Span span : List.of(hName, hAddress, hSize, hScale, hFormula, hType, hOp, hOrder, hLive)) {
                span.getStyle().set("font-weight", "bold");
                span.getStyle().set("color", "var(--lumo-secondary-text-color)");
            }

            tableHeader.add(hName, hAddress, hSize, hScale, hFormula, hType, hOp, hOrder, hLive);
            editorLayout.add(tableHeader);

            for (RequiredField reqField : currentTemplate.requiredFields()) {
                ModbusConfig.Entry<?> existingEntry = null;
                try {
                    existingEntry = config.getEntryForId(reqField.field());
                } catch (NoSuchElementException ignored) {}

                FieldRow row = new FieldRow(reqField, existingEntry);
                currentFieldRows.add(row);
                editorLayout.add(row);
            }

        } catch (IOException e) {
            Notification.show(getTranslation("msg.error.open"));
        }
    }

    private void showDeleteDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("dialog.delete.title"));
        dialog.add(new Span(getTranslation("dialog.delete.confirm", currentConfigName)));

        Button cancel = new Button(getTranslation("btn.cancel"), e -> dialog.close());
        Button delete = new Button(getTranslation("btn.delete"), e -> {
            try {
                storage.delete(currentTemplate, currentConfigName);
                refreshConfigList();
                editorLayout.removeAll();
                Notification.show(getTranslation("msg.delete.success"));
                dialog.close();
            } catch (Exception ex) {
                Notification.show(getTranslation("msg.error.general", ex.getMessage()));
            }
        });
        delete.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        dialog.getFooter().add(cancel, delete);
        dialog.open();
    }

    private void saveCurrentConfig() {
        Map<String, ModbusConfig.Entry<?>> entries = new HashMap<>();
        for (FieldRow row : currentFieldRows) {
            var entry = row.buildEntry();
            if (entry != null) {
                entries.put(row.getFieldName(), entry);
            }
        }

        ModbusConfig.Fingerprint fingerprint = fingerprintSection.buildFingerprint();

        ModbusConfig newConfig = new ModbusConfig(fingerprint, entries);

        try {
            storage.save(currentTemplate, currentConfigName, newConfig);
            Notification.show(getTranslation("msg.save.success"), 2000, Notification.Position.TOP_END);
        } catch (IOException e) {
            Notification.show(getTranslation("msg.error.save"));
        }
    }

    private void applySmallTheme(HasTheme... components) {
        for (HasTheme component : components) {
            component.addThemeName("small");
        }
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        currentFieldRows.forEach(FieldRow::disposeSubscription);
        fingerprintSection.disposeSubscription();
    }

    class FieldRow extends HorizontalLayout {
        private final RequiredField requiredField;
        private final IntegerField startAddressField = new IntegerField();
        private final IntegerField sizeField = new IntegerField();
        private final NumberField scaleFactorField = new NumberField();
        private final TextField formulaField = new TextField();
        private final ComboBox<ModbusParameterType<?>> parameterTypeField = new ComboBox<>();
        private final ComboBox<ModbusReadOperationType> operationType = new ComboBox<>();
        private final ComboBox<ByteOrder> byteOrderField = new ComboBox<>();
        private final TextField liveValueDisplay = new TextField();
        private Disposable subscription;

        public FieldRow(RequiredField requiredField, ModbusConfig.Entry<?> entry) {
            this.requiredField = requiredField;
            setWidthFull();
            setAlignItems(Alignment.CENTER);
            setSpacing(true);
            getStyle().set("border-bottom", "1px solid var(--lumo-contrast-10pct)");
            getStyle().set("padding-bottom", "4px");

            Span nameLabel = new Span(requiredField.field());
            nameLabel.setWidth("130px");
            nameLabel.getStyle().set("font-weight", "600");

            startAddressField.setWidth("80px");
            startAddressField.setValueChangeMode(ValueChangeMode.EAGER);

            sizeField.setWidth("70px");
            sizeField.setValueChangeMode(ValueChangeMode.EAGER);

            scaleFactorField.setWidth("70px");
            scaleFactorField.setValueChangeMode(ValueChangeMode.EAGER);

            formulaField.setPlaceholder("x");
            formulaField.setWidth("90px");
            formulaField.setValueChangeMode(ValueChangeMode.EAGER);

            parameterTypeField.setItems(ModbusParameterType.values());
            parameterTypeField.setItemLabelGenerator(ModbusParameterType::identifier);
            parameterTypeField.setWidth("100px");

            operationType.setItems(ModbusReadOperationType.values());
            operationType.setItemLabelGenerator(ModbusReadOperationType::getId);
            operationType.setWidth("160px");

            byteOrderField.setItems(ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN);
            byteOrderField.setItemLabelGenerator(b -> b == ByteOrder.BIG_ENDIAN ? "BIG_ENDIAN" : "LITTLE_ENDIAN");
            byteOrderField.setWidth("110px");

            liveValueDisplay.setReadOnly(true);
            liveValueDisplay.setWidth("110px");
            liveValueDisplay.addThemeName("align-right");
            liveValueDisplay.getStyle().set("font-family", "monospace");

            applySmallTheme(startAddressField, sizeField, scaleFactorField, formulaField, parameterTypeField, operationType, byteOrderField, liveValueDisplay);

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
                        double rawEvaluatedValue = ModbusConfigView.this.getLiveValueForField(requiredField.field(), new HashSet<>());
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

        public String getFieldName() {
            return requiredField.field();
        }

        public ModbusConfig.Entry<?> buildEntry() {
            try {
                return new ModbusConfig.Entry<>(
                        startAddressField.getValue(),
                        sizeField.getValue(),
                        scaleFactorField.getValue().floatValue(),
                        formulaField.getValue(),
                        parameterTypeField.getValue(),
                        operationType.getValue(),
                        byteOrderField.getValue()
                );
            } catch (Exception e) {
                return null;
            }
        }
    }

    public static class FingerprintSection extends VerticalLayout {
        private final TestSection testSection;

        private final IntegerField addressField = new IntegerField("Register");
        private final IntegerField sizeField = new IntegerField("Länge");
        private final ComboBox<ModbusParameterType<?>> parameterTypeField = new ComboBox<>("Datentyp");
        private final ComboBox<ModbusReadOperationType> operationTypeField = new ComboBox<>("Operation");
        private final ComboBox<ByteOrder> byteOrderField = new ComboBox<>("Byte Order");
        private final TextField expectedValueField = new TextField("Erwarteter Wert");

        private final TextField liveValueDisplay = new TextField("Live-Wert");
        private Disposable subscription;

        public FingerprintSection(TestSection testSection) {
            this.testSection = testSection;

            setPadding(false);
            setSpacing(false);

            Span description = new Span("Gib ein Register und den dazugehörigen festen Wert (z.B. Softwareversion oder Geräte-ID) an. Dies ermöglicht dem Auto-Scanner eine garantierte 100% Erkennung deines Geräts im Netzwerk.");
            description.getStyle().set("font-size", "0.9em");
            description.getStyle().set("color", "var(--lumo-secondary-text-color)");
            description.getStyle().set("margin-bottom", "10px");

            addressField.setWidth("100px");
            addressField.setValueChangeMode(ValueChangeMode.EAGER);

            sizeField.setWidth("80px");
            sizeField.setValueChangeMode(ValueChangeMode.EAGER);

            parameterTypeField.setItems(ModbusParameterType.values());
            parameterTypeField.setItemLabelGenerator(ModbusParameterType::identifier);
            parameterTypeField.setWidth("120px");

            operationTypeField.setItems(ModbusReadOperationType.values());
            operationTypeField.setItemLabelGenerator(ModbusReadOperationType::getId);
            operationTypeField.setWidth("180px");

            byteOrderField.setItems(ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN);
            byteOrderField.setItemLabelGenerator(b -> b == ByteOrder.BIG_ENDIAN ? "BIG_ENDIAN" : "LITTLE_ENDIAN");
            byteOrderField.setWidth("120px");

            expectedValueField.setWidth("150px");
            expectedValueField.setPlaceholder("z.B. 1 oder SMARTFOX");
            expectedValueField.setValueChangeMode(ValueChangeMode.EAGER);

            liveValueDisplay.setReadOnly(true);
            liveValueDisplay.setWidth("120px");
            liveValueDisplay.addThemeName("align-right");
            liveValueDisplay.getStyle().set("font-weight", "bold");
            liveValueDisplay.getStyle().set("font-family", "monospace");

            for (HasTheme component : List.of(addressField, sizeField, parameterTypeField, operationTypeField, byteOrderField, expectedValueField, liveValueDisplay)) {
                component.addThemeName("small");
            }

            HorizontalLayout layout = new HorizontalLayout(addressField, sizeField, parameterTypeField, operationTypeField, byteOrderField, expectedValueField, liveValueDisplay);
            layout.setAlignItems(Alignment.BASELINE);

            add(description, layout);
        }

        @Override
        protected void onAttach(AttachEvent attachEvent) {
            super.onAttach(attachEvent);
            subscription = Flux.defer(() -> Mono.fromRunnable(() -> {
                String displayStr = "---";
                boolean isMatch = false;
                boolean isError = false;

                if (testSection.getClient() != null && addressField.getValue() != null) {
                    try {
                        ModbusConfig.Entry<?> dummyEntry = new ModbusConfig.Entry<>(
                                addressField.getValue(),
                                sizeField.getValue() != null ? sizeField.getValue() : 1,
                                1.0f, "x",
                                parameterTypeField.getValue() != null ? (ModbusParameterType<Integer>) parameterTypeField.getValue() : ModbusParameterType.INT32,
                                operationTypeField.getValue() != null ? operationTypeField.getValue() : ModbusReadOperationType.READ_HOLDING_REGISTER,
                                byteOrderField.getValue() != null ? byteOrderField.getValue() : ByteOrder.BIG_ENDIAN
                        );

                        Object rawObj = testSection.getClient().read(dummyEntry);

                        if (rawObj instanceof Number n) {
                            double raw = n.doubleValue();
                            if (raw == (long) raw) {
                                displayStr = String.valueOf((long) raw);
                            } else {
                                displayStr = String.valueOf(raw);
                            }
                        } else if (rawObj != null) {
                            displayStr = rawObj.toString().trim();
                        }

                        if (!expectedValueField.isEmpty() && expectedValueField.getValue().trim().equals(displayStr)) {
                            isMatch = true;
                        }
                    } catch (Exception ex) {
                        displayStr = "Error";
                        isError = true;
                    }
                }

                final String resultText = displayStr;
                final boolean finalMatch = isMatch;
                final boolean finalError = isError;

                FrontendService.scheduleUpdate(getUI(), ui -> {
                    liveValueDisplay.setValue(resultText);
                    if (finalMatch) {
                        liveValueDisplay.getStyle().set("color", "var(--lumo-success-text-color)");
                    } else if (finalError) {
                        liveValueDisplay.getStyle().set("color", "var(--lumo-error-text-color)");
                    } else {
                        liveValueDisplay.getStyle().remove("color");
                    }
                });

            })).subscribeOn(Schedulers.boundedElastic()).repeat().delayElements(Duration.ofSeconds(2)).subscribe();
        }

        public void disposeSubscription() {
            if (subscription != null && !subscription.isDisposed()) {
                subscription.dispose();
            }
        }

        public void setFingerprint(ModbusConfig.Fingerprint fp) {
            if (fp != null) {
                addressField.setValue(fp.address());
                sizeField.setValue(fp.size());
                parameterTypeField.setValue(fp.parameterType());
                operationTypeField.setValue(fp.operationType());
                byteOrderField.setValue(fp.byteOrder());
                expectedValueField.setValue(fp.expectedValue());
            } else {
                addressField.clear();
                sizeField.setValue(1);
                parameterTypeField.setValue(ModbusParameterType.INT32);
                operationTypeField.setValue(ModbusReadOperationType.READ_HOLDING_REGISTER);
                byteOrderField.setValue(ByteOrder.BIG_ENDIAN);
                expectedValueField.clear();
            }
        }

        public ModbusConfig.Fingerprint buildFingerprint() {
            if (addressField.getValue() != null && !expectedValueField.isEmpty()) {
                return new ModbusConfig.Fingerprint(
                        addressField.getValue(),
                        sizeField.getValue() != null ? sizeField.getValue() : 1,
                        parameterTypeField.getValue(),
                        operationTypeField.getValue(),
                        byteOrderField.getValue(),
                        expectedValueField.getValue()
                );
            }
            return null;
        }
    }

    public static class TestSection extends Div {
        private TCPModbusClient client;
        private final TextField ip = new TextField("IP Adresse", "192.168.178.50", "");
        private final IntegerField port = new IntegerField("Port");
        private final IntegerField slaveId = new IntegerField("Slave ID");
        private final Button connect = new Button("Test Connection");

        public TestSection() {
            ip.setWidth("180px");
            port.setWidth("90px");
            slaveId.setWidth("90px");

            port.setValue(502);
            slaveId.setValue(1);
            slaveId.setMin(1);

            ip.addThemeName("small");
            port.addThemeName("small");
            slaveId.addThemeName("small");
            connect.addThemeVariants(ButtonVariant.LUMO_SMALL);

            connect.setDisableOnClick(true);

            connect.addClickListener(e -> {
                connect.setText("Connecting...");
                var loadingIcon = VaadinIcon.REFRESH.create();
                loadingIcon.getStyle().set("animation", "spin 1s linear infinite");
                connect.setIcon(loadingIcon);
                connect.removeThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_ERROR);

                var ui = UI.getCurrent();
                String ipValue = ip.getValue();
                int portValue = port.getValue() != null ? port.getValue() : 502;
                int slaveValue = slaveId.getValue() != null ? slaveId.getValue() : 1;

                CompletableFuture.runAsync(() -> {
                    try {
                        if (client != null) {
                            client.close();
                        }
                        client = new TCPModbusClient(ipValue, portValue, slaveValue);

                        ui.access(() -> {
                            connect.setText("Connected");
                            connect.setIcon(VaadinIcon.CHECK.create());
                            connect.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
                            connect.setEnabled(true);
                            Notification.show("Modbus-Verbindung erfolgreich", 2000, Notification.Position.TOP_END);
                        });
                    } catch (Exception ex) {
                        ui.access(() -> {
                            connect.setText("Connection Failed");
                            connect.setIcon(VaadinIcon.CLOSE.create());
                            connect.addThemeVariants(ButtonVariant.LUMO_ERROR);
                            connect.setEnabled(true);
                            Notification.show("Modbus-Verbindung fehlgeschlagen", 3000, Notification.Position.MIDDLE);
                            ex.printStackTrace();
                        });
                    }
                });
            });

            UI.getCurrent().getElement().executeJs(
                    "if (!document.getElementById('spin-style')) {" +
                            "  var style = document.createElement('style');" +
                            "  style.id = 'spin-style';" +
                            "  style.type = 'text/css';" +
                            "  style.innerHTML = '@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }';" +
                            "  document.head.appendChild(style);" +
                            "}"
            );

            HorizontalLayout layout = new HorizontalLayout(ip, port, slaveId, connect);
            layout.setAlignItems(Alignment.BASELINE);
            add(layout);
        }

        public TCPModbusClient getClient() {
            return client;
        }

        @Override
        protected void onDetach(DetachEvent detachEvent) {
            super.onDetach(detachEvent);
            if (client != null) {
                try {
                    client.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}