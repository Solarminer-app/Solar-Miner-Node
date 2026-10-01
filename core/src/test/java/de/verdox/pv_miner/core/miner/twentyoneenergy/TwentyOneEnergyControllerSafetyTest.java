package de.verdox.pv_miner.core.miner.twentyoneenergy;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;

class TwentyOneEnergyControllerSafetyTest {
    private final TwentyOneEnergyController controller = new TwentyOneEnergyController(new ObjectMapper(), false, null);
    private final MinerDetails heater = new MinerDetails(UUID.randomUUID(), "192.168.178.99", 80, "", "");

    @Test
    void monitoringOnlyControllerNeverIssuesControlRequests() {
        assertFalse(controller.startMining(heater));
        assertFalse(controller.pauseMining(heater));
        assertFalse(controller.setPowerTarget(heater, 800));
    }
}
