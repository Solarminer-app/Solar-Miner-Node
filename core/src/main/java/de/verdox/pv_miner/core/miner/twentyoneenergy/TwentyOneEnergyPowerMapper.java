package de.verdox.pv_miner.core.miner.twentyoneenergy;

import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;

/**
 * Maps a PV budget to a verified discrete heater level without ever exceeding it.
 */
public final class TwentyOneEnergyPowerMapper {
    private final Map<Integer, Long> wattsByLevel;

    public TwentyOneEnergyPowerMapper(Map<Integer, Long> wattsByLevel) {
        this.wattsByLevel = Map.copyOf(wattsByLevel);
        validate(this.wattsByLevel);
    }

    public OptionalInt levelForBudget(long watts) {
        return new TreeMap<>(wattsByLevel).entrySet().stream()
                // Level 0 is not assumed to be electrically off; the controller disables the heater below level 1.
                .filter(entry -> entry.getKey() > 0).filter(entry -> entry.getValue() <= watts).max(Map.Entry.comparingByValue()).map(entry -> OptionalInt.of(entry.getKey())).orElseGet(OptionalInt::empty);
    }

    public long wattsForLevel(int level) {
        Long watts = wattsByLevel.get(level);
        if (watts == null) throw new IllegalArgumentException("Unknown 21energy power level");
        return watts;
    }

    public Map<Integer, Long> levels() {
        return wattsByLevel;
    }

    private static void validate(Map<Integer, Long> levels) {
        if (levels.size() != 5 || !levels.keySet().containsAll(java.util.Set.of(0, 1, 2, 3, 4))) {
            throw new IllegalArgumentException("A verified 21energy power map must contain levels 0 through 4");
        }
        long previous = -1;
        for (int level = 0; level <= 4; level++) {
            long watts = levels.get(level);
            if (watts < 0 || watts <= previous) {
                throw new IllegalArgumentException("21energy power levels must be non-negative and strictly monotone");
            }
            previous = watts;
        }
    }
}
