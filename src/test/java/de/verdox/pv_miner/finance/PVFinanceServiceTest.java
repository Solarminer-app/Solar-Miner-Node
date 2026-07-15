package de.verdox.pv_miner.finance;

import de.verdox.pv_miner.dto.FinanceKpiDto;
import de.verdox.pv_miner.dto.MoneyDto;
import de.verdox.pv_miner.dto.PVStatisticDto;
import de.verdox.pv_miner.globalconstants.GlobalConstantsService;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.statistic.daily.DailyStatisticService;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PVFinanceServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 14);

    @Test
    void omitsBreakEvenDatesOutsideTheWebDateRange() {
        assertNull(PVFinanceService.estimateBreakEvenDate(TODAY, 25_000, 0.06, 0.0001));
    }

    @Test
    void roundsBreakEvenProjectionUpToTheNextFullDay() {
        assertEquals(TODAY.plusDays(3), PVFinanceService.estimateBreakEvenDate(TODAY, 100, 75, 10));
    }

    @Test
    void reportsTodayWhenInvestmentHasAlreadyBrokenEven() {
        assertEquals(TODAY, PVFinanceService.estimateBreakEvenDate(TODAY, 100, 100, 0));
    }

    @Test
    void calculatesTransparentPeriodAndCapitalInsights() {
        CustomCurrency eur = CustomCurrency.getInstance("EUR");
        PVFinanceService service = new PVFinanceService(
                mock(DailyStatisticService.class),
                mock(GlobalConstantsService.class)
        );
        PVSiteEntity site = mock(PVSiteEntity.class);
        when(site.getSetupDate()).thenReturn(TODAY);

        PVStatisticDto day = new PVStatisticDto(
                TODAY,
                30,
                10,
                6,
                4,
                12,
                8,
                0.0001,
                MoneyDto.of(5, eur),
                MoneyDto.of(3, eur),
                MoneyDto.of(2, eur),
                MoneyDto.of(1.2, eur),
                MoneyDto.of(15, eur),
                MoneyDto.of(12, eur),
                MoneyDto.of(4, eur),
                MoneyDto.of(2, eur),
                MoneyDto.of(0.08, eur)
        );
        FinanceKpiDto kpis = new FinanceKpiDto(
                MoneyDto.of(100, eur),
                MoneyDto.of(10, eur),
                MoneyDto.of(20, eur),
                0.0001,
                0,
                0.0001,
                34.28,
                MoneyDto.of(5, eur),
                MoneyDto.of(4, eur),
                MoneyDto.of(2, eur),
                null
        );

        var insights = service.calculateInsights(site, List.of(day), kpis, eur, ZoneId.of("Europe/Berlin"));

        assertAll(
                () -> assertEquals(7, insights.miningNetHistoric().getRawMoneyAmount(), 0.0001),
                () -> assertEquals(13, insights.operatingResult().getRawMoneyAmount(), 0.0001),
                () -> assertEquals(40, insights.gridMiningSharePercent(), 0.0001),
                () -> assertEquals(50_000, insights.costPerMinedBtc().getRawMoneyAmount(), 0.0001),
                () -> assertEquals(-69, insights.netPosition().getRawMoneyAmount(), 0.0001),
                () -> assertEquals(69, insights.remainingToBreakEven().getRawMoneyAmount(), 0.0001),
                () -> assertEquals(TODAY, insights.bestDay()),
                () -> assertEquals(1, insights.profitableMiningDays())
        );
    }
}
