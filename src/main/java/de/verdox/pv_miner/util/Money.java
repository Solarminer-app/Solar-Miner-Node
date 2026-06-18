package de.verdox.pv_miner.util;

import de.verdox.pv_miner.util.currency.CustomCurrency;
import jakarta.persistence.Embeddable;

import java.util.Locale;

@Embeddable
public class Money {
    private double amount;
    private String currencyCode;

    public Money(double amount, CustomCurrency currency) {
        this.amount = amount;
        this.currencyCode = currency.getCurrencyCode();
    }

    public Money(Number amount, CustomCurrency currency) {
        this(amount.doubleValue(), currency);
    }

    public Money() {
    }

    public double getRawMoneyAmount() {
        return amount;
    }

    public CustomCurrency getCurrency() {
        return CustomCurrency.getInstance(currencyCode);
    }

    public Money multiply(Number amount) {
        return new Money(this.amount * amount.doubleValue(), CustomCurrency.getInstance(currencyCode));
    }

    public Money multiply(Money amount) {
        checkSameCurrency(amount);
        return new Money(this.amount * amount.amount, CustomCurrency.getInstance(currencyCode));
    }

    public Money divide(Number amount) {
        return new Money(this.amount / amount.doubleValue(), CustomCurrency.getInstance(currencyCode));
    }

    public Money divide(Money amount) {
        checkSameCurrency(amount);
        return new Money(this.amount / amount.amount, CustomCurrency.getInstance(currencyCode));
    }

    public Money add(Number amount) {
        return new Money(this.amount + amount.doubleValue(), CustomCurrency.getInstance(currencyCode));
    }

    public Money add(Money amount) {
        checkSameCurrency(amount);
        return new Money(this.amount + amount.amount, CustomCurrency.getInstance(currencyCode));
    }

    public Money subtract(Number amount) {
        return new Money(this.amount - amount.doubleValue(), CustomCurrency.getInstance(currencyCode));
    }

    public Money subtract(Money amount) {
        checkSameCurrency(amount);
        return new Money(this.amount - amount.amount, CustomCurrency.getInstance(currencyCode));
    }

    @Override
    public String toString() {
        Locale locale = getLocaleForCurrency(currencyCode);
        return getCurrency().format(amount, locale);
    }

    private Locale getLocaleForCurrency(String currencyCode) {
        return switch (currencyCode) {
            case "USD" -> Locale.US;
            case "EUR", "CHF" -> Locale.GERMANY;
            case "JPY" -> Locale.JAPAN;
            case "GBP" -> Locale.UK;
            default -> Locale.US; // Fallback
        };
    }

    private void checkSameCurrency(Money other) {
        if (!other.currencyCode.equals(this.currencyCode)) {
            throw new IllegalArgumentException("The provided money unit has a different currency (" + other.currencyCode + ") then this money unit (" + this.currencyCode + ")");
        }
    }
}
