package de.verdox.pv_miner.dashboard;

import de.verdox.pv_miner.miningpool.KryptexRewardService;
import de.verdox.pv_miner.miningpool.MiningCoinDailyReward;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningEarningsServiceTest {
    @Test
    void aggregatesSevenCompletePoolDaysWithoutCallingBalanceOrPayoutEarnings() {
        LocalDate from = LocalDate.of(2026, 10, 6);
        LocalDate to = from.plusDays(6);
        List<MiningCoinDailyReward> rows = new ArrayList<>();
        for (int day = 0; day < 7; day++) rows.add(reward("pearl", "prl-wallet", from.plusDays(day), "2", "3"));
        var snapshot = new KryptexRewardService.Snapshot(rows,
                Set.of(new KryptexRewardService.Route("pearl", "prl-wallet")));

        var actual = MiningEarningsService.actualPoolCredits(snapshot, from, to).getFirst();

        assertEquals(new BigDecimal("14"), actual.amount());
        assertEquals(0, new BigDecimal("2").compareTo(actual.averagePerDay()));
        assertEquals(new BigDecimal("42"), actual.usdValue());
        assertEquals(7, actual.dataPoints());
        assertTrue(actual.complete());
    }

    @Test
    void marksHistoryIncompleteWhenOneRouteDayIsMissing() {
        LocalDate from = LocalDate.of(2026, 10, 6);
        LocalDate to = from.plusDays(6);
        List<MiningCoinDailyReward> rows = new ArrayList<>();
        for (int day = 0; day < 6; day++) rows.add(reward("monero", "xmr-wallet", from.plusDays(day), "1", "500"));

        var actual = MiningEarningsService.actualPoolCredits(new KryptexRewardService.Snapshot(rows,
                Set.of(new KryptexRewardService.Route("monero", "xmr-wallet"))), from, to).getFirst();

        assertEquals(6, actual.dataPoints());
        assertFalse(actual.complete());
    }

    private MiningCoinDailyReward reward(String coin, String address, LocalDate date, String amount, String price) {
        MiningCoinDailyReward row = new MiningCoinDailyReward();
        row.setCoin(coin);
        row.setPayoutAddress(address);
        row.setDate(date);
        row.setAmount(new BigDecimal(amount));
        row.setPriceUsd(new BigDecimal(price));
        row.setFetchedAt(Instant.parse("2026-10-13T10:00:00Z"));
        return row;
    }
}
