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
import de.verdox.pv_miner_extensions.restpv.RestPVClient;
import de.verdox.pv_miner_extensions.restpv.config.*;
import de.verdox.vserializer.json.JsonSerializerContext;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.logging.Logger;

@Route(value = "config/pv/rest")
@PageTitle("Solarminer.app - Rest-API Config Editor")
public class RestPVConfigView extends VerticalLayout implements LocaleChangeObserver {
    private static final Logger LOGGER = Logger.getLogger(RestPVConfigView.class.getName());

    private final RestConfigStorage storage;
    private final ConfigFetcherService configFetcherService;
    private final ListBox<String> configSelection = new ListBox<>();
    private final VerticalLayout communityLayout = new VerticalLayout();
    private final VerticalLayout editorLayout = new VerticalLayout();

    private final H2 sidebarTitle = new H2();
    private final ComboBox<RestConfigCreatorTemplate> templateSelection = new ComboBox<>();
    private final TextField newConfigName = new TextField();
    private final Span uploadDropLabel = new Span();
    private final Button uploadButton = new Button();

    private final TestSection testSection = new TestSection();

    private String currentConfigName;
    private RestConfigCreatorTemplate currentTemplate;
    private final List<CompactRestFieldRow> currentFieldRows = new ArrayList<>();

    public RestPVConfigView(RestConfigStorage storage, ConfigFetcherService configFetcherService) {
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

        templateSelection.setItems(List.of(RestConfigCreatorTemplate.HOME_ASSISTANT_PV));
        templateSelection.setItemLabelGenerator(RestConfigCreatorTemplate::name);
        templateSelection.setWidthFull();
        templateSelection.addThemeName("small");
        templateSelection.setValue(RestConfigCreatorTemplate.HOME_ASSISTANT_PV);
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
                RestPVConfig importedConfig = RestPVConfig.SERIALIZER.deserialize(serializationElement);

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
        sidebarTitle.setText(getTranslation("rest.sidebar.title", "REST-PV Konfigurator"));
        templateSelection.setPlaceholder(getTranslation("rest.sidebar.template.placeholder", "Template wählen..."));
        newConfigName.setPlaceholder(getTranslation("rest.sidebar.new_config.placeholder", "Name..."));
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
            if (!profile.supportedProtocols().contains("Rest-API")) continue;

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
        configFetcherService.getRestPVConfig(profileName).ifPresentOrElse(config -> {
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

    private void createNewConfig(RestConfigCreatorTemplate template, String name) {
        try {
            this.currentTemplate = template;
            RestPVConfig newConfig = template.createTemplateConfig();
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
                double raw = testSection.getClient().read(entry);
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
        currentFieldRows.forEach(CompactRestFieldRow::disposeSubscription);
        currentFieldRows.clear();

        try {
            RestPVConfig config = storage.loadConfig(currentTemplate, configName);
            HorizontalLayout header = new HorizontalLayout();
            header.setWidthFull();
            header.setAlignItems(Alignment.CENTER);
            header.setJustifyContentMode(JustifyContentMode.BETWEEN);

            H2 title = new H2(configName + " (" + currentTemplate.name() + ")");
            title.getStyle().set("margin", "0");

            HorizontalLayout actions = new HorizontalLayout();

            StreamResource resource = new StreamResource(configName + ".json", () -> {
                try {
                    RestPVConfig configToExport = storage.loadConfig(currentTemplate, configName);
                    JsonSerializerContext ctx = new JsonSerializerContext();
                    String json = ctx.toJsonString(RestPVConfig.SERIALIZER.serialize(ctx, configToExport));
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

            Details testDetails = new Details(getTranslation("rest.test.title", "Live-Test REST-Verbindung"));
            testDetails.setWidthFull();
            testDetails.setContent(testSection);
            editorLayout.add(testDetails);

            HorizontalLayout tableHeader = new HorizontalLayout();
            tableHeader.setWidthFull();
            tableHeader.setSpacing(true);
            tableHeader.getStyle().set("border-bottom", "2px solid var(--lumo-contrast-20pct)");
            tableHeader.getStyle().set("padding-bottom", "4px");
            tableHeader.getStyle().set("margin-top", "15px");

            Span hName = new Span(getTranslation("rest.header.name", "Feldname")); hName.setWidth("150px");
            Span hMethod = new Span(getTranslation("rest.header.method", "Methode")); hMethod.setWidth("90px");
            Span hPath = new Span(getTranslation("rest.header.path", "URL Extension / Pfad")); hPath.setWidth("240px");
            Span hJsonPath = new Span(getTranslation("rest.header.jsonpath", "JSON Path")); hJsonPath.setWidth("160px");
            Span hScale = new Span(getTranslation("rest.header.scale", "Faktor")); hScale.setWidth("70px");
            Span hFormula = new Span(getTranslation("rest.header.formula", "Formel")); hFormula.setWidth("100px");
            Span hType = new Span(getTranslation("rest.header.type", "Typ")); hType.setWidth("90px");
            Span hLive = new Span(getTranslation("rest.header.live", "Live-Wert")); hLive.setWidth("120px");

            for (Span span : List.of(hName, hMethod, hPath, hJsonPath, hScale, hFormula, hType, hLive)) {
                span.getStyle().set("font-weight", "bold");
                span.getStyle().set("color", "var(--lumo-secondary-text-color)");
            }

            tableHeader.add(hName, hMethod, hPath, hJsonPath, hScale, hFormula, hType, hLive);
            editorLayout.add(tableHeader);

            for (RequiredField reqField : currentTemplate.requiredFields()) {
                RestPVConfig.Entry<?> existingEntry = null;
                try {
                    existingEntry = config.getEntryForId(reqField.field());
                } catch (NoSuchElementException ignored) {}

                CompactRestFieldRow row = new CompactRestFieldRow(reqField, existingEntry);
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
        Map<String, RestPVConfig.Entry<?>> entries = new HashMap<>();
        for (CompactRestFieldRow row : currentFieldRows) {
            var entry = row.buildEntry();
            if (entry != null) {
                entries.put(row.getFieldName(), entry);
            }
        }

        RestPVConfig newConfig = new RestPVConfig(entries);
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
        currentFieldRows.forEach(CompactRestFieldRow::disposeSubscription);
    }

    class CompactRestFieldRow extends HorizontalLayout {
        private final RequiredField requiredField;
        private final ComboBox<RestHttpMethod> httpMethod = new ComboBox<>();
        private final TextField urlExtensionField = new TextField();
        private final TextField jsonPathField = new TextField();
        private final NumberField scaleFactorField = new NumberField();
        private final TextField formulaField = new TextField();
        private final ComboBox<RestParameterType<?>> parameterTypeField = new ComboBox<>();
        private final TextField liveValueDisplay = new TextField();
        private Disposable subscription;

        public CompactRestFieldRow(RequiredField requiredField, RestPVConfig.Entry<?> entry) {
            this.requiredField = requiredField;
            setWidthFull();
            setAlignItems(Alignment.CENTER);
            setSpacing(true);
            getStyle().set("border-bottom", "1px solid var(--lumo-contrast-10pct)");
            getStyle().set("padding-bottom", "4px");

            Span nameLabel = new Span(requiredField.field());
            nameLabel.setWidth("150px");
            nameLabel.getStyle().set("font-weight", "600");

            httpMethod.setItems(RestHttpMethod.values());
            httpMethod.setWidth("90px");

            urlExtensionField.setPlaceholder("/api/states/...");
            urlExtensionField.setWidth("240px");
            urlExtensionField.setValueChangeMode(ValueChangeMode.EAGER);

            jsonPathField.setPlaceholder("state");
            jsonPathField.setWidth("160px");
            jsonPathField.setValueChangeMode(ValueChangeMode.EAGER);

            scaleFactorField.setWidth("70px");
            scaleFactorField.setValueChangeMode(ValueChangeMode.EAGER);

            formulaField.setPlaceholder("x");
            formulaField.setWidth("100px");
            formulaField.setValueChangeMode(ValueChangeMode.EAGER);

            parameterTypeField.setItems(RestParameterType.INT, RestParameterType.LONG, RestParameterType.FLOAT, RestParameterType.DOUBLE);
            parameterTypeField.setItemLabelGenerator(RestParameterType::identifier);
            parameterTypeField.setWidth("90px");

            liveValueDisplay.setReadOnly(true);
            liveValueDisplay.setWidth("120px");
            liveValueDisplay.addThemeName("align-right");
            liveValueDisplay.getStyle().set("font-family", "monospace");

            applySmallTheme(httpMethod, urlExtensionField, jsonPathField, scaleFactorField, formulaField, parameterTypeField, liveValueDisplay);

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

            add(nameLabel, httpMethod, urlExtensionField, jsonPathField, scaleFactorField, formulaField, parameterTypeField, liveValueDisplay);
        }

        @Override
        protected void onAttach(AttachEvent attachEvent) {
            super.onAttach(attachEvent);
            subscription = Flux.defer(() -> Mono.fromRunnable(() -> {
                String displayStr = "---";

                if (testSection.getClient() != null) {
                    try {
                        double rawEvaluatedValue = getLiveValueForField(requiredField.field(), new HashSet<>());

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

        public RestPVConfig.Entry<?> buildEntry() {
            try {
                return new RestPVConfig.Entry<>(
                        urlExtensionField.getValue(),
                        httpMethod.getValue(),
                        jsonPathField.getValue(),
                        scaleFactorField.getValue().floatValue(),
                        formulaField.getValue(),
                        parameterTypeField.getValue()
                );
            } catch (Exception e) {
                return null;
            }
        }
    }

    public static class TestSection extends Div {
        private RestPVClient client;
        private final TextField url = new TextField("Base URL", "http://192.168.178.50:8123", "");
        private final TextField token = new TextField("API Token");
        private final Button connect = new Button("Test Connection");

        public TestSection() {
            url.setWidth("280px");
            token.setWidth("250px");
            token.setPlaceholder("Bearer Token...");

            url.addThemeName("small");
            token.addThemeName("small");
            connect.addThemeVariants(ButtonVariant.LUMO_SMALL);

            connect.setDisableOnClick(true);

            connect.addClickListener(e -> {
                connect.setText("Connecting...");
                var loadingIcon = VaadinIcon.REFRESH.create();
                loadingIcon.getStyle().set("animation", "spin 1s linear infinite");
                connect.setIcon(loadingIcon);
                connect.removeThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_ERROR);

                var ui = UI.getCurrent();
                String urlValue = url.getValue();
                String tokenValue = token.getValue();

                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        if (client != null) {
                            client.close();
                        }
                        client = new RestPVClient(urlValue, tokenValue);
                        client.ping();

                        ui.access(() -> {
                            connect.setText("Connected");
                            connect.setIcon(VaadinIcon.CHECK.create());
                            connect.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
                            connect.setEnabled(true);
                            Notification.show("Connection successful", 2000, Notification.Position.TOP_END);
                        });
                    } catch (Exception ex) {
                        ui.access(() -> {
                            connect.setText("Connection Failed");
                            connect.setIcon(VaadinIcon.CLOSE.create());
                            connect.addThemeVariants(ButtonVariant.LUMO_ERROR);
                            connect.setEnabled(true);
                            Notification.show("Connection failed", 3000, Notification.Position.MIDDLE);
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

            HorizontalLayout layout = new HorizontalLayout(url, token, connect);
            layout.setAlignItems(Alignment.BASELINE);
            add(layout);
        }

        public RestPVClient getClient() {
            return client;
        }

        @Override
        protected void onDetach(DetachEvent detachEvent) {
            super.onDetach(detachEvent);
            if (client != null) {
                client.close();
            }
        }
    }
}