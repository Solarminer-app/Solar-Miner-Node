package de.verdox.pv_miner.dashboard;

import de.verdox.pv_miner.dto.MiningRevenueByCoinDto;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.globalconstants.GlobalConstantsService;
import de.verdox.pv_miner.lightning.LightningWalletService;
import de.verdox.pv_miner.miningcontroller.MinerClusterService;
import de.verdox.pv_miner.miningpool.KryptexRewardService;
import de.verdox.pv_miner.miningpool.MiningCoinDailyReward;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.statistic.daily.DailyStatisticService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardFacadeServiceTest {
    @Test
    void valuesKnownXmrAmountWithCurrencyServicePriceInsteadOfStoredPoolPrice() {
        GlobalConstantsService constants = mock(GlobalConstantsService.class);
        KryptexRewardService rewards = mock(KryptexRewardService.class);
        PVSiteEntity site = mock(PVSiteEntity.class);
        UUID siteId = UUID.randomUUID();
        when(site.getId()).thenReturn(siteId);
        when(constants.getExchangeRate(any(), any())).thenReturn(0.9);
        when(constants.getCoinPriceUsd(any(), any())).thenReturn(100.0);
        MiningCoinDailyReward row = new MiningCoinDailyReward();
        row.setCoin("monero");
        row.setPayoutAddress("xmr-wallet");
        row.setDate(LocalDate.now(java.time.ZoneOffset.UTC));
        row.setAmount(new BigDecimal("2"));
        row.setPriceUsd(new BigDecimal("999"));
        row.setFetchedAt(Instant.now());
        when(rewards.snapshot(any(), any(), any())).thenReturn(new KryptexRewardService.Snapshot(List.of(row),
                Set.of(new KryptexRewardService.Route("monero", "xmr-wallet"))));
        DashboardFacadeService service = new DashboardFacadeService(mock(DailyStatisticService.class),
                mock(EntityQueryService.class), constants, mock(LightningWalletService.class),
                mock(MinerClusterService.class), rewards);

        Object summary = ReflectionTestUtils.invokeMethod(service, "calculateMiningRevenueEuro", site, 0L);
        @SuppressWarnings("unchecked")
        List<MiningRevenueByCoinDto> byCoin = (List<MiningRevenueByCoinDto>) ReflectionTestUtils.getField(summary, "byCoin");
        MiningRevenueByCoinDto xmr = byCoin.stream().filter(item -> item.symbol().equals("XMR")).findFirst().orElseThrow();

        assertEquals(180.0, xmr.euroValue(), 0.0001);
        assertEquals(180.0, (Double) ReflectionTestUtils.getField(summary, "totalEuro"), 0.0001);
    }
}
