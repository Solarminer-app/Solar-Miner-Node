package de.verdox.pv_miner.frontend.setup;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.discovery.DiscoveryService;
import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MiningOS;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableButton;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MinerSetupStep extends VerticalLayout implements WizardStep {

    private final TranslatableSpan statusText;
    private final Icon radarIcon;
    private final Grid<MinerConfigEntry> minerGrid;
    private final HorizontalLayout bulkConfigLayout;

    private final List<MinerConfigEntry> discoveredMiners = new ArrayList<>();
    private boolean scanComplete = false;
    private final Runnable onScanFinishedCallback;

    private PVSiteEntity existingSite;

    public MinerSetupStep(Runnable onScanFinishedCallback) {
        this.onScanFinishedCallback = onScanFinishedCallback;

        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.START);
        setHeight("650px");
        setSpacing(true);

        radarIcon = VaadinIcon.SIGNAL.create();
        radarIcon.addClassName("radar-scan-animation");
        radarIcon.setSize("48px");

        statusText = new TranslatableSpan("setup.miner.scan.status.ready");
        statusText.getStyle().set("font-size", "1.1em");
        statusText.getStyle().set("font-weight", "500");

        minerGrid = new Grid<>();
        minerGrid.setWidthFull();
        minerGrid.setHeight("350px");
        minerGrid.setVisible(false);
        minerGrid.setSelectionMode(Grid.SelectionMode.MULTI);

        minerGrid.addColumn(entry -> entry.getMinerInfo().model())
                .setHeader(new TranslatableSpan("setup.miner.scan.grid.header.model")).setSortable(true).setAutoWidth(true);
        minerGrid.addColumn(entry -> entry.getMinerInfo().ipAddress())
                .setHeader(new TranslatableSpan("setup.miner.scan.grid.header.ip")).setSortable(true).setAutoWidth(true);
        minerGrid.addColumn(entry -> entry.getMinerInfo().os())
                .setHeader(new TranslatableSpan("setup.miner.scan.grid.header.os")).setSortable(true).setAutoWidth(true);


        minerGrid.addColumn(new ComponentRenderer<>(entry -> {
            if (!entry.isNeedsCustomCredentials()) {
                TranslatableSpan span = new TranslatableSpan(entry.getTestStatus() == TestStatus.TESTING ? "setup.miner.scan.status.testing" : "setup.miner.scan.credentials.standard");
                span.getStyle().set("color", "var(--lumo-contrast-50pct)");
                span.getStyle().set("font-style", "italic");
                return span;
            }
            TextField usernameField = new TextField();
            usernameField.setPlaceholder("root");
            usernameField.setValue(entry.getUsername());
            usernameField.addValueChangeListener(e -> entry.setUsername(e.getValue()));
            usernameField.setWidth("120px");
            return usernameField;
        })).setHeader("Username");


        minerGrid.addColumn(new ComponentRenderer<>(entry -> {
            if (!entry.isNeedsCustomCredentials()) {
                TranslatableSpan span = new TranslatableSpan(entry.getTestStatus() == TestStatus.TESTING ? "setup.miner.scan.status.testing" : "setup.miner.scan.credentials.standard");
                span.getStyle().set("color", "var(--lumo-contrast-50pct)");
                span.getStyle().set("font-style", "italic");
                return span;
            }
            PasswordField passwordField = new PasswordField();
            passwordField.setPlaceholder("password");
            passwordField.setValue(entry.getPassword());
            passwordField.addValueChangeListener(e -> entry.setPassword(e.getValue()));
            passwordField.setWidth("120px");
            return passwordField;
        })).setHeader("Password");


        minerGrid.addColumn(new ComponentRenderer<>(entry -> {
            HorizontalLayout actionLayout = new HorizontalLayout();
            actionLayout.setAlignItems(Alignment.CENTER);

            Icon statusIcon = createStatusIcon(entry.getTestStatus());


            if (entry.isNeedsCustomCredentials()) {
                Button testBtn = new Button(VaadinIcon.PLUG.create());
                testBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
                testBtn.addClickListener(e -> {
                    testCustomConnection(entry, statusIcon);
                });
                actionLayout.add(testBtn);
            }

            actionLayout.add(statusIcon);
            return actionLayout;
        })).setHeader("Status").setAutoWidth(true);

        bulkConfigLayout = createBulkConfigLayout();

        add(radarIcon, statusText, minerGrid, bulkConfigLayout);
    }

    public void setExistingSite(PVSiteEntity existingSite) {
        this.existingSite = existingSite;
    }

    private HorizontalLayout createBulkConfigLayout() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setAlignItems(Alignment.BASELINE);
        layout.setVisible(false);
        layout.getStyle().set("margin-top", "15px");
        layout.getStyle().set("padding", "10px");
        layout.getStyle().set("background", "var(--lumo-contrast-5pct)");
        layout.getStyle().set("border-radius", "8px");

        TranslatableSpan bulkTitle = new TranslatableSpan("setup.miner.scan.bulk_actions");
        bulkTitle.getStyle().set("font-weight", "bold");

        TextField bulkUser = new TextField();
        bulkUser.setPlaceholder("Username");

        PasswordField bulkPass = new PasswordField();
        bulkPass.setPlaceholder("Password");

        TranslatableButton applyBtn = new TranslatableButton("btn.apply", VaadinIcon.EDIT.create(), e -> {
            Set<MinerConfigEntry> selected = minerGrid.getSelectedItems();
            if (selected.isEmpty()) {
                Notification.show("Please select at least one miner from the grid.");
                return;
            }
            selected.forEach(entry -> {
                if (entry.isNeedsCustomCredentials()) {
                    entry.setUsername(bulkUser.getValue());
                    entry.setPassword(bulkPass.getValue());
                }
            });
            minerGrid.getDataProvider().refreshAll();
            Notification.show(getTranslation("notification.miner.credentials_applied", selected.size()));
        });

        TranslatableButton testBulkBtn = new TranslatableButton("btn.test_selected", VaadinIcon.CHECK.create(), e -> {
            Set<MinerConfigEntry> selected = minerGrid.getSelectedItems();
            if (selected.isEmpty()) {
                Notification.show("Please select at least one miner to test.");
                return;
            }
            selected.forEach(entry -> {
                if (entry.isNeedsCustomCredentials()) {
                    testCustomConnection(entry, null);
                }
            });
            minerGrid.getDataProvider().refreshAll();
        });
        testBulkBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        layout.add(bulkTitle, bulkUser, bulkPass, applyBtn, testBulkBtn);
        return layout;
    }

    @Override
    public void onEnter() {
        startScan();
    }

    public void startScan() {
        discoveredMiners.clear();
        minerGrid.setItems(discoveredMiners);
        scanComplete = false;
        bulkConfigLayout.setVisible(false);

        UI ui = UI.getCurrent();
        statusText.setText("setup.miner.scan.status.scanning");

        DiscoveryService discoveryService = SpringContextHelper.getBean(DiscoveryService.class);

        String localSubnet = "192.168.178.";

        discoveryService.discoverMiners(
                localSubnet,
                minerInfo -> ui.access(() -> {
                    boolean alreadyExists = false;
                    if (existingSite != null && existingSite.getMiners() != null) {
                        alreadyExists = existingSite.getMiners().stream()
                                .anyMatch(m -> m.getIP() != null && m.getIP().equals(minerInfo.ipAddress()));
                    }

                    if (!alreadyExists) {
                        MinerConfigEntry newEntry = new MinerConfigEntry(minerInfo);
                        newEntry.setTestStatus(TestStatus.TESTING);

                        discoveredMiners.add(newEntry);
                        updateGridData();
                        minerGrid.select(newEntry);

                        CompletableFuture.runAsync(() -> testStandardCredentials(newEntry, ui));
                    }
                }),
                () -> ui.access(() -> {
                    scanComplete = true;

                    radarIcon.removeClassName("radar-scan-animation");
                    radarIcon.setColor("var(--lumo-success-color)");
                    radarIcon.getElement().setAttribute("icon", VaadinIcon.CHECK_CIRCLE.create().getElement().getAttribute("icon"));

                    if (discoveredMiners.isEmpty()) {
                        statusText.setText("setup.miner.scan.status.searching.done.none");
                    } else {
                        statusText.setText("setup.miner.scan.status.searching.done.any");
                        statusText.setTranslationParameters(discoveredMiners.size());
                        bulkConfigLayout.setVisible(true);
                    }

                    if (onScanFinishedCallback != null) {
                        onScanFinishedCallback.run();
                    }
                })
        );
    }

    private void testStandardCredentials(MinerConfigEntry entry, UI ui) {
        int port = entry.getMinerInfo().os().equals(MiningOS.BRAIINS) ? 50051 : 80;
        var details = new MinerApiClient.MinerDetails(UUID.randomUUID(), entry.getMinerInfo().ipAddress(), port, null, null);

        MinerApiClient client = SpringContextHelper.getBean(MinerApiClient.class);
        boolean success = false;
        try {
            success = client.checkStandardCredentialsWork(entry.getMinerInfo().os(), details);
        } catch (Exception e) {

        }

        boolean finalSuccess = success;
        ui.access(() -> {
            if (finalSuccess) {
                entry.setTestStatus(TestStatus.SUCCESS);
                entry.setNeedsCustomCredentials(false);
            } else {
                entry.setTestStatus(TestStatus.UNTESTED);
                entry.setNeedsCustomCredentials(true);
            }
            minerGrid.getDataProvider().refreshItem(entry);
        });
    }

    private void testCustomConnection(MinerConfigEntry entry, Icon statusIconToUpdate) {
        int port = entry.getMinerInfo().os().equals(MiningOS.BRAIINS) ? 50051 : 80;
        var details = new MinerApiClient.MinerDetails(UUID.randomUUID(), entry.getMinerInfo().ipAddress(), port, entry.getUsername(), entry.getPassword());

        boolean success = false;
        try {
            success = SpringContextHelper.getBean(MinerApiClient.class).checkIfCustomCredentialsWork(entry.getMinerInfo().os(), details);
        } catch (Exception e) {

        }

        entry.setTestStatus(success ? TestStatus.SUCCESS : TestStatus.FAILED);

        if (statusIconToUpdate != null) {
            Icon newIcon = createStatusIcon(entry.getTestStatus());
            statusIconToUpdate.getElement().setAttribute("icon", newIcon.getElement().getAttribute("icon"));
            statusIconToUpdate.setColor(newIcon.getColor());
        }
    }

    private Icon createStatusIcon(TestStatus status) {
        Icon icon;
        switch (status) {
            case SUCCESS:
                icon = VaadinIcon.CHECK_CIRCLE.create();
                icon.setColor("var(--lumo-success-color)");
                break;
            case FAILED:
                icon = VaadinIcon.CLOSE_CIRCLE.create();
                icon.setColor("var(--lumo-error-color)");
                break;
            case TESTING:
                icon = VaadinIcon.HOURGLASS.create();
                icon.setColor("var(--lumo-primary-color)");
                break;
            default:
                icon = VaadinIcon.QUESTION_CIRCLE_O.create();
                icon.setColor("var(--lumo-contrast-50pct)");
                break;
        }
        return icon;
    }

    private void updateGridData() {
        minerGrid.setItems(discoveredMiners);

        if (discoveredMiners.size() == 1) {
            minerGrid.setVisible(true);
            minerGrid.addClassName("fade-in");
        }

        statusText.setText("setup.miner.scan.status.searching");
        statusText.setTranslationParameters(discoveredMiners.size());
    }

    public Set<MinerConfigEntry> getSelectedMiners() {
        return minerGrid.getSelectedItems();
    }

    @Override
    public String getTitleTranslationKey() {
        return "setup.miner.scan.title";
    }

    @Override
    public boolean isValid() {
/*        if (!scanComplete) {
            return false;
        }

        Set<MinerConfigEntry> selected = minerGrid.getSelectedItems();

        for (MinerConfigEntry entry : selected) {
            if (entry.getTestStatus() != TestStatus.SUCCESS) {

                return false;
            }
        }*/
        return true;
    }

    public enum TestStatus {
        UNTESTED, TESTING, SUCCESS, FAILED
    }

    public static class MinerConfigEntry {
        private final DiscoveryService.MinerInfo minerInfo;
        private String username = "root";
        private String password = "root";
        private TestStatus testStatus = TestStatus.UNTESTED;
        private boolean needsCustomCredentials = false;

        public MinerConfigEntry(DiscoveryService.MinerInfo minerInfo) {
            this.minerInfo = minerInfo;
        }

        public DiscoveryService.MinerInfo getMinerInfo() { return minerInfo; }

        public String getUsername() { return username; }
        public void setUsername(String username) {
            this.username = username;
            this.testStatus = TestStatus.UNTESTED;
        }

        public String getPassword() { return password; }
        public void setPassword(String password) {
            this.password = password;
            this.testStatus = TestStatus.UNTESTED;
        }

        public TestStatus getTestStatus() { return testStatus; }
        public void setTestStatus(TestStatus testStatus) { this.testStatus = testStatus; }

        public boolean isNeedsCustomCredentials() { return needsCustomCredentials; }
        public void setNeedsCustomCredentials(boolean needsCustomCredentials) { this.needsCustomCredentials = needsCustomCredentials; }
    }
}