package de.verdox.pv_miner.globalconstants;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalConstantsServiceTest {
    @Test
    void resolvesFreshCoinPricesByTickerCaseInsensitively() {
        GlobalConstantsService service = new GlobalConstantsService("http://localhost");
        ReflectionTestUtils.setField(service, "currentCoinPrices", Map.of("xmr", 512.25, "etc", 18.75));
        ReflectionTestUtils.setField(service, "coinPricesFetchedAt", Instant.now());

        assertEquals(512.25, service.getCurrentCoinPriceUsd("XMR"));
        assertEquals(18.75, service.getCurrentCoinPriceUsd("etc"));
    }

    @Test
    void rejectsStaleCoinPrices() {
        GlobalConstantsService service = new GlobalConstantsService("http://localhost");
        ReflectionTestUtils.setField(service, "currentCoinPrices", Map.of("xmr", 512.25));
        ReflectionTestUtils.setField(service, "coinPricesFetchedAt", Instant.now().minusSeconds(7_201));

        assertEquals(0.0, service.getCurrentCoinPriceUsd("XMR"));
    }

    @Test
    void resolvesHistoricalCoinPriceFromCurrencyServiceCacheByDate() {
        GlobalConstantsService service = new GlobalConstantsService("http://localhost");
        LocalDate date = LocalDate.of(2026, 10, 12);
        @SuppressWarnings("unchecked")
        Map<LocalDate, Map<String, Double>> cache = (Map<LocalDate, Map<String, Double>>)
                ReflectionTestUtils.getField(service, "historicalCoinPricesCache");
        cache.put(date, Map.of("etc", 19.25));

        assertEquals(19.25, service.getCoinPriceUsd("ETC", date));
    }
}
