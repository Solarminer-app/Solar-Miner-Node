package de.verdox.pv_miner.frontend;

import com.vaadin.flow.i18n.I18NProvider;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

@Component
public class VaadinI18nProvider implements I18NProvider {
    public static final String BUNDLE_PREFIX = "messages";

    private final MessageSource messageSource;

    public final Locale LOCALE_DE = Locale.GERMAN;
    public final Locale LOCALE_EN = Locale.ENGLISH;
    public final Locale LOCALE_ES = new Locale("es", "ES");
    public final Locale LOCALE_FR = Locale.FRANCE;

    private final List<Locale> locales = List.of(LOCALE_DE, LOCALE_EN, LOCALE_ES, LOCALE_FR);

    public VaadinI18nProvider(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    public List<Locale> getProvidedLocales() {
        return locales;
    }

    @Override
    public String getTranslation(String key, Locale locale, Object... params) {
        if (key == null) {
            LoggerFactory.getLogger(VaadinI18nProvider.class.getName()).warn("Got lang request for key with null value!");
            return "";
        }

        if(key.isBlank()) {
            return "";
        }

        final ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_PREFIX, locale);

        String value;
        try {
            value = bundle.getString(key);
        } catch (final MissingResourceException e) {
            LoggerFactory.getLogger(VaadinI18nProvider.class.getName()).warn("Missing translation for locale {}: {}", locale.getLanguage(), e.getKey());
            return "!" + locale.getLanguage() + ": " + key;
        }
        if (params.length > 0) {
            value = MessageFormat.format(value, params);
        }
        return value;
    }
}
