package de.verdox.pv_miner.miningpool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KryptexRewardServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void groupsKryptexRewardPointsByUtcDayAndKeepsFractionalAmounts() throws Exception {
        Map<LocalDate, BigDecimal> rewards = KryptexRewardService.parseRewards(mapper.readTree("""
                [{"timestamp":1788134400000,"reward":0.000000616763836464},
                 {"timestamp":1788138000000,"reward":0.000000100000000000}]
                """));
        assertEquals(new BigDecimal("0.000000716763836464"), rewards.get(LocalDate.of(2026, 8, 31)));
    }

    @Test
    void usesTheLatestPricePointForEachUtcDay() throws Exception {
        Map<LocalDate, BigDecimal> prices = KryptexRewardService.parsePrices(mapper.readTree("""
                [{"timestamp":1788138000000,"price":501.23}, {"timestamp":1788134400000,"price":500.12}]
                """));
        assertEquals(new BigDecimal("501.23"), prices.get(LocalDate.of(2026, 8, 31)));
    }

    @Test
    void acceptsEveryKryptexPcAgentCoinAndRejectsLookalikeHosts() {
        List<String> supported = List.of("monero", "pearl", "ravencoin", "ethereumclassic", "quantus");
        List<MiningTargetEntity> targets = new java.util.ArrayList<>();
        for (String coin : supported) {
            MiningTargetEntity target = new MiningTargetEntity();
            target.setCoin(coin);
            target.setPayoutAddress("wallet-" + coin);
            target.setStratumUrl("stratum+tcp://" + MiningCoin.from(coin).kryptexTicker() + ".kryptex.network:7000");
            targets.add(target);
        }
        MiningTargetEntity impostor = new MiningTargetEntity();
        impostor.setCoin("ravencoin");
        impostor.setPayoutAddress("wrong");
        impostor.setStratumUrl("stratum+tcp://rvn.kryptex.network.example:7000");
        targets.add(impostor);

        var routes = KryptexRewardService.supportedRoutes(targets);
        assertEquals(5, routes.size());
        for (String coin : supported)
            org.junit.jupiter.api.Assertions.assertTrue(routes.contains(
                    new KryptexRewardService.Route(coin, "wallet-" + coin)));
    }
}
