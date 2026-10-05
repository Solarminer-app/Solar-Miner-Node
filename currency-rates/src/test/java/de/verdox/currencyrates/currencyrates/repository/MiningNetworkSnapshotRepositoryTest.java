package de.verdox.currencyrates.currencyrates.repository;

import de.verdox.currencyrates.currencyrates.model.MiningNetworkSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class MiningNetworkSnapshotRepositoryTest {
    @Autowired
    private MiningNetworkSnapshotRepository repository;

    @Test
    void persistsCoinKeyedSnapshotAndTimestamp() {
        Instant collectedAt = Instant.parse("2026-10-04T12:00:00Z");
        repository.saveAndFlush(new MiningNetworkSnapshot("monero", "XMR", "RandomX",
                6_651_124_960d, 798_134_995_218d, 120d, 0.60676846d, 542.46d,
                collectedAt, "xmrchain.net", "CoinGecko", false));

        var loaded = repository.findById("monero").orElseThrow();
        assertThat(loaded.getCollectedAt()).isEqualTo(collectedAt);
        assertThat(loaded.getNetworkHashrateHps()).isEqualTo(6_651_124_960d);
        assertThat(loaded.getPriceUsd()).isEqualTo(542.46d);
    }
}
