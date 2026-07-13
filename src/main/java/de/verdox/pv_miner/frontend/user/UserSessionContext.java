package de.verdox.pv_miner.frontend.user;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import jakarta.servlet.http.Cookie;

import java.io.Serializable;
import java.time.ZoneId;
import java.util.Locale;

@org.springframework.stereotype.Component
@VaadinSessionScope
public class UserSessionContext implements Serializable {

    private Locale locale = Locale.ENGLISH;
    private CustomCurrency currency = CustomCurrency.getInstance("EUR");
    private ZoneId zoneId = ZoneId.systemDefault();

    public UserSessionContext() {

        VaadinRequest request = VaadinService.getCurrentRequest();
        if (request != null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                try {
                    switch (cookie.getName()) {
                        case "user_locale" -> this.locale = Locale.forLanguageTag(cookie.getValue());
                        case "user_currency" -> this.currency = CustomCurrency.getInstance(cookie.getValue());
                        case "user_timezone" -> this.zoneId = ZoneId.of(cookie.getValue());
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    public Locale getLocale() {
        return locale;
    }

    public void setLocale(Locale locale) {
        if (locale != null && !locale.equals(this.locale)) {
            this.locale = locale;
            saveCookie("user_locale", locale.toLanguageTag());
        }
    }

    public CustomCurrency getCurrency() {
        return currency;
    }

    public void setCurrency(CustomCurrency currency) {
        if (currency != null && !currency.equals(this.currency)) {
            this.currency = currency;
            saveCookie("user_currency", currency.getCurrencyCode());

            UI.getCurrent().getUI().ifPresent(ui -> {
                CurrencyChangeEvent event = new CurrencyChangeEvent(ui, currency);
                notifyCurrencyComponents(ui, event);
            });
        }
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public void setZoneId(ZoneId zoneId) {
        if (zoneId != null && !zoneId.equals(this.zoneId)) {
            this.zoneId = zoneId;
            saveCookie("user_timezone", zoneId.getId());

            UI.getCurrent().getUI().ifPresent(ui -> {
                TimeZoneChangeEvent event = new TimeZoneChangeEvent(ui, zoneId);
                notifyTimeZoneComponents(ui, event);
            });
        }
    }
    
    private void saveCookie(String name, String value) {
        UI.getCurrent().getPage().executeJs(
                "document.cookie = $0 + '=' + encodeURIComponent($1) + ';path=/;max-age=31536000';",
                name, value
        );
    }

    private void notifyCurrencyComponents(Component component, CurrencyChangeEvent event) {
        if (component instanceof CurrencyChangeObserver listener) {
            listener.onCurrencyChange(event);
        }
        component.getChildren().forEach(child -> notifyCurrencyComponents(child, event));
    }

    private void notifyTimeZoneComponents(Component component, TimeZoneChangeEvent event) {
        if (component instanceof TimeZoneChangeObserver listener) {
            listener.onTimeZoneChange(event);
        }
        component.getChildren().forEach(child -> notifyTimeZoneComponents(child, event));
    }
}