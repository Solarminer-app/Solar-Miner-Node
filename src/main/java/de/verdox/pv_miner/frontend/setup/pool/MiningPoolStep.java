package de.verdox.pv_miner.frontend.setup.pool;

import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.lightning.LightningWalletService;
import de.verdox.pv_miner.miningpool.MiningPoolEntity;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableH3;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableSpan;
import de.verdox.pv_miner.frontend.FrontendColor;
import de.verdox.pv_miner.frontend.components.CheckConnection;
import de.verdox.pv_miner.frontend.components.MarkdownView;
import de.verdox.pv_miner.frontend.setup.WizardStep;

import java.util.ArrayList;
import java.util.List;

public class MiningPoolStep extends VerticalLayout implements WizardStep, LocaleChangeObserver {

    private final TextField bolt12AddressField = new TextField();

    private final List<MiningPoolSetupProvider> supportedPools = new ArrayList<>();

    private final HorizontalLayout cardsContainer;
    private final VerticalLayout configContainer;
    private VerticalLayout selectedCard;

    private boolean isPlugAndPlaySelected = false;
    private boolean manualConnectionVerified = false;
    private final Runnable onValidationChanged;

    private MiningPoolSetupProvider activeManualProvider;

    private PVSiteEntity existingSite;

    public MiningPoolStep(Runnable onValidationChanged) {
        this.onValidationChanged = onValidationChanged;

        supportedPools.add(new BraiinsSetupProvider());

        setSizeFull();
        setSpacing(true);
        setPadding(true);
        setAlignItems(Alignment.CENTER);

        TranslatableH3 title = new TranslatableH3("setup.pool.title");
        TranslatableSpan subtitle = new TranslatableSpan("setup.pool.subtitle");
        subtitle.getStyle().set("color", FrontendColor.TEXT_VALUE_GRAY);

        VerticalLayout header = new VerticalLayout(title, subtitle);
        header.setAlignItems(Alignment.CENTER);
        header.setPadding(false);
        header.setSpacing(false);
        header.getStyle().set("margin-bottom", "20px");

        cardsContainer = new HorizontalLayout();
        cardsContainer.setWidthFull();
        cardsContainer.setMaxWidth("750px");
        cardsContainer.setJustifyContentMode(JustifyContentMode.CENTER);
        cardsContainer.setSpacing(true);
        cardsContainer.getStyle().set("flex-wrap", "wrap");

        configContainer = new VerticalLayout();
        configContainer.setWidthFull();
        configContainer.setMaxWidth("750px");
        configContainer.setPadding(false);
        configContainer.getStyle().set("margin-top", "20px");

        buildCards();

        add(header, cardsContainer, configContainer);
    }

    private void buildCards() {
        cardsContainer.removeAll();

        boolean hasOceans = false;
        if (existingSite != null && existingSite.getConnectedMiningPools() != null) {
            hasOceans = existingSite.getConnectedMiningPools().stream()
                    .anyMatch(pool -> pool.getUrlIdentifier() != null && pool.getUrlIdentifier().toLowerCase().contains("ocean"));
        }

        boolean isOceansImplemented = false;

        VerticalLayout oceansCard = createSelectionCard(
                "setup.pool.oceans.title",
                "setup.pool.oceans.badge",
                VaadinIcon.DROP,
                true,
                hasOceans,
                isOceansImplemented
        );

        if (!isOceansImplemented || hasOceans) {
            oceansCard.setEnabled(false);
        } else {
            oceansCard.addClickListener(e -> selectOceans(oceansCard));
        }
        cardsContainer.add(oceansCard);

        for (MiningPoolSetupProvider provider : supportedPools) {
            boolean isImplemented = provider.isImplemented();

            boolean alreadyConnected = false;
            if (existingSite != null && existingSite.getConnectedMiningPools() != null) {
                String searchKeyword = provider.getDisplayName().split(" ")[0].toLowerCase();
                alreadyConnected = existingSite.getConnectedMiningPools().stream()
                        .anyMatch(pool -> pool.getUrlIdentifier() != null && pool.getUrlIdentifier().toLowerCase().contains(searchKeyword));
            }

            VerticalLayout providerCard = createSelectionCard(
                    provider.getDisplayName(),
                    "setup.pool.manual.badge",
                    VaadinIcon.SERVER,
                    false,
                    alreadyConnected,
                    isImplemented
            );

            if (isImplemented && !alreadyConnected) {
                providerCard.addClickListener(e -> selectManualPool(providerCard, provider));
            } else {
                providerCard.setEnabled(false);
            }
            cardsContainer.add(providerCard);
        }

        if (isOceansImplemented && !hasOceans) {
            selectOceans(oceansCard);
        } else {
            for (int i = 0; i < supportedPools.size(); i++) {
                MiningPoolSetupProvider provider = supportedPools.get(i);

                boolean providerConnected = false;
                if (existingSite != null && existingSite.getConnectedMiningPools() != null) {
                    String searchKeyword = provider.getDisplayName().split(" ")[0].toLowerCase();
                    providerConnected = existingSite.getConnectedMiningPools().stream()
                            .anyMatch(pool -> pool.getUrlIdentifier() != null && pool.getUrlIdentifier().toLowerCase().contains(searchKeyword));
                }

                if (provider.isImplemented() && !providerConnected) {
                    VerticalLayout card = (VerticalLayout) cardsContainer.getComponentAt(i + 1);
                    selectManualPool(card, provider);
                    break;
                }
            }
        }
    }

    private void selectOceans(VerticalLayout card) {
        setCardSelected(card);
        this.activeManualProvider = null;
        isPlugAndPlaySelected = true;
        manualConnectionVerified = false;

        configContainer.removeAll();
        configContainer.add(createPlugAndPlayLayout());

        configContainer.removeClassName("fade-in");
        configContainer.getElement().executeJs("setTimeout(() => { this.classList.add('fade-in'); }, 10);");

        onValidationChanged.run();
    }

    private void selectManualPool(VerticalLayout card, MiningPoolSetupProvider strategy) {
        setCardSelected(card);
        this.activeManualProvider = strategy;
        isPlugAndPlaySelected = false;
        manualConnectionVerified = false;

        configContainer.removeAll();
        configContainer.add(createManualLayout(strategy));

        configContainer.removeClassName("fade-in");
        configContainer.getElement().executeJs("setTimeout(() => { this.classList.add('fade-in'); }, 10);");

        onValidationChanged.run();
    }

    public MiningPoolEntity<?> getSelectedPoolEntity() {
        if (isPlugAndPlaySelected) {
            // TODO: Oceans
            /*
            OceansPoolEntity oceans = new OceansPoolEntity();
            oceans.setBolt12Offer(bolt12AddressField.getValue());
            return oceans;
            */
            throw new UnsupportedOperationException("OceansPoolEntity ist noch nicht implementiert!");
        } else if (activeManualProvider != null) {
            return activeManualProvider.getConfiguredEntity();
        }
        return null;
    }

    private void setCardSelected(VerticalLayout card) {
        cardsContainer.getChildren().forEach(c -> {
            c.getStyle()
                    .set("border", "2px solid #222226")
                    .set("background-color", FrontendColor.CARD_BACKGROUND_COLOR);
        });

        this.selectedCard = card;
        card.getStyle()
                .set("border", "2px solid var(--lumo-primary-color)")
                .set("background-color", "var(--lumo-contrast-5pct)");
    }

    private VerticalLayout createSelectionCard(String titleKeyOrText, String badgeKey, VaadinIcon icon, boolean recommended, boolean alreadyConnected, boolean isImplemented) {
        VerticalLayout card = new VerticalLayout();
        card.setWidth("230px");
        card.setHeight("180px");
        card.setAlignItems(Alignment.CENTER);
        card.setJustifyContentMode(JustifyContentMode.CENTER);
        card.getStyle()
                .set("border", "2px solid #222226")
                .set("background-color", FrontendColor.CARD_BACKGROUND_COLOR)
                .set("border-radius", "12px")
                .set("transition", "all 0.2s ease-in-out");

        Icon cardIcon = icon.create();
        cardIcon.setSize("40px");
        cardIcon.setColor(recommended ? "var(--lumo-primary-color)" : FrontendColor.TEXT_VALUE_GRAY);

        Span title = new Span();
        if (titleKeyOrText.contains(".")) {
            title = new TranslatableSpan(titleKeyOrText);
        } else {
            title.setText(titleKeyOrText);
        }
        title.getStyle().set("font-weight", "bold").set("font-size", "18px").set("margin-top", "10px");

        HorizontalLayout badgeLayout = new HorizontalLayout();
        badgeLayout.setSpacing(true);

        TranslatableSpan typeBadge = new TranslatableSpan(badgeKey);
        typeBadge.getStyle()
                .set("font-size", "10px").set("padding", "3px 8px")
                .set("border-radius", "10px").set("background-color", "var(--lumo-contrast-10pct)");
        badgeLayout.add(typeBadge);

        if (!isImplemented) {
            TranslatableSpan soonBadge = new TranslatableSpan("setup.pool.badge.coming_soon");
            soonBadge.getStyle()
                    .set("font-size", "10px").set("padding", "3px 8px")
                    .set("border-radius", "10px").set("background-color", "var(--lumo-error-color-10pct)")
                    .set("color", "var(--lumo-error-text-color)");
            badgeLayout.add(soonBadge);

            card.getStyle().set("opacity", "0.5").set("cursor", "not-allowed");
            cardIcon.setColor("var(--lumo-contrast-50pct)");

        } else if (alreadyConnected) {
            TranslatableSpan connectedBadge = new TranslatableSpan("setup.pool.badge.already_connected");
            connectedBadge.getStyle()
                    .set("font-size", "10px").set("padding", "3px 8px")
                    .set("border-radius", "10px").set("background-color", "var(--lumo-success-color-10pct)")
                    .set("color", "var(--lumo-success-text-color)");
            badgeLayout.add(connectedBadge);
            card.getStyle().set("opacity", "0.5").set("cursor", "not-allowed");

        } else {
            card.getStyle().set("cursor", "pointer");

            if (recommended) {
                TranslatableSpan recBadge = new TranslatableSpan("setup.pool.badge.recommended");
                recBadge.getStyle()
                        .set("font-size", "10px").set("padding", "3px 8px")
                        .set("border-radius", "10px").set("background-color", "var(--lumo-primary-color-10pct)")
                        .set("color", "var(--lumo-primary-text-color)");
                badgeLayout.add(recBadge);
            }

            card.getElement().executeJs(
                    "this.addEventListener('mouseenter', () => { if(this.style.borderColor !== 'var(--lumo-primary-color)') this.style.borderColor = 'var(--lumo-contrast-30pct)'; });" +
                            "this.addEventListener('mouseleave', () => { if(this.style.borderColor !== 'var(--lumo-primary-color)') this.style.borderColor = '#222226'; });"
            );
        }

        card.add(cardIcon, title, badgeLayout);
        return card;
    }

    private VerticalLayout createPlugAndPlayLayout() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.getStyle()
                .set("background-color", FrontendColor.CARD_BACKGROUND_COLOR)
                .set("border-radius", "12px")
                .set("border", "1px solid #222226");

        MarkdownView introMarkdown = new MarkdownView();
        introMarkdown.setMarkdown(getTranslation("setup.pool.oceans.desc"));

        VerticalLayout disclaimer = createOceansDisclaimer();

        bolt12AddressField.setLabel(getTranslation("setup.pool.oceans.bolt12"));
        bolt12AddressField.setWidthFull();
        bolt12AddressField.setMaxWidth("700px");
        bolt12AddressField.setReadOnly(true);
        bolt12AddressField.setHelperText(getTranslation("setup.pool.oceans.bolt12_help"));

        try {
            LightningWalletService walletService = SpringContextHelper.getBean(LightningWalletService.class);
            String bolt12Offer = walletService.claimFreeLightningAddress();
            bolt12AddressField.setValue(bolt12Offer);
        } catch (Exception e) {
            bolt12AddressField.setValue(getTranslation("setup.pool.oceans.bolt12_loading"));
            bolt12AddressField.setHelperText(getTranslation("setup.pool.oceans.bolt12_loading_help"));
        }

        layout.add(introMarkdown, bolt12AddressField, disclaimer);
        return layout;
    }

    private VerticalLayout createOceansDisclaimer() {
        VerticalLayout disclaimerCard = new VerticalLayout();
        disclaimerCard.setPadding(true);
        disclaimerCard.setSpacing(true);
        disclaimerCard.getStyle().set("background-color", "var(--lumo-warning-color-10pct)");
        disclaimerCard.getStyle().set("border", "1px solid var(--lumo-warning-color-50pct)");
        disclaimerCard.getStyle().set("border-radius", "var(--lumo-border-radius-m)");

        HorizontalLayout header = new HorizontalLayout();
        header.setAlignItems(Alignment.CENTER);
        Icon warningIcon = VaadinIcon.WARNING.create();
        warningIcon.setColor("var(--lumo-warning-text-color)");

        TranslatableSpan title = new TranslatableSpan("setup.pool.oceans.disclaimer.title");
        title.getStyle().set("font-weight", "bold");
        title.getStyle().set("color", "var(--lumo-warning-text-color)");
        header.add(warningIcon, title);

        TranslatableSpan content = new TranslatableSpan("setup.pool.oceans.disclaimer.text");
        content.getStyle().set("font-size", "var(--lumo-font-size-s)");

        disclaimerCard.add(header, content);
        return disclaimerCard;
    }

    private HorizontalLayout createManualLayout(MiningPoolSetupProvider strategy) {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setAlignItems(Alignment.START);
        layout.getStyle()
                .set("background-color", FrontendColor.CARD_BACKGROUND_COLOR)
                .set("border-radius", "12px")
                .set("border", "1px solid #222226")
                .set("padding", "var(--lumo-space-l)");

        VerticalLayout instructionsContainer = new VerticalLayout();
        instructionsContainer.setWidth("55%");
        instructionsContainer.setPadding(false);

        MarkdownView manualMarkdownView = new MarkdownView();
        manualMarkdownView.setMarkdown(strategy.getMarkdownInstructions());
        instructionsContainer.add(manualMarkdownView);

        VerticalLayout manualFormContainer = new VerticalLayout();
        manualFormContainer.setWidth("45%");
        manualFormContainer.setSpacing(true);
        manualFormContainer.setPadding(false);
        manualFormContainer.getStyle()
                .set("background-color", "var(--lumo-contrast-5pct)")
                .set("border-radius", "8px")
                .set("padding", "var(--lumo-space-m)");

        TranslatableH3 formTitle = new TranslatableH3("setup.pool.manual.config_title");
        formTitle.getStyle().set("margin-top", "0");

        FormLayout manualFormLayout = new FormLayout();

        Runnable[] fieldsChangedProxy = new Runnable[1];
        Runnable onFieldsChanged = () -> {
            if (fieldsChangedProxy[0] != null) {
                fieldsChangedProxy[0].run();
            }
        };

        strategy.buildForm(manualFormLayout, onFieldsChanged);

        CheckConnection checkConnection = strategy.buildConnectionChecker(success -> {
            this.manualConnectionVerified = success;
            onValidationChanged.run();
        });

        checkConnection.getTestConnectionButton().setEnabled(strategy.isInputValid());

        fieldsChangedProxy[0] = () -> {
            manualConnectionVerified = false;
            checkConnection.reset();
            checkConnection.getTestConnectionButton().setEnabled(strategy.isInputValid());
            onValidationChanged.run();
        };

        manualFormContainer.add(formTitle, manualFormLayout, checkConnection);
        layout.add(instructionsContainer, manualFormContainer);
        return layout;
    }

    public void setExistingSite(PVSiteEntity existingSite) {
        this.existingSite = existingSite;
        buildCards();
    }

    @Override
    public String getTitleTranslationKey() {
        return "setup.pool.step_title";
    }

    @Override
    public boolean isValid() {
        if (isPlugAndPlaySelected) {
            return true;
        } else {
            return manualConnectionVerified;
        }
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        bolt12AddressField.setLabel(getTranslation("setup.pool.oceans.bolt12"));
    }
}