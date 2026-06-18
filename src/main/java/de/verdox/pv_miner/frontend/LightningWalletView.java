package de.verdox.pv_miner.frontend;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.Lumo;
import de.verdox.phoenixdjava.PhoenixDTOs;
import de.verdox.pv_miner.frontend.user.*;
import de.verdox.pv_miner.globalconstants.GlobalConstantsService;
import de.verdox.pv_miner.lightningremote.SolarMiningWebSocketClient;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import de.verdox.pv_miner.lightning.LightningTransaction;
import de.verdox.pv_miner.lightning.LightningWalletService;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableSpan;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Route(value = "lightning-wallet")
@CssImport("./themes/solarminer/lightning-wallet.css")
public class LightningWalletView extends VerticalLayout implements HasDynamicTitle, LocaleChangeObserver, CurrencyChangeObserver, TimeZoneChangeObserver {

    private final LightningWalletService walletService;
    private final UserSessionContext sessionContext;
    private final GlobalConstantsService globalConstantsService;

    private final WalletHeader walletHeader = new WalletHeader();
    private final MainAccountCard mainAccountCard;
    private final AdvancedBolt12Panel advancedBolt12Panel;
    private final SolarMiningWebSocketClient webSocketClient;
    private final TransactionHistoryGrid transactionHistoryGrid;
    private final ChannelStatisticsPanel channelStatisticsPanel = new ChannelStatisticsPanel();
    private final ConnectionStatusPanel connectionStatusPanel;

    public LightningWalletView(@Autowired LightningWalletService walletService,
                               @Autowired UserSessionContext sessionContext,
                               @Autowired SolarMiningWebSocketClient webSocketClient,
                               @Autowired GlobalConstantsService globalConstantsService) {
        getElement().getThemeList().add(Lumo.DARK);
        this.walletService = walletService;
        this.sessionContext = sessionContext;
        this.webSocketClient = webSocketClient;
        this.globalConstantsService = globalConstantsService;

        this.mainAccountCard = new MainAccountCard(walletService);
        this.advancedBolt12Panel = new AdvancedBolt12Panel(walletService);
        this.connectionStatusPanel = new ConnectionStatusPanel();
        this.transactionHistoryGrid = new TransactionHistoryGrid();

        setWidthFull();
        getStyle().set("min-height", "100vh");
        setPadding(false);
        setSpacing(false);
        addClassName("lightning-view-container");

        VerticalLayout contentWrapper = new VerticalLayout();
        contentWrapper.addClassName("lightning-content-wrapper");
        add(contentWrapper);

        contentWrapper.add(walletHeader);

        Div dashboardGrid = new Div();
        dashboardGrid.addClassName("lightning-dashboard-grid");

        Div mainColumn = new Div();
        mainColumn.addClassName("dashboard-main-column");

        mainColumn.add(mainAccountCard);
        mainColumn.add(new SectionHeader("lightning.section.history", VaadinIcon.TIME_BACKWARD));
        mainColumn.add(transactionHistoryGrid);

        Div sideColumn = new Div();
        sideColumn.addClassName("dashboard-side-column");

        sideColumn.add(connectionStatusPanel);

        sideColumn.add(new SectionHeader("lightning.section.nerds", VaadinIcon.DASHBOARD));
        sideColumn.add(channelStatisticsPanel);

        sideColumn.add(new SectionHeader("lightning.section.advanced", VaadinIcon.WRENCH));
        sideColumn.add(advancedBolt12Panel);

        dashboardGrid.add(mainColumn, sideColumn);
        contentWrapper.add(dashboardGrid);

        updateData();
    }

    @Override
    public String getPageTitle() {
        return getTranslation("lightning.page.title") + " | PV-Mining";
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        updateData();
    }

    @Override
    public void onCurrencyChange(CurrencyChangeEvent event) {
        updateData();
    }

    @Override
    public void onTimeZoneChange(TimeZoneChangeEvent event) {
        transactionHistoryGrid.refreshTimeDisplay();
    }

    private void updateData() {
        long balance = walletService.getBalanceSat();
        mainAccountCard.setBalance(balance);
        transactionHistoryGrid.setTransactions(walletService.getTransactions());

        PhoenixDTOs.NodeInfo nodeInfo = walletService.getNodeInfo();
        if (nodeInfo != null && nodeInfo.channels() != null) {
            long localLiquidity = 0;
            long remoteLiquidity = 0;
            int activeChannels = 0;

            for (var channel : nodeInfo.channels()) {
                localLiquidity += channel.balanceSat();
                remoteLiquidity += channel.inboundLiquiditySat();
                if ("STABLE".equalsIgnoreCase(channel.state())) {
                    activeChannels++;
                }
            }
            channelStatisticsPanel.updateStatistics(activeChannels, localLiquidity, remoteLiquidity);
        }
    }

    private String convertSatsToUserCurrencyString(long sats) {
        double btc = sats / 100000000.0;
        CustomCurrency userCurrency = sessionContext.getCurrency() != null ? sessionContext.getCurrency() : CustomCurrency.getInstance("EUR");
        CustomCurrency btcCurrency = CustomCurrency.getInstance("BTC");

        double rate = globalConstantsService.getExchangeRate(btcCurrency, userCurrency);

        if (rate <= 0.0) {
            double fallbackBtcEurRate = 63000.0;
            rate = userCurrency.getCurrencyCode().equals("USD") ? fallbackBtcEurRate * 1.08 : fallbackBtcEurRate;
        }

        double convertedValue = btc * rate;

        return String.format("%,d sats (%s%,.2f)", sats, userCurrency.getSymbol(sessionContext.getLocale()), convertedValue);
    }

    private static class SectionHeader extends HorizontalLayout implements LocaleChangeObserver {
        private final H3 titleNode = new H3();
        private final String translationKey;

        public SectionHeader(String translationKey, VaadinIcon icon) {
            this.translationKey = translationKey;
            addClassName("lightning-section-header");
            getStyle().set("margin-top", "0");

            var iconInstance = icon.create();
            iconInstance.addClassName("lightning-section-icon");

            titleNode.addClassName("nowrap-title");
            add(iconInstance, titleNode);
        }

        @Override
        public void localeChange(LocaleChangeEvent event) {
            titleNode.setText(getTranslation(translationKey));
        }
    }

    private static class DashboardCard extends Div implements LocaleChangeObserver {
        private final Span titleNode = new Span();
        private final String titleKey;

        public DashboardCard(String titleKey, VaadinIcon icon) {
            this.titleKey = titleKey;
            addClassName("lightning-card");

            titleNode.addClassName("nowrap-title");
            HorizontalLayout header = new HorizontalLayout(icon.create(), titleNode);
            header.addClassName("lightning-card-header");
            add(header);
        }

        @Override
        public void localeChange(LocaleChangeEvent event) {
            titleNode.setText(getTranslation(titleKey));
        }
    }

    private static class WalletHeader extends HorizontalLayout implements LocaleChangeObserver {
        private final Button backButton = new Button(VaadinIcon.ARROW_LEFT.create());
        private final H1 title = new H1("⚡ Mining Payout Center");
        private final Span subTitle = new Span();

        public WalletHeader() {
            addClassName("wallet-header-layout");
            setPadding(false);

            backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            backButton.addClickListener(e -> UI.getCurrent().navigate(PVSiteSelectionView.class));
            backButton.getStyle().set("margin-right", "var(--lumo-space-m)");

            title.addClassName("lightning-header-title");
            subTitle.addClassName("lightning-header-subtitle");

            VerticalLayout titleGroup = new VerticalLayout(title, subTitle);
            titleGroup.setPadding(false);
            titleGroup.setSpacing(false);
            titleGroup.setWidth("auto");

            add(backButton, titleGroup);
        }

        @Override
        public void localeChange(LocaleChangeEvent event) {
            subTitle.setText(getTranslation("lightning.header.subtitle"));
        }
    }

    private class MainAccountCard extends DashboardCard {
        private final Span balanceSpan = new Span();
        private final Span addressLabel = new Span();
        private final Span addressSpan = new Span();

        private final Button withdrawLightningBtn = new Button();
        private final Button withdrawOnChainBtn = new Button();
        private final Button autoRuleBtn = new Button();

        public MainAccountCard(LightningWalletService walletService) {
            super("lightning.card.balance", VaadinIcon.WALLET);

            balanceSpan.addClassName("lightning-balance-value");

            HorizontalLayout addressRow = new HorizontalLayout();
            addressRow.addClassName("lightning-address-compact-row");

            var mailIcon = VaadinIcon.AT.create();
            mailIcon.getStyle().set("color", "var(--lumo-primary-color)").set("width", "16px");

            addressLabel.getStyle().set("font-size", "var(--lumo-font-size-xs)").set("color", "var(--lumo-secondary-text-color)");

            addressSpan.setText(walletService.claimFreeLightningAddress());
            addressSpan.getStyle().set("font-size", "0.95rem").set("font-weight", "600");

            Button copyAddressBtn = new Button(VaadinIcon.COPY.create());
            copyAddressBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
            copyAddressBtn.addClickListener(e -> {
                copyAddressBtn.getElement().executeJs("navigator.clipboard.writeText($0)", addressSpan.getText());
                Notification.show(getTranslation("lightning.notification.copied")).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            });

            VerticalLayout addressTextGroup = new VerticalLayout(addressLabel, addressSpan);
            addressTextGroup.setPadding(false);
            addressTextGroup.setSpacing(false);

            addressRow.add(mailIcon, addressTextGroup, copyAddressBtn);
            addressRow.setAlignItems(Alignment.CENTER);

            HorizontalLayout buttonRow = new HorizontalLayout();
            buttonRow.setWidthFull();
            buttonRow.addClassName("lightning-action-buttons-row");

            withdrawLightningBtn.setIcon(VaadinIcon.BOLT.create());
            withdrawLightningBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            withdrawLightningBtn.addClickListener(e -> openWithdrawDialog("Lightning"));
            withdrawLightningBtn.setEnabled(false);

            withdrawOnChainBtn.setIcon(VaadinIcon.LINK.create());
            withdrawOnChainBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
            withdrawOnChainBtn.addClickListener(e -> openWithdrawDialog("On-Chain"));
            withdrawOnChainBtn.setEnabled(false);

            autoRuleBtn.setIcon(VaadinIcon.COGS.create());
            autoRuleBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            autoRuleBtn.addClickListener(e -> Notification.show("Auto-Regeln Setup folgt..."));
            autoRuleBtn.setEnabled(false);

            buttonRow.add(withdrawLightningBtn, withdrawOnChainBtn, autoRuleBtn);

            add(balanceSpan, addressRow, buttonRow);
        }

        public void setBalance(long sats) {
            balanceSpan.setText(convertSatsToUserCurrencyString(sats));
        }

        private void openWithdrawDialog(String type) {
            Dialog dialog = new Dialog();
            dialog.setHeaderTitle(type + " " + getTranslation("lightning.dialog.title"));
            dialog.setWidth("500px");

            VerticalLayout layout = new VerticalLayout();
            layout.setPadding(false);

            TextArea addressInput = new TextArea(getTranslation("lightning.dialog.address_label"));
            addressInput.setPlaceholder(getTranslation("lightning.dialog.address_placeholder"));
            addressInput.setWidthFull();
            addressInput.setMinHeight("120px");

            Paragraph infoText = new Paragraph(getTranslation("lightning.dialog.info"));
            infoText.getStyle().set("font-size", "var(--lumo-font-size-xs)").set("color", "var(--lumo-secondary-text-color)");

            layout.add(addressInput, infoText);
            dialog.add(layout);

            Button confirmBtn = new Button(getTranslation("lightning.dialog.button.confirm"), e -> {
                String target = addressInput.getValue();
                if (target == null || target.isBlank()) {
                    Notification.show(getTranslation("lightning.dialog.error.empty")).addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }
                boolean success = walletService.sendPayment(target);
                if (success) {
                    Notification.show(getTranslation("lightning.dialog.success")).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    dialog.close();
                    updateData();
                } else {
                    Notification.show(getTranslation("lightning.dialog.error.failed")).addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            });
            confirmBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

            Button cancelBtn = new Button(getTranslation("lightning.dialog.button.cancel"), e -> dialog.close());
            dialog.getFooter().add(cancelBtn, confirmBtn);
            dialog.open();
        }

        @Override
        public void localeChange(LocaleChangeEvent event) {
            super.localeChange(event);
            addressLabel.setText(getTranslation("lightning.label.address"));
            withdrawLightningBtn.setText(getTranslation("lightning.button.withdraw_lightning"));
            withdrawOnChainBtn.setText(getTranslation("lightning.button.withdraw_onchain"));
            autoRuleBtn.setText(getTranslation("lightning.button.auto_rules"));
        }
    }

    private static class AdvancedBolt12Panel extends DashboardCard {
        private final Details bolt12Details = new Details();
        private final TextArea bolt12TextArea = new TextArea();
        private final Button copyBolt12Btn = new Button(VaadinIcon.COPY.create());

        public AdvancedBolt12Panel(LightningWalletService walletService) {
            super("lightning.details.bolt12", VaadinIcon.QRCODE);

            bolt12Details.setWidthFull();
            bolt12Details.addClassName("lightning-bolt12-details");

            HorizontalLayout bolt12Row = new HorizontalLayout();
            bolt12Row.setWidthFull();
            bolt12Row.getStyle().set("align-items", "center");

            bolt12TextArea.setWidthFull();
            bolt12TextArea.setReadOnly(true);
            bolt12TextArea.setValue(walletService.getBolt12());

            copyBolt12Btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            copyBolt12Btn.addClickListener(e -> {
                copyBolt12Btn.getElement().executeJs("navigator.clipboard.writeText($0)", bolt12TextArea.getValue());
                Notification.show(getTranslation("lightning.notification.copied_bolt12")).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            });

            bolt12Row.add(bolt12TextArea, copyBolt12Btn);
            bolt12Row.setFlexGrow(1, bolt12TextArea);

            bolt12Details.add(bolt12Row);
            bolt12Details.setOpened(false);

            add(bolt12Details);
        }

        @Override
        public void localeChange(LocaleChangeEvent event) {
            super.localeChange(event);
            bolt12Details.setSummaryText("BOLT12 Offer (Advanced)");
        }
    }

    private class TransactionHistoryGrid extends Grid<LightningTransaction> implements LocaleChangeObserver {
        public TransactionHistoryGrid() {
            setWidthFull();
            addClassNames("lightning-grid", "lightning-history-grid");
            addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
            addClassName("lightning-grid");
            setupColumns();
        }

        private void setupColumns() {
            removeAllColumns();

            addComponentColumn(tx -> {
                var icon = tx.type() == LightningTransaction.Type.INCOMING ? VaadinIcon.ARROW_DOWN.create() : VaadinIcon.ARROW_UP.create();
                icon.getStyle().set("color", tx.type() == LightningTransaction.Type.INCOMING ? "var(--lumo-success-color)" : "var(--lumo-error-color)");
                return icon;
            }).setHeader(getTranslation("lightning.grid.type")).setAutoWidth(true);

            addColumn(tx -> {
                ZoneId zoneId = sessionContext.getZoneId() != null ? sessionContext.getZoneId() : ZoneId.systemDefault();
                ZonedDateTime zonedDateTime = tx.timestamp().atZone(zoneId);
                return zonedDateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
            }).setHeader(getTranslation("lightning.grid.date")).setSortable(true).setAutoWidth(true);

            addColumn(LightningTransaction::memo).setHeader(getTranslation("lightning.grid.memo")).setFlexGrow(2);

            addColumn(tx -> (tx.type() == LightningTransaction.Type.INCOMING ? "+" : "-") + convertSatsToUserCurrencyString(tx.amountSat()))
                    .setHeader(getTranslation("lightning.grid.amount")).setAutoWidth(true);

            addComponentColumn(tx -> {
                Span badge = new Span(tx.status().name());
                badge.getElement().getThemeList().add("badge");
                switch (tx.status()) {
                    case SETTLED -> badge.getElement().getThemeList().add("success");
                    case PENDING -> badge.getElement().getThemeList().add("warning");
                    case EXPIRED -> badge.getElement().getThemeList().add("error");
                }
                return badge;
            }).setHeader(getTranslation("lightning.grid.status")).setAutoWidth(true);
        }

        public void setTransactions(java.util.List<LightningTransaction> transactions) {
            setItems(transactions);
        }

        public void refreshTimeDisplay() {
            setupColumns();
        }

        @Override
        public void localeChange(LocaleChangeEvent event) {
            setupColumns();
        }
    }

    private class ChannelStatisticsPanel extends Div implements LocaleChangeObserver {
        private final DashboardCard card1 = new DashboardCard("lightning.stats.channels", VaadinIcon.CONNECT);
        private final DashboardCard card2 = new DashboardCard("lightning.stats.local", VaadinIcon.ARROW_CIRCLE_UP);
        private final DashboardCard card3 = new DashboardCard("lightning.stats.remote", VaadinIcon.ARROW_CIRCLE_DOWN);

        private final Span activeChannelsSpan = new Span();
        private final Span localLiquiditySpan = new Span();
        private final Span remoteLiquiditySpan = new Span();

        public ChannelStatisticsPanel() {
            addClassName("channel-stats-grid");

            activeChannelsSpan.addClassName("stat-large-value");
            card1.add(activeChannelsSpan);

            localLiquiditySpan.addClassName("stat-large-value");
            localLiquiditySpan.getStyle().set("color", "var(--lumo-success-text-color)");
            card2.add(localLiquiditySpan);

            remoteLiquiditySpan.addClassName("stat-large-value");
            remoteLiquiditySpan.getStyle().set("color", "var(--lumo-primary-text-color)");
            card3.add(remoteLiquiditySpan);

            add(card1, card2, card3);
        }

        public void updateStatistics(int activeChannels, long localSat, long remoteSat) {
            activeChannelsSpan.setText(activeChannels + " " + getTranslation("lightning.stats.active_channels"));
            localLiquiditySpan.setText(convertSatsToUserCurrencyString(localSat));
            remoteLiquiditySpan.setText(convertSatsToUserCurrencyString(remoteSat));
        }

        @Override
        public void localeChange(LocaleChangeEvent event) {
            activeChannelsSpan.setText("3 " + getTranslation("lightning.stats.active_channels"));
        }
    }

    private class ConnectionStatusPanel extends DashboardCard {
        private final Span statusBadge = new Span();
        private final Button toggleBtn = new Button();
        private Runnable statusChangeListener;

        public ConnectionStatusPanel() {
            super("lightning.card.connection_status", VaadinIcon.PLUG);

            Paragraph infoText = new Paragraph(new TranslatableSpan("lightning.bolt11.info"));
            infoText.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--lumo-secondary-text-color)").set("margin-top", "0");

            statusBadge.getElement().getThemeList().add("badge");

            HorizontalLayout controlsRow = new HorizontalLayout(statusBadge, toggleBtn);
            controlsRow.getStyle().set("align-items", "center");

            add(infoText, controlsRow);

            toggleBtn.addClickListener(e -> {
                boolean isCurrentlyEnabled = webSocketClient.isEnabled();
                webSocketClient.setEnabled(!isCurrentlyEnabled);
                updateUIState();
            });

            updateUIState();
        }

        public void updateUIState() {
            boolean enabled = webSocketClient.isEnabled();
            boolean connected = webSocketClient.isConnected();

            statusBadge.getElement().getThemeList().removeAll(java.util.List.of("success", "error", "contrast"));

            if (!enabled) {
                statusBadge.setText("Deaktiviert");
                statusBadge.getElement().getThemeList().add("contrast");
                toggleBtn.setText("Verbindung aktivieren");
                toggleBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            } else if (connected) {
                statusBadge.setText("Verbunden");
                statusBadge.getElement().getThemeList().add("success");
                toggleBtn.setText("Verbindung trennen");
                toggleBtn.removeThemeVariants(ButtonVariant.LUMO_PRIMARY);
            } else {
                statusBadge.setText("Verbindet...");
                statusBadge.getElement().getThemeList().add("warning");
                toggleBtn.setText("Verbindung trennen");
                toggleBtn.removeThemeVariants(ButtonVariant.LUMO_PRIMARY);
            }
        }

        @Override
        protected void onAttach(AttachEvent attachEvent) {
            super.onAttach(attachEvent);
            UI ui = attachEvent.getUI();
            statusChangeListener = () -> ui.access(this::updateUIState);
            webSocketClient.addStatusListener(statusChangeListener);
        }

        @Override
        protected void onDetach(DetachEvent detachEvent) {
            super.onDetach(detachEvent);
            if (statusChangeListener != null) {
                webSocketClient.removeStatusListener(statusChangeListener);
            }
        }
    }
}