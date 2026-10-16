package de.verdox.pv_miner.miningcontroller;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.data.MinerStats;

import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MinerEntity;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import static de.verdox.pv_miner.miningcontroller.EconomicDispatchService.EnergyTier.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class EconomicDispatchServiceTest {
    @Test
    void batteryBandsAndPvBudgetSeparateGridAndPvOnlyWorkers() {
        // EUR/kWh: feed-in 0.08, grid 0.30, required margin 5% -> 0.315.
        assertEquals(PV, EconomicDispatchService.energyTier(0.20, 180, 85, 70, 10, 0, 0.08, 0.315));
        assertEquals(PV, EconomicDispatchService.energyTier(0.20, 180, 40, 70, 10, 200, 0.08, 0.315));
        assertEquals(OFF, EconomicDispatchService.energyTier(0.20, 180, 40, 70, 10, 100, 0.08, 0.315));
        assertEquals(GRID, EconomicDispatchService.energyTier(0.32, 180, 10, 70, 10, 0, 0.08, 0.315));
        assertEquals(OFF, EconomicDispatchService.energyTier(0.32, 180, 9.9, 70, 10, 0, 0.08, 0.315));
        assertEquals(PV, EconomicDispatchService.energyTier(0.20, 180, 9.9, 70, 10, 200, 0.08, 0.315));
        assertEquals(OFF, EconomicDispatchService.energyTier(0.20, 180, 9.9, 70, 10, 100, 0.08, 0.315));
        assertEquals(OFF, EconomicDispatchService.energyTier(0.08, 180, 90, 70, 10, 200, 0.08, 0.315));
        assertEquals(GRID, EconomicDispatchService.energyTier(0.32, 180, 0, 70, 0, 0, 0.08, 0.315));
        assertEquals(OFF, EconomicDispatchService.energyTier(0.31, 180, 0, 70, 0, 0, 0.08, 0.315));
    }

    @Test
    void locallyManagedWorkerReceivesOnOffPlanWithoutCoinAssignment() throws Exception {
        ObjectMapper json = new ObjectMapper();
        MinerApiClient api = mock(MinerApiClient.class);
        MinerEntity<?> miner = mock(MinerEntity.class);
        String recent = Instant.now().toString();
        JsonNode capabilities = json.readTree("""
                {"protocolVersion":1,"workers":[{"workerId":"GPU-a","externalControlEnabled":true,
                "coinPolicy":"AUTO","powerMode":"EFFICIENCY","selectedCoin":"pearl","coins":[
                  {"coin":"pearl","configured":true,"feeRouteReady":true,"usdPerDay":10,
                   "marketUpdatedAt":"%s","profile":{"watts":100,"measuredAt":"%s"}},
                  {"coin":"ravencoin","configured":true,"feeRouteReady":true,"usdPerDay":20,
                   "marketUpdatedAt":"%s","profile":{"watts":100,"measuredAt":"%s"}}
                ]}]}""".formatted(recent, recent, recent, recent));
        when(api.getAgentEconomicCapabilities(any())).thenReturn(capabilities);
        when(api.applyAgentEconomicPlan(any(), any())).thenReturn(json.readTree("{\"accepted\":true}"));
        EconomicDispatchService service = new EconomicDispatchService(api, null, null, null, List.of(), null, json);

        service.dispatchAgent(miner, 80, 70, 10, 0, 0.08, 0.315, 1);

        ArgumentCaptor<JsonNode> plan = ArgumentCaptor.forClass(JsonNode.class);
        verify(api).applyAgentEconomicPlan(any(), plan.capture());
        assertTrue(plan.getValue().path("assignments").isEmpty());
        assertFalse(plan.getValue().path("activeWorkerIds").isEmpty());
        assertEquals("GPU-a", plan.getValue().path("activeWorkerIds").get(0).asText());
    }

    @Test
    void stoppedAsicCanUseItsConfiguredEfficiencyForAColdStartEstimate() {
        MinerEntity<?> miner = mock(MinerEntity.class);
        var learning = mock(MinerEfficiencyLearningService.class);
        UUID id = UUID.randomUUID();
        when(miner.getId()).thenReturn(id);
        when(miner.getMaxPowerTarget()).thenReturn(2_000L);
        when(learning.estimate(miner, 2_000L)).thenReturn(new MinerEfficiencyLearningService.EfficiencyEstimate(
                20.0, MinerEfficiencyLearningService.EfficiencySource.NOMINAL, null, 0));
        var service = new EconomicDispatchService(null, null, null, null, List.of(), learning, new ObjectMapper());

        MinerStats estimate = service.priorOrEstimatedStats(miner, MinerStats.DEFAULT);

        assertEquals(MinerStats.MinerStatus.MINING, estimate.miningStatus());
        assertEquals(2_000, estimate.miningPowerWatts());
        assertEquals(100.0, estimate.terahashPerSecond());
    }
}
