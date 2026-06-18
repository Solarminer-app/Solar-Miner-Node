package de.verdox.currencyrates.currencyrates.repository;

import de.verdox.currencyrates.currencyrates.model.BitcoinNetworkStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;

@Repository
public interface BitcoinNetworkStatsRepository extends JpaRepository<BitcoinNetworkStats, LocalDate> {
}
