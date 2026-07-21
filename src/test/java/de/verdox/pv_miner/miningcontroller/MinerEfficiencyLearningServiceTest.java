package de.verdox.pv_miner.miningcontroller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinerEfficiencyLearningServiceTest {
    @Test
    void groupsPowerTargetsIntoStableBuckets() {
        assertEquals(1_000, MinerEfficiencyLearningService.bucketFor(1_100));
        assertEquals(1_250, MinerEfficiencyLearningService.bucketFor(1_150));
    }

    @Test
    void limitsAndSmoothsSuddenEfficiencyChanges() {
        assertEquals(30.6, MinerEfficiencyLearningService.blend(30, 60), 0.0001);
        assertEquals(29.4, MinerEfficiencyLearningService.blend(30, 10), 0.0001);
    }
}
