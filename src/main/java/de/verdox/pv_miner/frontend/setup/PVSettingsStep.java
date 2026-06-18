package de.verdox.pv_miner.frontend.setup;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import de.verdox.pv_miner_extensions.modbus.TCPModbusClient;
import de.verdox.pv_miner_extensions.restpv.RestPVClient;

import java.util.concurrent.CompletableFuture;

public class PVSettingsStep extends VerticalLayout implements WizardStep, LocaleChangeObserver {

    private final PVSetupStep pvSetupStep;
    private final Runnable onStateChanged;

    private FormLayout formLayout;
    private PasswordField apiTokenField;
    private IntegerField slaveIdField;

    private final Button testButton = new Button("Verbindung testen");
    private final Span testFeedback = new Span();

    private boolean connectionTestedSuccessfully = false;

    public PVSettingsStep(PVSetupStep pvSetupStep, Runnable onStateChanged) {
        this.pvSetupStep = pvSetupStep;
        this.onStateChanged = onStateChanged;

        setAlignItems(Alignment.CENTER);

        formLayout = new FormLayout();
        formLayout.setMaxWidth("500px");

        testButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        testButton.setIcon(VaadinIcon.PLUG.create());
        testFeedback.getStyle().set("font-weight", "bold");
        testFeedback.getStyle().set("margin-left", "10px");

        HorizontalLayout testLayout = new HorizontalLayout(testButton, testFeedback);
        testLayout.setAlignItems(Alignment.CENTER);
        testLayout.getStyle().set("margin-top", "20px");

        add(new Span("Bitte überprüfe die Verbindungsdaten für die Anlage:"), formLayout, testLayout);

        UI.getCurrent().getElement().executeJs(
                "if (!document.getElementById('spin-style')) {" +
                        "  var style = document.createElement('style');" +
                        "  style.id = 'spin-style';" +
                        "  style.type = 'text/css';" +
                        "  style.innerHTML = '@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }';" +
                        "  document.head.appendChild(style);" +
                        "}"
        );
    }

    @Override
    public void onEnter() {
        formLayout.removeAll();
        resetTestState();

        var selectedDevices = pvSetupStep.getSelectedValidPVDevices();
        if (selectedDevices.isEmpty()) return;

        var device = selectedDevices.iterator().next();

        TextField infoField = new TextField("Gerät / Profil");
        infoField.setValue(device.getIpAddress() + " (" + device.getSelectedConfigName() + ")");
        infoField.setReadOnly(true);
        formLayout.add(infoField);

        if ("Modbus-TCP".equals(device.getProtocol())) {
            slaveIdField = new IntegerField("Modbus Slave-ID");
            slaveIdField.setValue(device.getModbusSlaveId());
            slaveIdField.setMin(1);
            slaveIdField.setMax(255);
            slaveIdField.setRequiredIndicatorVisible(true);
            slaveIdField.setValueChangeMode(ValueChangeMode.EAGER);

            slaveIdField.addValueChangeListener(e -> {
                device.setModbusSlaveId(e.getValue());
                resetTestState();
            });
            formLayout.add(slaveIdField);

            testButton.addClickListener(e -> testModbusConnection(device));

        } else if ("Rest-API".equals(device.getProtocol())) {
            apiTokenField = new PasswordField("API Token / Passwort");
            apiTokenField.setRequired(device.isRequiresAuth());
            if (device.getRestAPIToken() != null) apiTokenField.setValue(device.getRestAPIToken());
            apiTokenField.setValueChangeMode(ValueChangeMode.EAGER);

            apiTokenField.addValueChangeListener(e -> {
                device.setRestAPIToken(e.getValue());
                resetTestState();
            });
            formLayout.add(apiTokenField);

            testButton.addClickListener(e -> testRestConnection(device));
        }
    }

    private void testModbusConnection(PVSetupStep.DiscoveredPVDevice device) {
        if (slaveIdField.getValue() == null) return;

        setLoadingState();
        var ui = UI.getCurrent();

        CompletableFuture.runAsync(() -> {
            try (TCPModbusClient client = new TCPModbusClient(device.getIpAddress(), device.getPort(), slaveIdField.getValue())) {
                ui.access(() -> setSuccessState("Verbindung erfolgreich!"));
            } catch (Exception ex) {
                ui.access(() -> setErrorState("Verbindung fehlgeschlagen: " + ex.getMessage()));
            }
        });
    }

    private void testRestConnection(PVSetupStep.DiscoveredPVDevice device) {
        setLoadingState();
        var ui = UI.getCurrent();

        String baseUrl = device.getIpAddress().startsWith("http")
                ? device.getIpAddress() + ":" + device.getPort()
                : "http://" + device.getIpAddress() + ":" + device.getPort();

        String token = apiTokenField != null ? apiTokenField.getValue() : "";

        CompletableFuture.runAsync(() -> {
            try {
                RestPVClient client = new RestPVClient(baseUrl, token);
                client.ping();
                client.close();
                ui.access(() -> setSuccessState("API Token gültig. Gerät antwortet!"));
            } catch (Exception ex) {
                ui.access(() -> setErrorState("Fehler: Unauthorized oder Gerät nicht erreichbar."));
            }
        });
    }

    private void setLoadingState() {
        testButton.setEnabled(false);
        testButton.setText("Teste...");

        var loadingIcon = VaadinIcon.REFRESH.create();
        loadingIcon.getStyle().set("animation", "spin 1s linear infinite");
        testButton.setIcon(loadingIcon);

        testFeedback.setText("");
    }

    private void setSuccessState(String message) {
        testButton.setEnabled(true);
        testButton.setText("Erneut testen");
        testButton.setIcon(VaadinIcon.CHECK.create());
        testButton.removeThemeVariants(ButtonVariant.LUMO_ERROR);
        testButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

        testFeedback.setText(message);
        testFeedback.getStyle().set("color", "var(--lumo-success-text-color)");

        connectionTestedSuccessfully = true;
        onStateChanged.run();
    }

    private void setErrorState(String message) {
        testButton.setEnabled(true);
        testButton.setText("Verbindung testen");
        testButton.setIcon(VaadinIcon.CLOSE.create());
        testButton.removeThemeVariants(ButtonVariant.LUMO_SUCCESS);
        testButton.addThemeVariants(ButtonVariant.LUMO_ERROR);

        testFeedback.setText(message);
        testFeedback.getStyle().set("color", "var(--lumo-error-text-color)");

        connectionTestedSuccessfully = false;
        onStateChanged.run();
    }

    private void resetTestState() {
        connectionTestedSuccessfully = false;
        testButton.setEnabled(true);
        testButton.setText("Verbindung testen");
        testButton.setIcon(VaadinIcon.PLUG.create());
        testButton.removeThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_ERROR);
        testButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        testFeedback.setText("");
        onStateChanged.run();
    }

    @Override
    public boolean isValid() {
        return connectionTestedSuccessfully;
    }

    @Override
    public String getTitleTranslationKey() {
        return "setup.pv.settings.title";
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        //TODO: Make locale aware!
    }
}