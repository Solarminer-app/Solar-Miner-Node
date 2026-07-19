package de.verdox.solarminer.rest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestValueParserTest {
    @Test
    void normalizesMeasurementsToBaseUnits() {
        assertEquals("2400", RestValueParser.normalizeNumber("2.4 kw"));
        assertEquals("2400", RestValueParser.normalizeNumber("2,4 KW"));
        assertEquals("50", RestValueParser.normalizeNumber("50W"));
        assertEquals("-1250", RestValueParser.normalizeNumber("-1.25 kWh"));
        assertEquals("2500000", RestValueParser.normalizeNumber("2.5 MW"));
        assertEquals("0.25", RestValueParser.normalizeNumber("250 mW"));
        assertEquals("21.5", RestValueParser.normalizeNumber("21.5 °C"));
        assertEquals("2400", RestValueParser.normalizeNumber("power: 2.4 kW (live)"));
    }

    @Test
    void preservesPlainAndScientificNumbers() {
        assertEquals("1234", RestValueParser.normalizeNumber("1234"));
        assertEquals("2400", RestValueParser.normalizeNumber("2.4e3 W"));
        assertEquals(2400, RestParameterType.INT.parser().apply(RestValueParser.normalizeNumber("2.4 kW")));
    }

    @Test
    void rejectsValuesWithoutNumbers() {
        assertThrows(NumberFormatException.class, () -> RestValueParser.normalizeNumber("not available"));
    }
}
