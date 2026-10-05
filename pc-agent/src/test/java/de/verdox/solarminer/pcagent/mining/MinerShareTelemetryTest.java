package de.verdox.solarminer.pcagent.mining;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MinerShareTelemetryTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void readsXmrigGoodAndTotalCounters() throws Exception {
        MinerShareTelemetry.Counters counters = MinerShareTelemetry.xmrig(
                json.readTree("{\"shares_good\":41,\"shares_total\":44}"));

        assertEquals(41L, counters.accepted());
        assertEquals(3L, counters.rejected());
    }

    @Test
    void readsBothObservedSrbMinerShapes() throws Exception {
        MinerShareTelemetry.Counters direct = MinerShareTelemetry.srbMiner(
                json.readTree("{\"accepted\":12,\"rejected\":2}"));
        MinerShareTelemetry.Counters nested = MinerShareTelemetry.srbMiner(
                json.readTree("{\"shares\":{\"accepted\":7,\"rejected\":1}}"));

        assertEquals(new MinerShareTelemetry.Counters(12L, 2L), direct);
        assertEquals(new MinerShareTelemetry.Counters(7L, 1L), nested);
    }

    @Test
    void missingCountersRemainUnavailableInsteadOfLookingLikeZeroShares() throws Exception {
        MinerShareTelemetry.Counters counters = MinerShareTelemetry.srbMiner(json.readTree("{\"uptime\":20}"));

        assertNull(counters.accepted());
        assertNull(counters.rejected());
    }
}
