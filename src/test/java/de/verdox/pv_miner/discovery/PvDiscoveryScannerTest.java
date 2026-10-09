package de.verdox.pv_miner.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PvDiscoveryScannerTest {
    @Test
    void acceptsPrivateNetworksAndRejectsPublicOrMalformedTargets() {
        for (String subnet : List.of("10.0.0.", "192.168.178.", "172.16.0.", "172.31.255."))
            assertEquals(subnet, PvDiscoveryScanner.validateSubnet(subnet));
        for (String subnet : List.of("8.8.8.", "127.0.0.", "169.254.1.", "172.32.0.", "192.168.999.", "localhost", "192.168.1.1", "192.168.1.;"))
            assertThrows(ResponseStatusException.class, () -> PvDiscoveryScanner.validateSubnet(subnet));
    }

    @Test
    void returnsCompleteSnapshotForFinishedScan() {
        var result = new PvDiscoveryScanner().scan(List.of("a", "b"), List::of, Duration.ofSeconds(2));
        assertTrue(result.complete());
        assertEquals(2, result.checkedHosts());
        assertTrue(result.devices().containsAll(List.of("a", "b")));
        assertThrows(UnsupportedOperationException.class, () -> result.devices().add("late"));
    }

    @Test
    void deadlineReportsPartialResultsAndInterruptsOutstandingWork() throws Exception {
        CountDownLatch interrupted = new CountDownLatch(1);
        var result = new PvDiscoveryScanner().scan(List.of("fast", "slow"), host -> {
            if (host.equals("fast")) return List.of(host);
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException exception) { interrupted.countDown(); Thread.currentThread().interrupt(); }
            return List.of("late");
        }, Duration.ofMillis(200));
        assertFalse(result.complete());
        assertEquals(List.of("fast"), result.devices());
        assertTrue(interrupted.await(2, TimeUnit.SECONDS));
        assertEquals(List.of("fast"), result.devices());
    }

    @Test
    void doesNotHideProbeFailuresAsSuccessfulEmptyScan() {
        assertThrows(ResponseStatusException.class, () -> new PvDiscoveryScanner().scan(List.of("a"), host -> {
            throw new IllegalStateException("broken profile storage");
        }, Duration.ofSeconds(2)));
    }
}
