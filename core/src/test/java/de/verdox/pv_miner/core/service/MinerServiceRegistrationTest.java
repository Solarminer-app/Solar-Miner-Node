package de.verdox.pv_miner.core.service;

import de.verdox.pv_miner.core.miner.MinerDataRegistry;
import de.verdox.pv_miner.core.miner.MiningOS;
import de.verdox.pv_miner.core.miner.braiins.MinerController;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinerServiceRegistrationTest {
    private static final MinerDetails DETAILS = new MinerDetails(null, "192.168.1.42", 8084, "", "");

    @Test
    void oneRegisteredInstanceHandlesStatsControlAndPoolConfiguration() {
        ProxyDiscoveryService proxyDiscoveryService = mock(ProxyDiscoveryService.class);
        when(proxyDiscoveryService.getCurrentProxyIp()).thenReturn("192.168.1.10");
        DevFeeService devFeeService = mock(DevFeeService.class);
        MinerDataRegistry minerDataRegistry = mock(MinerDataRegistry.class);
        MinerController controller = mock(MinerController.class);
        MinerStats stats = new MinerStats(new MinerStats.MinerIdentity("miner", "", "model"), "miner",
                MinerStats.MinerStatus.MINING, 100, 0, 100, 100, 100, 1, 1, java.util.List.of(), java.util.List.of());
        when(controller.startMining(DETAILS)).thenReturn(true);
        when(controller.setPowerTarget(DETAILS, 120)).thenReturn(true);
        when(controller.queryStats("miner", DETAILS)).thenReturn(stats);
        when(controller.setPoolTarget(DETAILS, "stratum+tcp://192.168.1.10:3335", "pool.example:3333;worker;x")).thenReturn(true);

        AtomicReference<MinerControllerRegistration.PoolTargetRequest> poolRequest = new AtomicReference<>();
        Map<MiningOS, MinerControllerRegistration> registrations = new EnumMap<>(MiningOS.class);
        registrations.put(MiningOS.AGENT, new MinerControllerRegistration(
                controller,
                Set.of(MinerCapability.DYNAMIC_POWER_SCALING, MinerCapability.POOL_CONFIGURATION),
                request -> {
                    poolRequest.set(request);
                    return controller.setPoolTarget(request.minerDetails(), request.proxyUrl(3335), request.proxyUserName());
                }
        ));
        MinerService service = new MinerService(proxyDiscoveryService, devFeeService, minerDataRegistry, registrations);

        assertTrue(service.startMining(MiningOS.AGENT, DETAILS));
        assertTrue(service.setPowerTarget(MiningOS.AGENT, DETAILS, 120));
        assertEquals(stats, service.queryStats(MiningOS.AGENT, "miner", DETAILS));
        assertTrue(service.setPoolTarget(MiningOS.AGENT, DETAILS, "stratum+tcp://pool.example:3333", "worker", "ref"));

        verify(controller).startMining(DETAILS);
        verify(controller).setPowerTarget(DETAILS, 120);
        verify(controller).queryStats("miner", DETAILS);
        verify(controller).setPoolTarget(DETAILS, "stratum+tcp://192.168.1.10:3335", "pool.example:3333;worker;x");
        assertEquals("ref", poolRequest.get().referralCode());
    }

    @Test
    void absentProxyAndUnregisteredOperatingSystemsAreExplicitlyUnsupported() {
        ProxyDiscoveryService proxyDiscoveryService = mock(ProxyDiscoveryService.class);
        when(proxyDiscoveryService.getCurrentProxyIp()).thenReturn(null);
        MinerController controller = mock(MinerController.class);
        Map<MiningOS, MinerControllerRegistration> registrations = new EnumMap<>(MiningOS.class);
        registrations.put(MiningOS.ANTMINER_STOCK_OS, new MinerControllerRegistration(
                controller,
                Set.of(MinerCapability.POOL_CONFIGURATION),
                request -> request.proxyUrl(3333) != null
                        && controller.setPoolTarget(request.minerDetails(), request.proxyUrl(3333), request.proxyUserName())
        ));
        MinerService service = new MinerService(proxyDiscoveryService, mock(DevFeeService.class), mock(MinerDataRegistry.class), registrations);

        assertFalse(service.setPoolTarget(MiningOS.ANTMINER_STOCK_OS, DETAILS, "stratum+tcp://pool.example:3333", "worker", null));
        for (MiningOS miningOS : MiningOS.values()) {
            if (miningOS != MiningOS.ANTMINER_STOCK_OS) {
                assertFalse(service.startMining(miningOS, DETAILS), miningOS + " must remain explicitly unsupported");
            }
        }
    }

    @Test
    void twentyOneEnergyStaysMonitoringOnlyUntilItsVerifiedMapIsRegistered() {
        MinerService service = new MinerService(mock(ProxyDiscoveryService.class), mock(DevFeeService.class),
                new com.fasterxml.jackson.databind.ObjectMapper(), mock(MinerDataRegistry.class));

        assertFalse(service.setPowerTarget(MiningOS.TWENTY_ONE_ENERGY, DETAILS, 1000));
        assertFalse(service.setPoolTarget(MiningOS.TWENTY_ONE_ENERGY, DETAILS, "stratum+tcp://pool.example:3333", "worker", null));
    }
}
