package de.verdox.pv_miner.frontend.components.translatable;

import com.vaadin.flow.i18n.LocaleChangeObserver;

public interface TranslatableComponent extends LocaleChangeObserver {
    String getTranslationKey();

    void setTranslationParameters(Object... params);
}