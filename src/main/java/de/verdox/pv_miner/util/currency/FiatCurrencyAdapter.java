package de.verdox.pv_miner.util.currency;

import java.util.Currency;
import java.util.Locale;

/**
 * Adapter implementation linking Java's native {@link java.util.Currency}
 * to the custom {@link CustomCurrency} interface architecture.
 */
public class FiatCurrencyAdapter implements CustomCurrency {
    private final Currency currency;

    /**
     * Constructs a new adapter wrapping an existing standard Java currency instance.
     *
     * @param currency the native Java currency, must not be null.
     * @throws IllegalArgumentException if the provided currency is null.
     */
    public FiatCurrencyAdapter(Currency currency) {
        if (currency == null) {
            throw new IllegalArgumentException("Currency cannot be null");
        }
        this.currency = currency;
    }

    @Override
    public String getCurrencyCode() {
        return currency.getCurrencyCode();
    }

    @Override
    public String getDisplayName() {
        return currency.getDisplayName(Locale.getDefault());
    }

    @Override
    public String getDisplayName(Locale locale) {
        return currency.getDisplayName(locale);
    }

    @Override
    public boolean isIso4217() {
        return true;
    }

    @Override
    public int getDefaultFractionDigits() {
        return currency.getDefaultFractionDigits();
    }

    @Override
    public String getSymbol() {
        return currency.getSymbol();
    }

    @Override
    public String getSymbol(Locale locale) {
        return currency.getSymbol(locale);
    }

    @Override
    public int getNumericCode() {
        return currency.getNumericCode();
    }

    /**
     * Gets the underlying wrapped native Java currency instance.
     *
     * @return the native {@link Currency} object.
     */
    public Currency getJavaCurrency() {
        return currency;
    }
}