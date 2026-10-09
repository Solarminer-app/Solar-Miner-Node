package de.verdox.pv_miner.miningpool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KryptexPoolApiServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesTheSamePublicBalanceShapeAsThePcAgent() throws Exception {
        assertEquals(new BigDecimal("12.3456789"),
                KryptexPoolApiService.parseBalance(mapper.readTree("{\"total\":12.3456789}")));
        assertThrows(IllegalArgumentException.class,
                () -> KryptexPoolApiService.parseBalance(mapper.readTree("{\"total\":-1}")));
        assertThrows(IllegalArgumentException.class,
                () -> KryptexPoolApiService.parseBalance(mapper.readTree("{}")));
    }
}
