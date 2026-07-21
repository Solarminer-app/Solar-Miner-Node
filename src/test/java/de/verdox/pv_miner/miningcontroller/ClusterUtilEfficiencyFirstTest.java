package de.verdox.pv_miner.miningcontroller;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClusterUtilEfficiencyFirstTest {
    private static final UUID EFFICIENT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void fillsTheMostEfficientMinerBeforeStartingTheNext() {
        var allocation = ClusterUtil.allocateEfficiencyFirst(List.of(
                candidate(SECOND, 30, 0, false),
                candidate(EFFICIENT, 20, 0, false)
        ), 4_000);

        assertEquals(3_000, allocation.get(EFFICIENT), 0.001);
        assertEquals(1_000, allocation.get(SECOND), 0.001);
    }

    @Test
    void lockedEfficientMinerBelowMaximumBlocksTheNextMiner() {
        var allocation = ClusterUtil.allocateEfficiencyFirst(List.of(
                candidate(EFFICIENT, 20, 1_500, true),
                candidate(SECOND, 30, 0, false)
        ), 4_000);

        assertEquals(1_500, allocation.get(EFFICIENT), 0.001);
        assertEquals(0, allocation.get(SECOND), 0.001);
    }

    @Test
    void continuesWithTheNextMinerOnceTheLockedEfficientMinerIsFull() {
        var allocation = ClusterUtil.allocateEfficiencyFirst(List.of(
                candidate(EFFICIENT, 20, 3_000, true),
                candidate(SECOND, 30, 0, false)
        ), 4_000);

        assertEquals(3_000, allocation.get(EFFICIENT), 0.001);
        assertEquals(1_000, allocation.get(SECOND), 0.001);
    }

    @Test
    void doesNotSkipAnEfficientMinerWhoseMinimumCannotBeReached() {
        var allocation = ClusterUtil.allocateEfficiencyFirst(List.of(
                candidate(EFFICIENT, 20, 0, false),
                candidate(SECOND, 30, 0, false)
        ), 750);

        assertEquals(0, allocation.get(EFFICIENT), 0.001);
        assertEquals(0, allocation.get(SECOND), 0.001);
    }

    @Test
    void treatsTheHighestReachableStepAsFullPower() {
        var allocation = ClusterUtil.allocateEfficiencyFirst(List.of(
                new ClusterUtil.EfficiencyCandidate(EFFICIENT, 20, 800, 3_100, 250, 0, false, null),
                candidate(SECOND, 30, 0, false)
        ), 4_000);

        assertEquals(3_000, allocation.get(EFFICIENT), 0.001);
        assertEquals(1_000, allocation.get(SECOND), 0.001);
    }

    @Test
    void roundsEveryAllocationDownToItsConfiguredStepSize() {
        var allocation = ClusterUtil.allocateEfficiencyFirst(List.of(
                candidate(EFFICIENT, 20, 0, false),
                candidate(SECOND, 30, 0, false)
        ), 4_124);

        assertEquals(3_000, allocation.get(EFFICIENT), 0.001);
        assertEquals(1_000, allocation.get(SECOND), 0.001);
    }

    @Test
    void manualPriorityOverridesMeasuredEfficiency() {
        var allocation = ClusterUtil.allocateEfficiencyFirst(List.of(
                new ClusterUtil.EfficiencyCandidate(EFFICIENT, 20, 800, 3_000, 250, 0, false, null),
                new ClusterUtil.EfficiencyCandidate(SECOND, 30, 800, 3_000, 250, 0, false, 1)
        ), 3_000);

        assertEquals(3_000, allocation.get(SECOND), 0.001);
        assertEquals(0, allocation.get(EFFICIENT), 0.001);
    }

    private ClusterUtil.EfficiencyCandidate candidate(UUID id, double efficiency, double current, boolean locked) {
        return new ClusterUtil.EfficiencyCandidate(id, efficiency, 800, 3_000, 250, current, locked, null);
    }
}
