package de.verdox.pv_miner.frontend.components.translatable;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.i18n.LocaleChangeEvent;

public class TranslatableSpan extends Span implements TranslatableComponent {
    private static final Object[] EMPTY = new Object[0];
    private String translationKey;
    private Object[] translationParameters = EMPTY;

    public TranslatableSpan(String translationKey) {
        this.translationKey = translationKey;
        super.setText(getTranslation(translationKey));
    }

    public TranslatableSpan(String translationKey, Object... parameters) {
        this.translationKey = translationKey;
        setTranslationParameters(parameters);
    }

    @Override
    public String getTranslationKey() {
        return translationKey;
    }

    @Override
    public void setText(String translationKey) {
        this.translationKey = translationKey;
        super.setText(getTranslation(translationKey, translationParameters));
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        super.setText(getTranslation(translationKey, translationParameters));
    }

    public void setTranslationParameters(Object... translationParameters) {
        if (translationParameters == null) {
            translationParameters = EMPTY;
        }
        this.translationParameters = translationParameters;
        super.setText(getTranslation(translationKey, translationParameters));
    }
}
