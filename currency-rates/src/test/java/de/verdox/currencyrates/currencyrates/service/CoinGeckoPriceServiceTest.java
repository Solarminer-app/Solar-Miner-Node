package de.verdox.currencyrates.currencyrates.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.currencyrates.currencyrates.model.DailyCoinPrices;
import de.verdox.currencyrates.currencyrates.repository.DailyCoinPricesRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoinGeckoPriceServiceTest {
    @Test
    void currentPricesIncludeAllMiningCoinsAndPearlFallback() {
        DailyCoinPricesRepository repository = mock(DailyCoinPricesRepository.class);
        when(repository.findById(any(LocalDate.class))).thenReturn(Optional.empty());
        when(repository.save(any(DailyCoinPrices.class))).thenAnswer(invocation -> invocation.getArgument(0));
        HttpTextClient http = uri -> uri.getHost().equals("pearlchain.live")
                ? "{\"price\":1.013}"
                : """
                  {"bitcoin":{"usd":85423},"monero":{"usd":548.81},
                   "ravencoin":{"usd":0.00231303},"ethereum-classic":{"usd":8.89}}
                  """;
        CoinGeckoPriceService service = new CoinGeckoPriceService(repository, new ObjectMapper(), http);

        var result = service.refreshCurrentPrices().orElseThrow();

        assertThat(result).containsKeys("btc", "xmr", "prl", "rvn", "etc");
        assertThat(result.get("prl")).isEqualTo(1.013d);
    }
}
