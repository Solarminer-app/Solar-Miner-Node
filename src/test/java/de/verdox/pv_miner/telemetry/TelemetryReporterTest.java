package de.verdox.pv_miner.telemetry;

import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.lightning.LightningWalletService;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import de.verdox.pv_miner.statistic.daily.DailyStatisticService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelemetryReporterTest {

    private final PVSiteRepository pvSiteRepository = mock(PVSiteRepository.class);
    private final EntityQueryService queryService = mock(EntityQueryService.class);
    private final DailyStatisticService dailyStatisticService = mock(DailyStatisticService.class);
    // Unstubbed: getNodeInfo() returns null -> the batch carries no lightning id,
    // exercising the uuid fallback path.
    private final LightningWalletService walletService = mock(LightningWalletService.class);

    @Test
    void blankTelemetryUrlDisablesReporting() {
        // A blank base url disables the endpoint entirely (self-hosted/offline):
        // report() returns before touching any site, so no identity is ever minted.
        TelemetryReporter disabled = new TelemetryReporter(
                pvSiteRepository, queryService, dailyStatisticService, walletService,
                RestClient.builder(), versionProvider(null), "", "DE");
        disabled.report();

        verify(pvSiteRepository, never()).save(any());
    }

    @Test
    void mintsAndPersistsIdentityForOptedInSitesOnly() {
        PVSiteEntity optedIn = new PVSiteEntity();
        optedIn.setTelemetryOptIn(true);
        PVSiteEntity optedOut = new PVSiteEntity();
        optedOut.setTelemetryOptIn(false);
        when(pvSiteRepository.findAll()).thenReturn(List.of(optedIn, optedOut));
        // No live stat yet on the node: the tick must not blow up, report 0 for the day.
        when(dailyStatisticService.getLiveDailyStatistic(any(), anyString(), any()))
                .thenThrow(new IllegalStateException("no live daily statistic"));

        // Connection-refused endpoint: the best-effort POST throws and is swallowed.
        TelemetryReporter reporter = new TelemetryReporter(
                pvSiteRepository, queryService, dailyStatisticService, walletService,
                RestClient.builder(), versionProvider("1.0.0"), "http://127.0.0.1:9", "DE");
        reporter.report();

        verify(pvSiteRepository, times(1)).save(optedIn);
        verify(pvSiteRepository, never()).save(optedOut);
        assertNotNull(optedIn.getNodeIdentity());
        assertHasUuidShape(optedIn.getNodeIdentity().trim());
        assertNull(optedOut.getNodeIdentity());
    }

    private static void assertHasUuidShape(String s) {
        String[] parts = s.split("-");
        assertEquals(5, parts.length);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<BuildProperties> versionProvider(String version) {
        ObjectProvider<BuildProperties> provider = mock(ObjectProvider.class);
        if (version != null) {
            java.util.Properties entries = new java.util.Properties();
            entries.setProperty("version", version);
            when(provider.getIfAvailable()).thenReturn(new BuildProperties(entries));
        }
        return provider;
    }
}
