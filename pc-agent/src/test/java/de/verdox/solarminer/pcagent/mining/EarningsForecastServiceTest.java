package de.verdox.solarminer.pcagent.mining;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EarningsForecastServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Instant now = Instant.parse("2026-09-29T18:00:00Z");

    @Test
    void parsesMoneroExplorerAndTickerUnits() throws Exception {
        var network = mapper.readTree("""
                {"difficulty":"773332665792","hash_rate":6444438881,"target":120}
                """);
        var block = mapper.readTree("""
                {"txs":[{"coinbase":true,"xmr_outputs":606768460000}]}
                """);
        var price = mapper.readTree("""
                {"XXMRZUSD":{"c":["542.46","0.123"]}}
                """);

        var result = EarningsForecastService.parseMonero(network, block, price, now);

        assertThat(result.networkHashrateHps()).isEqualTo(6_444_438_881d);
        assertThat(result.blockReward()).isEqualTo(0.60676846d);
        assertThat(result.priceUsd()).isEqualTo(542.46d);
        assertThat(result.targetBlockSeconds()).isEqualTo(120d);
    }

    @Test
    void parsesPearlExplorerStatsAndStalePriceFlag() throws Exception {
        var stats = mapper.readTree("""
                {"difficulty":32912109.754,"networkHashPs":3.781356619201569E19,
                 "targetBlockSecs":194,"blockRewardPearl":2295.8255488576}
                """);
        var price = mapper.readTree("""
                {"price":1.32,"stale":true}
                """);

        var result = EarningsForecastService.parsePearl(stats, price, now);

        assertThat(result.networkHashrateHps()).isEqualTo(3.781356619201569E19);
        assertThat(result.blockReward()).isEqualTo(2295.8255488576d);
        assertThat(result.priceUsd()).isEqualTo(1.32d);
        assertThat(result.stale()).isTrue();
        assertThat(EarningsForecastService.estimateDailyCoins(60_000_000_000_000d, result))
                .isCloseTo(1.623, org.assertj.core.data.Offset.offset(0.001));
    }
}
