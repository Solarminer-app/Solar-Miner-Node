package de.verdox.pv_miner.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.verdox.pv_miner.util.Money;
import de.verdox.pv_miner.util.currency.CustomCurrency;

import java.util.Locale;

/**
 * Shared monetary representation for both frontends and finance reports.
 */
public record MoneyDto(double amount, String currency, @JsonInclude(JsonInclude.Include.NON_NULL) String formatted) {
    public MoneyDto {
        currency = currency == null || currency.isBlank() ? "EUR" : currency;
        amount = Double.isFinite(amount) ? amount : 0;
    }

    public MoneyDto(double amount, String currency) {
        this(amount, currency, null);
    }

    public static MoneyDto from(Money money) {
        if (money == null) {
            return new MoneyDto(0, "EUR");
        }
        return new MoneyDto(money.getRawMoneyAmount(), money.getCurrency().getCurrencyCode());
    }

    public static MoneyDto of(double amount, CustomCurrency currency) {
        return new MoneyDto(amount, currency.getCurrencyCode());
    }

    /**
     * Compatibility accessor for the legacy Vaadin view and report templates.
     */
    @JsonIgnore
    public double getRawMoneyAmount() {
        return amount;
    }

    @Override
    public String toString() {
        return formatted == null || formatted.isBlank()
                ? CustomCurrency.getInstance(currency).format(amount, localeForCurrency(currency))
                : formatted;
    }

    private static Locale localeForCurrency(String currencyCode) {
        return switch (currencyCode) {
            case "USD" -> Locale.US;
            case "EUR", "CHF" -> Locale.GERMANY;
            case "JPY" -> Locale.JAPAN;
            case "GBP" -> Locale.UK;
            default -> Locale.US;
        };
    }
}
