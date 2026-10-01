package de.verdox.pv_miner.core.miner.twentyoneenergy;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TwentyOneEnergyPowerMapperTest {
    private final TwentyOneEnergyPowerMapper mapper = new TwentyOneEnergyPowerMapper(Map.of(
            0, 100L, 1, 300L, 2, 500L, 3, 800L, 4, 1100L));

    @Test
    void floorsBudgetsAndNeverExceedsThem() {
        assertEquals(2, mapper.levelForBudget(799).orElseThrow());
        assertEquals(4, mapper.levelForBudget(1200).orElseThrow());
        assertTrue(mapper.levelForBudget(99).isEmpty());
    }

    @Test
    void rejectsIncompleteOrNonMonotoneMaps() {
        assertThrows(IllegalArgumentException.class, () -> new TwentyOneEnergyPowerMapper(Map.of(0, 0L)));
        assertThrows(IllegalArgumentException.class, () -> new TwentyOneEnergyPowerMapper(Map.of(
                0, 100L, 1, 300L, 2, 300L, 3, 800L, 4, 1100L)));
    }
}
