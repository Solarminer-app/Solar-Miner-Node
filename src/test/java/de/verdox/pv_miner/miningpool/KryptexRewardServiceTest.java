package de.verdox.pv_miner.miningpool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KryptexRewardServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void groupsKryptexRewardPointsByUtcDayAndKeepsFractionalAmounts() throws Exception {
        Map<LocalDate, BigDecimal> rewards = KryptexRewardService.parseRewards(mapper.readTree("""
                [{"timestamp":1788134400000,"reward":0.000000616763836464},
                 {"timestamp":1788138000000,"reward":0.000000100000000000}]
                """));
        assertEquals(new BigDecimal("0.000000716763836464"), rewards.get(LocalDate.of(2026, 8, 31)));
    }

    @Test
    void usesTheLatestPricePointForEachUtcDay() throws Exception {
        Map<LocalDate, BigDecimal> prices = KryptexRewardService.parsePrices(mapper.readTree("""
                [{"timestamp":1788138000000,"price":501.23}, {"timestamp":1788134400000,"price":500.12}]
                """));
        assertEquals(new BigDecimal("501.23"), prices.get(LocalDate.of(2026, 8, 31)));
    }
}
