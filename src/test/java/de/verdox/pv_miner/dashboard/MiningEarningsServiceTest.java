package de.verdox.pv_miner.dashboard;

import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.globalconstants.GlobalConstantsService;
import de.verdox.pv_miner.miningpool.KryptexRewardService;
import de.verdox.pv_miner.miningpool.MiningCoinDailyReward;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.util.Money;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class MiningEarningsServiceTest {
    @Test
    void aggregatesSevenCompletePoolDaysWithoutCallingBalanceOrPayoutEarnings() {
        LocalDate from = LocalDate.of(2026, 10, 6);
        LocalDate to = from.plusDays(6);
        List<MiningCoinDailyReward> rows = new ArrayList<>();
        for (int day = 0; day < 7; day++) rows.add(reward("pearl", "prl-wallet", from.plusDays(day), "2"));
        var snapshot = new KryptexRewardService.Snapshot(rows,
                Set.of(new KryptexRewardService.Route("pearl", "prl-wallet")));

        var actual = MiningEarningsService.actualPoolCredits(snapshot, from, to, (coin, date) -> 3.0).getFirst();

        assertEquals(new BigDecimal("14"), actual.amount());
        assertEquals(0, new BigDecimal("2").compareTo(actual.averagePerDay()));
        assertEquals(0, new BigDecimal("42").compareTo(actual.usdValue()));
        assertEquals(7, actual.dataPoints());
        assertTrue(actual.complete());
    }

    @Test
    void marksHistoryIncompleteWhenOneRouteDayIsMissing() {
        LocalDate from = LocalDate.of(2026, 10, 6);
        LocalDate to = from.plusDays(6);
        List<MiningCoinDailyReward> rows = new ArrayList<>();
        for (int day = 0; day < 6; day++) rows.add(reward("monero", "xmr-wallet", from.plusDays(day), "1"));

        var actual = MiningEarningsService.actualPoolCredits(new KryptexRewardService.Snapshot(rows,
                Set.of(new KryptexRewardService.Route("monero", "xmr-wallet"))), from, to,
                (coin, date) -> 500.0).getFirst();

        assertEquals(6, actual.dataPoints());
        assertFalse(actual.complete());
    }

    @Test
    void presentsForecastInRequestedCurrency() {
        GlobalConstantsService constants = mock(GlobalConstantsService.class);
        PVSiteEntity site = mock(PVSiteEntity.class);
        UUID siteId = UUID.randomUUID();
        when(site.getId()).thenReturn(siteId);
        when(site.getCurrentElectricityPrice()).thenReturn(new Money(0.30, CustomCurrency.getInstance("EUR")));
        when(constants.getExchangeRate(any(CustomCurrency.class), any(CustomCurrency.class))).thenReturn(0.8);
        when(constants.convert(any(Money.class), any(CustomCurrency.class)))
                .thenReturn(new Money(0.24, CustomCurrency.getInstance("CHF")));
        MiningEarningsService service = new MiningEarningsService(mock(EntityQueryService.class), constants,
                List.of(), mock(KryptexRewardService.class));
        var estimate = new MiningEarningsService.CoinEstimate("monero", "XMR", true, "", 1, 24,
                1_000, 1, Instant.now().toString(), List.of(), List.of(), 1, 24, 0.0, "NET_KNOWN_FEES");
        var base = new MiningEarningsService.Snapshot(List.of(estimate), List.of(), true, 90.0, 30.0,
                21.6, 24.0, 0.9, Instant.now(), List.of());
        @SuppressWarnings("unchecked")
        var cache = (java.util.Map<UUID, MiningEarningsService.Snapshot>) ReflectionTestUtils.getField(service, "cache");
        cache.put(siteId, base);

        var result = service.currencySnapshot(site, CustomCurrency.getInstance("CHF"), Locale.GERMAN);

        assertEquals("CHF", result.currencyCode());
        assertEquals(19.2, result.currencyPerDay(), 0.0001);
        assertEquals(0.8, result.currencyPerKwh(), 0.0001);
        assertEquals(0.24, result.gridCurrencyPerKwh(), 0.0001);
    }

    private MiningCoinDailyReward reward(String coin, String address, LocalDate date, String amount) {
        MiningCoinDailyReward row = new MiningCoinDailyReward();
        row.setCoin(coin);
        row.setPayoutAddress(address);
        row.setDate(date);
        row.setAmount(new BigDecimal(amount));
        row.setFetchedAt(Instant.parse("2026-10-13T10:00:00Z"));
        return row;
    }
}
