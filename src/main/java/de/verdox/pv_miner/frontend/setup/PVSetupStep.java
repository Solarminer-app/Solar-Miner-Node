package de.verdox.pv_miner.frontend.setup;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.shared.SelectionPreservationMode;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.configfetcher.ConfigFetcherService;
import de.verdox.pv_miner.discovery.DiscoveryService;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableButton;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableSpan;
import de.verdox.pv_miner_extensions.inverter.modbustcp.config.ModbusConfigCreatorTemplate;
import de.verdox.pv_miner_extensions.inverter.modbustcp.config.ModbusConfigStorage;
import de.verdox.pv_miner_extensions.inverter.rest.config.RestConfigCreatorTemplate;
import de.verdox.pv_miner_extensions.inverter.rest.config.RestConfigStorage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class PVSetupStep extends VerticalLayout implements WizardStep {

    private final TranslatableSpan statusText;
    private final Icon radarIcon;
    private final Grid<DiscoveredPVDevice> pvGrid;

    private final List<DiscoveredPVDevice> discoveredDevices = new ArrayList<>();
    private boolean scanComplete = false;

    private final Runnable onStateChangedCallback;
    private final Consumer<String> onCreateCustomConfigCallback;

    public static class DiscoveredPVDevice {
        private final String ipAddress;
        private final int port;
        private final String protocol;
        private final boolean requiresAuth;
        private String selectedConfigName;
        private int modbusSlaveId = 1;
        private String restAPIToken = "";

        public DiscoveredPVDevice(String ipAddress, int port, String protocol, String matchedConfig, boolean requiresAuth) {
            this.ipAddress = ipAddress;
            this.port = port;
            this.protocol = protocol;
            this.selectedConfigName = matchedConfig;
            this.requiresAuth = requiresAuth;
        }

        public int getModbusSlaveId() {
            return modbusSlaveId;
        }

        public String getRestAPIToken() {
            return restAPIToken;
        }

        public void setRestAPIToken(String restAPIToken) {
            this.restAPIToken = restAPIToken;
        }

        public void setModbusSlaveId(int modbusSlaveId) {
            this.modbusSlaveId = modbusSlaveId;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public int getPort() {
            return port;
        }

        public String getProtocol() {
            return protocol;
        }

        public boolean isRequiresAuth() {
            return requiresAuth;
        }

        public String getSelectedConfigName() {
            return selectedConfigName;
        }

        public void setSelectedConfigName(String selectedConfigName) {
            this.selectedConfigName = selectedConfigName;
        }
    }

    public PVSetupStep(Runnable onStateChangedCallback, Consumer<String> onCreateCustomConfigCallback) {
        this.onStateChangedCallback = onStateChangedCallback;
        this.onCreateCustomConfigCallback = onCreateCustomConfigCallback;

        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.START);
        setHeightFull();
        setSpacing(true);

        radarIcon = VaadinIcon.SIGNAL.create();
        radarIcon.addClassName("radar-scan-animation");
        radarIcon.setSize("48px");

        statusText = new TranslatableSpan("setup.pv.scan.status.ready");
        statusText.getStyle().set("font-size", "1.1em");
        statusText.getStyle().set("font-weight", "500");

        pvGrid = new Grid<>();
        pvGrid.setWidthFull();
        pvGrid.setHeight("280px");
        pvGrid.setSelectionMode(Grid.SelectionMode.SINGLE);
        pvGrid.setSelectionPreservationMode(SelectionPreservationMode.PRESERVE_EXISTING);

        pvGrid.addComponentColumn(device -> {
            HorizontalLayout layout = new HorizontalLayout();
            layout.setAlignItems(Alignment.CENTER);
            layout.add(new Span(device.getIpAddress() + ":" + device.getPort()));
            if (device.isRequiresAuth()) {
                Icon lock = VaadinIcon.LOCK.create();
                lock.setSize("14px");
                lock.setColor("var(--lumo-error-color)");
                lock.setTooltipText(getTranslation("setup.pv.scan.auth_needed"));
                layout.add(lock);
            }
            return layout;
        }).setHeader(new TranslatableSpan("setup.pv.scan.grid.header.ip")).setSortable(true);

        pvGrid.addColumn(DiscoveredPVDevice::getProtocol).setHeader(new TranslatableSpan("setup.pv.scan.grid.header.protocol")).setSortable(true).setWidth("130px").setFlexGrow(0);

        pvGrid.addComponentColumn(device -> {
            ComboBox<String> configDropdown = new ComboBox<>();
            configDropdown.setWidthFull();
            configDropdown.setPlaceholder(getTranslation("setup.pv.scan.no_config"));

            List<String> availableProfiles = getProfilesForProtocol(device.getProtocol());
            configDropdown.setItems(availableProfiles);

            if (device.getSelectedConfigName() != null && availableProfiles.contains(device.getSelectedConfigName())) {
                configDropdown.setValue(device.getSelectedConfigName());
            }

            configDropdown.addValueChangeListener(e -> {
                device.setSelectedConfigName(e.getValue());
                onStateChangedCallback.run();
            });
            return configDropdown;
        }).setHeader(new TranslatableSpan("setup.pv.scan.grid.header.profile")).setFlexGrow(1);



        pvGrid.addSelectionListener(e -> onStateChangedCallback.run());

        HorizontalLayout fallbackActionBar = new HorizontalLayout();
        fallbackActionBar.setWidthFull();
        fallbackActionBar.setJustifyContentMode(JustifyContentMode.BETWEEN);
        fallbackActionBar.getStyle().set("margin-top", "20px");
        fallbackActionBar.getStyle().set("padding-top", "15px");
        fallbackActionBar.getStyle().set("border-top", "1px solid var(--lumo-contrast-10pct)");

        HorizontalLayout discordLayout = new HorizontalLayout();
        discordLayout.setAlignItems(Alignment.CENTER);
        Anchor discordLink = new Anchor("https://discord.solarminer.app/", "Discord Community");
        discordLink.setTarget("_blank");
        discordLayout.add(VaadinIcon.QUESTION_CIRCLE_O.create(), new TranslatableSpan("setup.pv.scan.need_help"), discordLink);

        HorizontalLayout actionButtons = new HorizontalLayout();

        var manualAddBtn = new TranslatableButton("setup.pv.scan.manual.ip", VaadinIcon.PLUS.create(), e -> openManualAddDialog());
        manualAddBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        var customConfigBtn = new TranslatableButton("setup.pv.scan.manual.create_config", VaadinIcon.TOOLS.create(), e -> openCustomConfigDialog());
        customConfigBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);

        actionButtons.add(manualAddBtn, customConfigBtn);
        fallbackActionBar.add(discordLayout, actionButtons);

        add(radarIcon, statusText, pvGrid, fallbackActionBar);
    }

    @Override
    public void onEnter() {
        startScan();
    }

    public void startScan() {
        discoveredDevices.clear();
        pvGrid.setItems(discoveredDevices);
        scanComplete = false;
        onStateChangedCallback.run();

        UI ui = UI.getCurrent();
        statusText.setText("setup.pv.scan.status.step.1");
        statusText.setTranslationParameters(1, 2);

        DiscoveryService discoveryService = SpringContextHelper.getBean(DiscoveryService.class);
        String localSubnet = "192.168.178."; // TODO: Dynamisch auslesen

        discoveryService.discoverModbusDevices(localSubnet,
                modbusDevice -> ui.access(() -> {
                    addDeviceToGrid(new DiscoveredPVDevice(modbusDevice.ipAddress(), 502, "Modbus-TCP", modbusDevice.matchingProfileName(), false));
                }),
                () -> ui.access(() -> {
                    statusText.setText("setup.pv.scan.status.step.2");
                    statusText.setTranslationParameters(2, 2);

                    discoveryService.discoverRestDevices(localSubnet,
                            restDevice -> ui.access(() -> {
                                boolean alreadyFound = discoveredDevices.stream().anyMatch(d -> d.getIpAddress().equals(restDevice.ipAddress()));
                                if (!alreadyFound) {
                                    addDeviceToGrid(new DiscoveredPVDevice(restDevice.ipAddress(), restDevice.port(), "Rest-API", restDevice.matchingProfileName(), restDevice.requiresAuth()));
                                }
                            }),
                            () -> ui.access(() -> {
                                scanComplete = true;
                                radarIcon.removeClassName("radar-scan-animation");
                                radarIcon.setColor("var(--lumo-success-color)");
                                radarIcon.getElement().setAttribute("icon", VaadinIcon.CHECK_CIRCLE.create().getElement().getAttribute("icon"));

                                if (discoveredDevices.isEmpty()) {
                                    statusText.setText("setup.pv.scan.status.nothing_found");
                                } else {
                                    statusText.setText("setup.pv.scan.status.found");
                                    statusText.setTranslationParameters(discoveredDevices.size());
                                }

                                onStateChangedCallback.run();
                            })
                    );
                })
        );
    }

    private void addDeviceToGrid(DiscoveredPVDevice device) {
        discoveredDevices.add(device);
        pvGrid.setItems(discoveredDevices);
        pvGrid.select(device);
        onStateChangedCallback.run();
    }

    /**
     * NEU: Kombiniert lokale und Community-Konfigurationen im Dropdown.
     * Lokale Namen überschreiben dank LinkedHashSet implizit Duplikate.
     */
    private List<String> getProfilesForProtocol(String protocol) {
        Set<String> uniqueProfileNames = new LinkedHashSet<>();

        try {
            if ("Modbus-TCP".equals(protocol)) {
                ModbusConfigStorage modbusStorage = SpringContextHelper.getBean(ModbusConfigStorage.class);
                if (modbusStorage != null) {
                    var pvSiteTemplate = ModbusConfigCreatorTemplate.PV_SITE;
                    uniqueProfileNames.addAll(modbusStorage.getSavedConfigs(pvSiteTemplate));
                }
            } else if ("Rest-API".equals(protocol)) {
                RestConfigStorage restStorage = SpringContextHelper.getBean(RestConfigStorage.class);
                if (restStorage != null) {
                    var pvSiteTemplate = RestConfigCreatorTemplate.HOME_ASSISTANT_PV;
                    uniqueProfileNames.addAll(restStorage.getSavedConfigs(pvSiteTemplate));
                }
            }
        } catch (Exception ignored) {
        }

        ConfigFetcherService fetcher = SpringContextHelper.getBean(ConfigFetcherService.class);
        if (fetcher != null && fetcher.getCachedProfiles() != null) {
            fetcher.getCachedProfiles().stream()
                    .filter(p -> p.supportedProtocols().contains(protocol))
                    .map(DeviceProfile::name)
                    .forEach(uniqueProfileNames::add);
        }

        return new ArrayList<>(uniqueProfileNames);
    }

    private void openManualAddDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("setup.pv.scan.dialog.add"));

        TextField ipField = new TextField(getTranslation("setup.pv.scan.dialog.ip"));
        ipField.setPlaceholder("192.168.178.50");

        IntegerField portField = new IntegerField(getTranslation("setup.pv.scan.dialog.port"));

        RadioButtonGroup<String> protocolGroup = new RadioButtonGroup<>(getTranslation("setup.pv.scan.dialog.protocol"));
        protocolGroup.setItems("Modbus-TCP", "Rest-API");
        protocolGroup.setValue("Modbus-TCP");

        protocolGroup.addValueChangeListener(e -> portField.setValue(e.getValue().equals("Modbus-TCP") ? 502 : 80));
        portField.setValue(502);

        Button saveBtn = new Button(getTranslation("setup.pv.scan.dialog.add_to_grid"), e -> {
            if (!ipField.isEmpty() && portField.getValue() != null) {
                addDeviceToGrid(new DiscoveredPVDevice(ipField.getValue(), portField.getValue(), protocolGroup.getValue(), null, false));
                dialog.close();
            }
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(new VerticalLayout(ipField, portField, protocolGroup));
        dialog.getFooter().add(new TranslatableButton("btn.cancel", e -> dialog.close()), saveBtn);
        dialog.open();
    }

    private void openCustomConfigDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("setup.pv.scan.dialog.creat_config"));

        TranslatableSpan info = new TranslatableSpan("setup.pv.scan.dialog.question.1");
        RadioButtonGroup<String> protocolGroup = new RadioButtonGroup<>();
        protocolGroup.setItems("Modbus-TCP", "Rest-API");

        if (!pvGrid.getSelectedItems().isEmpty()) {
            protocolGroup.setValue(pvGrid.getSelectedItems().iterator().next().getProtocol());
        }

        TranslatableButton startBtn = new TranslatableButton("setup.pv.scan.dialog.start_editor", e -> {
            if (protocolGroup.getValue() != null && onCreateCustomConfigCallback != null) {
                dialog.close();
                onCreateCustomConfigCallback.accept(protocolGroup.getValue());
            }
        });
        startBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(new VerticalLayout(info, protocolGroup));
        dialog.getFooter().add(new TranslatableButton("btn.cancel", e -> dialog.close()), startBtn);
        dialog.open();
    }

    public Set<DiscoveredPVDevice> getSelectedValidPVDevices() {
        return pvGrid.getSelectedItems();
    }

    @Override
    public String getTitleTranslationKey() {
        return "setup.pv.scan.title";
    }

    @Override
    public boolean isValid() {
        if (!scanComplete) return false;
        if (pvGrid.getSelectedItems().isEmpty()) return false;

        for (DiscoveredPVDevice device : pvGrid.getSelectedItems()) {
            if (device.getSelectedConfigName() == null || device.getSelectedConfigName().isBlank()) {
                return false;
            }
        }
        return true;
    }
}