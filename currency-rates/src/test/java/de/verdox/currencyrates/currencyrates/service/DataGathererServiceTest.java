package de.verdox.currencyrates.currencyrates.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.currencyrates.currencyrates.model.DailyUsdRates;
import de.verdox.currencyrates.currencyrates.repository.BitcoinNetworkStatsRepository;
import de.verdox.currencyrates.currencyrates.repository.DailyUsdRatesRepository;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DataGathererServiceTest {

    @Test
    void historicalRateRequestUsesTheRequestedDateAsProviderVersion() {
        URI uri = DataGathererService.exchangeRatesUri(LocalDate.of(2025, 2, 3));

        assertEquals(
                "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@2025-02-03/v1/currencies/usd.json",
                uri.toString()
        );
    }

    @Test
    void currentRateRequestUsesLatestProviderVersion() {
        assertEquals(
                "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json",
                DataGathererService.exchangeRatesUri(null).toString()
        );
    }

    @Test
    void historicalFetchPersistsTheRequestedDateFromAFakeUpstream() {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        DailyUsdRatesRepository ratesRepository = mock(DailyUsdRatesRepository.class);
        when(ratesRepository.save(any(DailyUsdRates.class))).thenAnswer(invocation -> invocation.getArgument(0));
        DataGathererService service = service(
                ratesRepository,
                mock(BitcoinMiningDataFetcher.class),
                uri -> {
                    requestedUri.set(uri);
                    return "{\"usd\":{\"eur\":0.9}}";
                }
        );

        DailyUsdRates saved = service.fetchAndSaveForDate(LocalDate.of(2025, 2, 3));

        assertEquals(LocalDate.of(2025, 2, 3), saved.getDate());
        assertEquals(DataGathererService.exchangeRatesUri(LocalDate.of(2025, 2, 3)), requestedUri.get());
    }

    @Test
    void failedRefreshClearsThePriorBitcoinSnapshotBeforePersistence() throws Exception {
        BitcoinMiningDataFetcher bitcoinFetcher = mock(BitcoinMiningDataFetcher.class);
        when(bitcoinFetcher.query())
                .thenReturn(new BitcoinMiningDataFetcher.BitcoinMiningSnapshot(1, 2, 3, 4, 5))
                .thenThrow(new IOException("upstream unavailable"));
        BitcoinNetworkStatsRepository bitcoinRepository = mock(BitcoinNetworkStatsRepository.class);
        DailyUsdRatesRepository ratesRepository = mock(DailyUsdRatesRepository.class);
        DataGathererService service = new DataGathererService(
                bitcoinRepository,
                ratesRepository,
                new ObjectMapper(),
                mock(CoinGeckoPriceService.class),
                bitcoinFetcher,
                uri -> "{\"usd\":{\"eur\":0.9}}",
                true
        );

        service.scheduledFetch();
        service.scheduledFetch();

        assertFalse(service.saveDailyStatsToDatabase());
        verifyNoInteractions(bitcoinRepository, ratesRepository);
    }

    private DataGathererService service(DailyUsdRatesRepository ratesRepository,
                                        BitcoinMiningDataFetcher bitcoinFetcher,
                                        HttpTextClient httpClient) {
        return new DataGathererService(
                mock(BitcoinNetworkStatsRepository.class),
                ratesRepository,
                new ObjectMapper(),
                mock(CoinGeckoPriceService.class),
                bitcoinFetcher,
                httpClient,
                true
        );
    }
}
