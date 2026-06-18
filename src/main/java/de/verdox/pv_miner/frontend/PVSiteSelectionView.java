package de.verdox.pv_miner.frontend;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.select.SelectVariant;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParam;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.theme.lumo.Lumo;
import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.entity.EntityService;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import de.verdox.pv_miner.frontend.pvsite.dashboard.Dashboard;
import de.verdox.pv_miner.frontend.setup.SetupWizard;
import org.springframework.data.domain.PageRequest;

import java.util.Locale;

@Route("")
@PageTitle("Solarminer.app - Mine Bitcoin with solar energy!")
@CssImport("./themes/solarminer/startview.css")
public class PVSiteSelectionView extends VerticalLayout implements LocaleChangeObserver {

    private final H1 title;
    private final Paragraph subtitle;
    private final ComboBox<PVSiteEntity> entitySelection;
    private final Button btnRegister;
    private final Span limitText;
    private final Span statusBadge;

    private final Span lightningText;
    private final Span restConfigText;
    private final Span modbusConfigText;

    private final long currentCount;

    private final boolean limitExceeded;

    public PVSiteSelectionView() {
        getElement().getThemeList().add(Lumo.DARK);
        setId("start-view-container");
        setSizeFull();
        setJustifyContentMode(JustifyContentMode.CENTER);
        setAlignItems(Alignment.CENTER);

        Div card = new Div();
        card.addClassName("start-card");

        Select<Locale> languageSelect = new Select<>();
        languageSelect.addClassName("language-select");
        languageSelect.addThemeVariants(SelectVariant.LUMO_SMALL);
        languageSelect.setItems(Locale.GERMAN, Locale.ENGLISH);
        languageSelect.setItemLabelGenerator(locale -> locale.getLanguage().toUpperCase());

        Locale sessionLocale = UI.getCurrent().getLocale();
        languageSelect.setValue(sessionLocale.getLanguage().equals("en") ? Locale.ENGLISH : Locale.GERMAN);

        languageSelect.addValueChangeListener(event -> {
            if (event.getValue() != null) {
                UI.getCurrent().getSession().setLocale(event.getValue());
            }
        });

        Div headerSection = new Div();
        headerSection.addClassName("start-header");

        var sunIcon = VaadinIcon.SUN_O.create();
        sunIcon.addClassName("start-logo-icon");

        title = new H1();
        subtitle = new Paragraph();
        headerSection.add(sunIcon, title, subtitle);

        entitySelection = new ComboBox<>();
        entitySelection.addClassName("start-dropdown");
        entitySelection.setItemLabelGenerator(PVSiteEntity::getName);

        entitySelection.setItems(query -> {
            PageRequest pageable = PageRequest.of(query.getPage(), query.getPageSize());
            return SpringContextHelper.getBean(PVSiteRepository.class).findAll(pageable).stream();
        });

        entitySelection.addValueChangeListener(event -> {
            if (event.getValue() != null) {
                RouteParameters params = new RouteParameters(
                        new RouteParam("siteId", event.getValue().getId().toString())
                );
                UI.getCurrent().navigate(Dashboard.class, params);
            }
        });

        long countTemp = 0;
        try {
            countTemp = SpringContextHelper.getBean(PVSiteRepository.class).count();
        } catch (Exception ignored) {
        }
        this.currentCount = countTemp;
        this.limitExceeded = currentCount >= EntityService.PV_SITE_LIMIT;

        btnRegister = new Button(VaadinIcon.PLUS.create());
        btnRegister.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        btnRegister.addClassName("start-register-btn");

        Div limitInfoContainer = new Div();
        limitInfoContainer.addClassName("limit-info-box");

        limitText = new Span();
        limitText.addClassName("limit-text");

        statusBadge = new Span();
        statusBadge.addClassName("limit-badge");
        limitInfoContainer.add(limitText, statusBadge);

        if (limitExceeded) {
            btnRegister.setEnabled(false);
            statusBadge.getElement().getThemeList().add("badge error primary small");
            card.addClassName("limit-reached-state");
        } else {
            statusBadge.getElement().getThemeList().add("badge success primary small");
            btnRegister.addClickListener(e -> {
                UI.getCurrent().navigate(SetupWizard.class);
            });
        }

        Anchor lightningLink = new Anchor("/lightning-wallet", "");
        lightningLink.setTarget("_blank"); // Öffnet in neuem Tab!
        lightningLink.addClassName("lightning-link");
        lightningText = new Span();
        lightningLink.add(VaadinIcon.BOLT.create(), lightningText);

        Div subtleLinksContainer = new Div();
        subtleLinksContainer.addClassName("subtle-links-container");

        Anchor restLink = new Anchor("/config/pv/rest", "");
        restLink.setTarget("_blank");
        restLink.addClassName("subtle-link");
        restConfigText = new Span();
        restLink.add(restConfigText);

        Anchor modbusLink = new Anchor("/config/pv/modbus/tcp", "");
        modbusLink.setTarget("_blank");
        modbusLink.addClassName("subtle-link");
        modbusConfigText = new Span();
        modbusLink.add(modbusConfigText);

        Span separator = new Span("•");
        separator.addClassName("subtle-separator");

        subtleLinksContainer.add(restLink, separator, modbusLink);

        card.add(languageSelect, headerSection, entitySelection, btnRegister, limitInfoContainer, lightningLink, subtleLinksContainer);
        add(card);

        updateTexts();
    }

    private void updateTexts() {
        title.setText(getTranslation("startview.title"));
        subtitle.setText(getTranslation("startview.subtitle"));

        entitySelection.setLabel(getTranslation("startview.dropdown.label"));
        entitySelection.setPlaceholder(getTranslation("startview.dropdown.placeholder"));

        btnRegister.setText(getTranslation("startview.button.register"));

        limitText.setText(String.format(getTranslation("startview.limit.text"), currentCount, EntityService.PV_SITE_LIMIT));

        if (limitExceeded) {
            btnRegister.setTooltipText(getTranslation("startview.limit.tooltip"));
            statusBadge.setText(getTranslation("startview.limit.reached"));
        } else {
            statusBadge.setText(getTranslation("startview.limit.available"));
        }

        lightningText.setText(getTranslation("startview.link.lightning"));
        restConfigText.setText(getTranslation("startview.link.rest"));
        modbusConfigText.setText(getTranslation("startview.link.modbus"));
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        updateTexts();
    }
}