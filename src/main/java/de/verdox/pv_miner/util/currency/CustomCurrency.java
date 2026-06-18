package de.verdox.pv_miner.util.currency;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface CustomCurrency extends Serializable {

    static Set<CustomCurrency> getAvailableCurrencies() {
        return Stream.concat(Currency.getAvailableCurrencies().stream().map(FiatCurrencyAdapter::new), CryptoCurrencyImpl.KNOWN_CURRENCIES.values().stream()).collect(Collectors.toSet());
    }

    static CustomCurrency getInstance(String currencyCode) {
        Objects.requireNonNull(currencyCode);
        try {
            var foundCurrency = Currency.getInstance(currencyCode);
            return new FiatCurrencyAdapter(foundCurrency);
        } catch (IllegalArgumentException e) {
            return CryptoCurrencyImpl.findByCode(currencyCode);
        }
    }

    CustomCurrency BTC = new CryptoCurrencyImpl("BTC", "Bitcoin", 8, "₿");

    /**
     * Returns the currency code (e.g., "EUR", "BTC").
     */
    String getCurrencyCode();

    /**
     * Returns the currency display name (e.g., "Euro", "Bitcoin").
     */
    String getDisplayName();

    /**
     * Returns the localized display name for a specific locale.
     */
    String getDisplayName(Locale locale);

    /**
     * Returns whether the currency is defined after Iso4217.
     */
    boolean isIso4217();

    /**
     * Returns the default number of fraction digits used with this currency.
     * For example, 2 for EUR, 0 for JPY, and 8 for BTC (Satoshis).
     */
    int getDefaultFractionDigits();

    /**
     * Returns the currency symbol (e.g., "€", "$", "₿").
     */
    String getSymbol();

    /**
     * Returns the currency symbol for a specific locale.
     */
    String getSymbol(Locale locale);

    /**
     * Returns the ISO 4217 numeric code for this currency.
     * Returns -1 if it's a crypto currency or has no numeric code.
     */
    int getNumericCode();

    /**
     * Formats a monetary amount into a localized string matching this currency's constraints.
     * It correctly positions the symbol (prefix or suffix) and uses regional decimal separators.
     *
     * @param amount the double value to format.
     * @param locale the target locale for regional formatting rules.
     * @return the formatted currency string.
     */
    default String format(double amount, Locale locale) {
        if (isIso4217()) {
            try {
                java.util.Currency javaCurrency = java.util.Currency.getInstance(getCurrencyCode());
                NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(locale);
                currencyFormat.setCurrency(javaCurrency);
                return currencyFormat.format(amount);
            } catch (IllegalArgumentException ignored) {
            }
        }

        NumberFormat numberFormat = NumberFormat.getNumberInstance(locale);
        numberFormat.setMinimumFractionDigits(getDefaultFractionDigits());
        numberFormat.setMaximumFractionDigits(getDefaultFractionDigits());
        String formattedNumber = numberFormat.format(amount);

        NumberFormat sampleFormat = NumberFormat.getCurrencyInstance(locale);
        String sampleOutput = sampleFormat.format(1.0).trim();
        String sampleJavaSymbol = sampleFormat.getCurrency().getSymbol(locale);

        if (sampleOutput.startsWith(sampleJavaSymbol)) {
            return getSymbol(locale) + " " + formattedNumber;
        } else {
            return formattedNumber + " " + getSymbol(locale);
        }
    }

    /**
     * Formats a monetary amount using the system's default locale environment.
     *
     * @param amount the double value to format.
     * @return the formatted currency string.
     */
    default String format(double amount) {
        return format(amount, Locale.getDefault());
    }
}
