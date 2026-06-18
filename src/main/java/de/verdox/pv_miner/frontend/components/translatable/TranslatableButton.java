package de.verdox.pv_miner.frontend.components.translatable;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.i18n.LocaleChangeEvent;

public class TranslatableButton extends Button implements TranslatableComponent {
    private static final Object[] EMPTY = new Object[0];
    private String translationKey;
    private Object[] translationParameters = EMPTY;

    public TranslatableButton() {
    }

    public TranslatableButton(String text) {
        super(text);
        setText(text);
    }

    public TranslatableButton(Component icon) {
        super(icon);
    }

    public TranslatableButton(String text, Component icon) {
        super(text, icon);
        setText(text);
    }

    public TranslatableButton(String text, ComponentEventListener<ClickEvent<Button>> clickListener) {
        super(text, clickListener);
        setText(text);
    }

    public TranslatableButton(Component icon, ComponentEventListener<ClickEvent<Button>> clickListener) {
        super(icon, clickListener);
    }

    public TranslatableButton(String text, Component icon, ComponentEventListener<ClickEvent<Button>> clickListener) {
        super(text, icon, clickListener);
        setText(text);
    }

    @Override
    public void setText(String text) {
        if(translationParameters == null) {
            translationParameters = EMPTY;
        }
        this.translationKey = text;
        super.setText(getTranslation(translationKey, translationParameters));
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        super.setText(getTranslation(translationKey, translationParameters));
    }

    @Override
    public String getTranslationKey() {
        return translationKey;
    }

    public void setTranslationParameters(Object... translationParameters) {
        if (translationParameters == null) {
            translationParameters = EMPTY;
        }
        this.translationParameters = translationParameters;
        super.setText(getTranslation(translationKey, translationParameters));
    }
}
