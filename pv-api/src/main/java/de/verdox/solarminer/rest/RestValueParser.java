package de.verdox.solarminer.rest;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Normalizes human readable measurements to their numeric SI base-unit value. */
public final class RestValueParser {
    private static final Pattern NUMBER = Pattern.compile("[-+]?(?:\\d+(?:[.,]\\d+)?|[.,]\\d+)(?:[eE][-+]?\\d+)?");
    private static final Pattern UNIT = Pattern.compile("^\\s*([\\p{L}µμ]+)");
    private static final Set<String> PREFIXABLE_UNITS = Set.of(
            "w", "wp", "wh", "v", "a", "ah", "hz", "j", "va", "var", "varh",
            "g", "m", "s", "pa", "b", "byte", "bit", "l"
    );
    private static final Map<Character, BigDecimal> CASE_SENSITIVE_PREFIXES = Map.of(
            'M', new BigDecimal("1000000"),
            'G', new BigDecimal("1000000000"),
            'm', new BigDecimal("0.001"),
            'u', new BigDecimal("0.000001"),
            'µ', new BigDecimal("0.000001"),
            'μ', new BigDecimal("0.000001")
    );

    private RestValueParser() {
    }

    public static String normalizeNumber(String rawValue) {
        if (rawValue == null) throw new NumberFormatException("REST value is null");
        String normalizedText = rawValue.trim().replace('\u2212', '-').replace('\u00a0', ' ');
        Matcher numberMatcher = NUMBER.matcher(normalizedText);
        if (!numberMatcher.find()) {
            throw new NumberFormatException("REST value does not contain a number: '" + abbreviated(normalizedText) + "'");
        }

        String numberText = numberMatcher.group().replace(',', '.');
        BigDecimal value = new BigDecimal(numberText);
        String suffix = normalizedText.substring(numberMatcher.end());
        Matcher unitMatcher = UNIT.matcher(suffix);
        if (unitMatcher.find()) value = value.multiply(multiplier(unitMatcher.group(1)));
        return value.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal multiplier(String unit) {
        if (unit == null || unit.length() < 2) return BigDecimal.ONE;
        char prefix = unit.charAt(0);
        String baseUnit = unit.substring(1).toLowerCase(Locale.ROOT);
        if (!PREFIXABLE_UNITS.contains(baseUnit)) return BigDecimal.ONE;
        if (prefix == 'k' || prefix == 'K') return new BigDecimal("1000");
        return CASE_SENSITIVE_PREFIXES.getOrDefault(prefix, BigDecimal.ONE);
    }

    private static String abbreviated(String value) {
        return value.length() <= 120 ? value : value.substring(0, 117) + "...";
    }
}
