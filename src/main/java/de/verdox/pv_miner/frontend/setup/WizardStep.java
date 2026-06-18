package de.verdox.pv_miner.frontend.setup;

import com.vaadin.flow.component.Component;

public interface WizardStep {
    String getTitleTranslationKey();

    default Component getComponent() {
        if (this instanceof Component) {
            return (Component) this;
        }
        throw new IllegalStateException("The class " + this.getClass().getSimpleName()
                + " needs to extend com.vaadin.flow.component.Component erben or override getComponent().");
    }

    boolean isValid();


    default void onEnter() {
    }
}