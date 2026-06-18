package de.verdox.pv_miner.frontend.user;

import com.vaadin.flow.component.UI;
import de.verdox.pv_miner.util.currency.CustomCurrency;

import java.util.EventObject;

public class CurrencyChangeEvent extends EventObject {
    private final CustomCurrency currency;

    /**
     * Currency change event constructor.
     *
     * @param ui       The ui on which the Event initially occurred.
     * @param currency new currency that was set
     */
    public CurrencyChangeEvent(UI ui, CustomCurrency currency) {
        super(ui);
        this.currency = currency;
    }

    /**
     * Get the new currency that was set.
     *
     * @return set currency
     */
    public CustomCurrency getCurrency() {
        return currency;
    }

    /**
     * Returns the UI where the currency changed in.
     *
     * @return the ui
     */
    public UI getUI() {
        return (UI) getSource();
    }
}
