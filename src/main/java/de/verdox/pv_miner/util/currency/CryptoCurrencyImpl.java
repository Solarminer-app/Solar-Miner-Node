package de.verdox.pv_miner.util.currency;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class CryptoCurrencyImpl implements CustomCurrency {
    static final Map<String, CryptoCurrencyImpl> KNOWN_CURRENCIES = new HashMap<>();

    public static CryptoCurrencyImpl findByCode(String code) {
        if (!KNOWN_CURRENCIES.containsKey(code)) {
            throw new IllegalArgumentException();
        }
        return KNOWN_CURRENCIES.get(code);
    }

    private final String code;
    private final String displayName;
    private final int defaultFractionDigits;
    private final String symbol;

    CryptoCurrencyImpl(String code, String displayName, int defaultFractionDigits, String symbol) {
        this.code = code;
        this.displayName = displayName;
        this.defaultFractionDigits = defaultFractionDigits;
        this.symbol = symbol;
        KNOWN_CURRENCIES.put(code, this);
    }

    @Override
    public String getCurrencyCode() {
        return code;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getDisplayName(Locale locale) {
        return displayName;
    }

    @Override
    public boolean isIso4217() {
        return false;
    }

    @Override
    public int getDefaultFractionDigits() {
        return defaultFractionDigits;
    }

    @Override
    public String getSymbol() {
        return symbol;
    }

    @Override
    public String getSymbol(Locale locale) {
        return symbol;
    }

    @Override
    public int getNumericCode() {
        return -1;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        CryptoCurrencyImpl that = (CryptoCurrencyImpl) o;
        return defaultFractionDigits == that.defaultFractionDigits && Objects.equals(code, that.code) && Objects.equals(displayName, that.displayName) && Objects.equals(symbol, that.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, displayName, defaultFractionDigits, symbol);
    }
}
