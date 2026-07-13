package de.verdox.pv_miner.frontend;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.*;
import com.vaadin.flow.theme.lumo.Lumo;
import com.vaadin.flow.theme.lumo.LumoUtility;
import de.verdox.pv_miner.entity.EntityService;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableSpan;
import de.verdox.pv_miner.frontend.pvsite.dashboard.Dashboard;
import de.verdox.pv_miner.frontend.pvsite.details.PVSiteDetailsSubPage;
import de.verdox.pv_miner.frontend.pvsite.finance.PVFinanceSubPage;
import de.verdox.pv_miner.frontend.pvsite.mining.MinerClusterView;
import de.verdox.pv_miner.frontend.user.UserSessionContext;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteRef;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@CssImport("./themes/solarminer/app-layout.css")
public class AppMainLayout extends AppLayout implements AfterNavigationObserver, LocaleChangeObserver, BeforeEnterObserver {

    private final Tabs menuTabs = new Tabs();
    private final H1 title = new H1();

    private final TranslatableSpan dashboardButton = new TranslatableSpan("pv_site.subpage.header.dashboard");
    private final TranslatableSpan pvSiteInfoButton = new TranslatableSpan("pv_site.subpage.header.info");
    private final TranslatableSpan miningInfoButton = new TranslatableSpan("pv_site.subpage.header.mining");
    private final TranslatableSpan financeButton = new TranslatableSpan("pv_site.subpage.header.finance");

    private final SettingsHeader settingsHeader;
    private final EntityService entityService;
    private final UserSessionContext userSessionContext;
    private PVSiteRef pvSiteReference;

    private Dialog mobileMenuDialog;

    public AppMainLayout(@Autowired EntityService entityService, @Autowired UserSessionContext userSessionContext) {
        this.entityService = entityService;
        this.userSessionContext = userSessionContext;

        getElement().setAttribute("theme", Lumo.DARK);
        setPrimarySection(Section.NAVBAR);

        title.getStyle()
                .set("font-size", "var(--lumo-font-size-l)")
                .set("margin", "0")
                .set("color", FrontendColor.TEXT_VALUE_WHITE);

        Div desktopNavWrapper = new Div(menuTabs);
        desktopNavWrapper.addClassName("desktop-only");
        desktopNavWrapper.getStyle().set("flex-grow", "1").set("justify-content", "center");

        this.settingsHeader = new SettingsHeader();
        Div desktopSettingsWrapper = new Div(settingsHeader);
        desktopSettingsWrapper.addClassName("desktop-only");

        Button mobileMenuBtn = new Button(VaadinIcon.MENU.create());
        mobileMenuBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        mobileMenuBtn.getStyle().set("color", FrontendColor.TEXT_VALUE_WHITE).set("font-size", "24px");
        mobileMenuBtn.addClickListener(e -> openMobileMenu());

        Div mobileMenuWrapper = new Div(mobileMenuBtn);
        mobileMenuWrapper.addClassName("mobile-only");
        mobileMenuWrapper.getStyle().set("align-items", "center");

        HorizontalLayout navbar = new HorizontalLayout(title, desktopNavWrapper, desktopSettingsWrapper, mobileMenuWrapper);
        navbar.setWidthFull();
        navbar.setPadding(true);
        navbar.setAlignItems(FlexComponent.Alignment.CENTER);
        navbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        navbar.getStyle().set("background-color", "#0f0f11").set("border-bottom", "1px solid #222226");
        navbar.addClassName(LumoUtility.Padding.Horizontal.MEDIUM);

        addToNavbar(navbar);
        updateTexts();
    }

    private void openMobileMenu() {
        mobileMenuDialog = new Dialog();
        mobileMenuDialog.getElement().setAttribute("theme", "full-screen");
        mobileMenuDialog.setWidth("100vw");
        mobileMenuDialog.setHeight("100vh");
        mobileMenuDialog.getElement().getThemeList().add(Lumo.DARK);

        Button closeBtn = new Button(VaadinIcon.CLOSE.create(), e -> mobileMenuDialog.close());
        closeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_LARGE);
        closeBtn.getStyle().set("color", "white").set("margin", "16px");

        HorizontalLayout header = new HorizontalLayout(closeBtn);
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        VerticalLayout content = new VerticalLayout();
        content.setAlignItems(FlexComponent.Alignment.START);
        content.setPadding(true);
        content.setSpacing(true);

        content.add(createMobileNavLink(Dashboard.class, VaadinIcon.HOME, getTranslation("pv_site.subpage.header.dashboard")));
        content.add(createMobileNavLink(PVSiteDetailsSubPage.class, VaadinIcon.WRENCH, getTranslation("pv_site.subpage.header.info")));
        content.add(createMobileNavLink(MinerClusterView.class, VaadinIcon.CLUSTER, getTranslation("pv_site.subpage.header.mining")));
        content.add(createMobileNavLink(PVFinanceSubPage.class, VaadinIcon.MONEY, getTranslation("pv_site.subpage.header.finance")));

        Hr separator = new Hr();
        separator.setWidthFull();
        separator.getStyle().set("border-color", "#222226").set("margin", "24px 0");
        content.add(separator);

        ComboBox<SupportedLanguage> langBox = createLanguageComboBox();
        langBox.setWidthFull();
        ComboBox<CustomCurrency> currBox = createCurrencyComboBox();
        currBox.setWidthFull();
        ComboBox<String> zoneBox = createZoneComboBox();
        zoneBox.setWidthFull();

        content.add(langBox, currBox, zoneBox);

        VerticalLayout dialogLayout = new VerticalLayout(header, content);
        dialogLayout.setPadding(false);
        dialogLayout.setSpacing(false);
        dialogLayout.setSizeFull();

        mobileMenuDialog.add(dialogLayout);
        mobileMenuDialog.open();
    }

    private RouterLink createMobileNavLink(Class<? extends Component> view, VaadinIcon icon, String text) {
        RouteParameters parameters = new RouteParameters(new RouteParam("siteId", pvSiteReference.getId().toString()));
        RouterLink link = new RouterLink(view, parameters);
        link.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("font-size", "22px")
                .set("font-weight", "bold")
                .set("color", "white")
                .set("text-decoration", "none")
                .set("margin", "12px 0")
                .set("width", "100%");

        Icon i = icon.create();
        i.getStyle().set("margin-right", "24px").set("color", FrontendColor.TEXT_VALUE_GRAY);
        link.add(i, new Span(text));
        return link;
    }

    private ComboBox<SupportedLanguage> createLanguageComboBox() {
        ComboBox<SupportedLanguage> box = new ComboBox<>();
        box.setItemLabelGenerator(lang -> lang.flag() + " " + lang.name());
        List<SupportedLanguage> languages = List.of(
                new SupportedLanguage("🇺🇸", "en", getTranslation("lang.en")),
                new SupportedLanguage("🇩🇪", "de", getTranslation("lang.de"))
        );
        box.setItems(languages);
        Locale sessionLocale = userSessionContext.getLocale();

        UI.getCurrent().setLocale(sessionLocale);

        languages.stream()
                .filter(l -> l.code().equalsIgnoreCase(sessionLocale.getLanguage()))
                .findFirst()
                .ifPresentOrElse(box::setValue, () -> box.setValue(languages.getFirst()));

        box.addValueChangeListener(event -> {
            if (event.getValue() != null && event.isFromClient()) {
                Locale locale = Locale.of(event.getValue().code());
                getUI().ifPresent(ui -> ui.setLocale(locale));
                userSessionContext.setLocale(locale);
            }
        });
        return box;
    }

    private ComboBox<CustomCurrency> createCurrencyComboBox() {
        ComboBox<CustomCurrency> box = new ComboBox<>();
        box.setItems(CustomCurrency.getAvailableCurrencies().stream()
                .filter(c -> c.getCurrencyCode().equals("EUR") || c.getCurrencyCode().equals("USD") || c.getCurrencyCode().equals("CHF"))
                .toList());
        box.setItemLabelGenerator(CustomCurrency::getCurrencyCode);
        box.setValue(userSessionContext.getCurrency() != null ? userSessionContext.getCurrency() : CustomCurrency.getInstance("EUR"));
        box.addValueChangeListener(event -> {
            if (event.getValue() != null && event.isFromClient()) {
                userSessionContext.setCurrency(event.getValue());
                Notification.show(getTranslation("notification.currency_change", event.getValue().getCurrencyCode()));
            }
        });
        return box;
    }

    private ComboBox<String> createZoneComboBox() {
        ComboBox<String> box = new ComboBox<>();
        box.setItems(ZoneId.getAvailableZoneIds().stream().sorted().toList());
        box.setValue(userSessionContext.getZoneId() != null ? userSessionContext.getZoneId().getId() : ZoneId.systemDefault().getId());
        box.addValueChangeListener(event -> {
            if (event.getValue() != null && event.isFromClient()) {
                userSessionContext.setZoneId(ZoneId.of(event.getValue()));
            }
        });
        return box;
    }

    private void setupNavigationMenu() {
        if (menuTabs.getTabCount() != 0) {
            return;
        }
        menuTabs.addThemeVariants(com.vaadin.flow.component.tabs.TabsVariant.LUMO_MINIMAL);
        menuTabs.getStyle().set("margin", "0");

        menuTabs.add(
                createRouterTab(Dashboard.class, VaadinIcon.HOME.create(), dashboardButton),
                createRouterTab(PVSiteDetailsSubPage.class, VaadinIcon.WRENCH.create(), pvSiteInfoButton),
                createRouterTab(MinerClusterView.class, VaadinIcon.CLUSTER.create(), miningInfoButton),
                createRouterTab(PVFinanceSubPage.class, VaadinIcon.MONEY.create(), financeButton)
        );
    }

    private <C extends Component> Tab createRouterTab(Class<? extends C> view, Icon icon, Span text) {
        RouteParameters parameters = new RouteParameters(new RouteParam("siteId", pvSiteReference.getId().toString()));
        RouterLink link = new RouterLink(view, parameters);
        link.add(icon, text);
        return new Tab(link);
    }

    private void updateTexts() {
        title.setText(getTranslation("app.title"));
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        updateTexts();
        settingsHeader.localeChange(event);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String parameter = event.getRouteParameters().get("siteId").orElseThrow();
        try {
            UUID siteUuid = UUID.fromString(parameter);
            this.pvSiteReference = entityService.pvSiteRef(siteUuid);
            setupNavigationMenu();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void afterNavigation(com.vaadin.flow.router.AfterNavigationEvent event) {
        for (int i = 0; i < menuTabs.getComponentCount(); i++) {
            if (menuTabs.getComponentAt(i) instanceof Tab tab) {
                if (tab.getChildren().anyMatch(child -> child instanceof RouterLink link && link.getHighlightCondition().shouldHighlight(link, event))) {
                    menuTabs.setSelectedTab(tab);
                    break;
                }
            }
        }

        if (mobileMenuDialog != null && mobileMenuDialog.isOpened()) {
            mobileMenuDialog.close();
        }
    }

    private class SettingsHeader extends HorizontalLayout implements LocaleChangeObserver {

        private final ComboBox<SupportedLanguage> languageComboBox;
        private final ComboBox<CustomCurrency> currencyComboBox;
        private final ComboBox<String> zoneComboBox;

        public SettingsHeader() {
            this.setAlignItems(FlexComponent.Alignment.CENTER);
            this.setSpacing(true);

            languageComboBox = createLanguageComboBox();
            languageComboBox.setWidth("140px");
            languageComboBox.addThemeName("small");

            currencyComboBox = createCurrencyComboBox();
            currencyComboBox.setWidth("100px");
            currencyComboBox.addThemeName("small");

            zoneComboBox = createZoneComboBox();
            zoneComboBox.setWidth("180px");
            zoneComboBox.addThemeName("small");

            this.add(languageComboBox, currencyComboBox, zoneComboBox);
            updateComponentTexts();
        }

        private void updateComponentTexts() {
            languageComboBox.setPlaceholder(getTranslation("placeholder.language"));
            currencyComboBox.setPlaceholder(getTranslation("placeholder.currency"));
            zoneComboBox.setPlaceholder(getTranslation("placeholder.timezone"));
        }

        @Override
        public void localeChange(LocaleChangeEvent event) {
            updateComponentTexts();
        }
    }

    private record SupportedLanguage(String flag, String code, String name) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SupportedLanguage that)) return false;
            return code.equalsIgnoreCase(that.code);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(code.toLowerCase());
        }
    }
}