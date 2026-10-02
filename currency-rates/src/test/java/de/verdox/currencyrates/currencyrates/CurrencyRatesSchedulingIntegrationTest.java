package de.verdox.currencyrates.currencyrates;

import de.verdox.currencyrates.currencyrates.service.BitcoinMiningDataFetcher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/** Verifies a scheduled method in the actual Spring Boot application context. */
@SpringBootTest(
        classes = CurrencyRatesMicroService.class,
        properties = {
                "currency-rates.refresh-on-startup=false",
                "currency-rates.refresh.fixed-rate-ms=10",
                "spring.datasource.url=jdbc:h2:mem:currency-rates-scheduling;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
class CurrencyRatesSchedulingIntegrationTest {

    @MockitoBean
    private BitcoinMiningDataFetcher bitcoinMiningDataFetcher;

    @Test
    void scheduledFetchInvokesTheBitcoinProvider() throws Exception {
        verify(bitcoinMiningDataFetcher, timeout(1_000).atLeastOnce()).query();
    }
}
