package de.verdox.currencyrates.currencyrates.repository;

import de.verdox.currencyrates.currencyrates.model.DailyUsdRates;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DailyUsdRatesRepository extends JpaRepository<DailyUsdRates, LocalDate> {
    @Query("SELECT d FROM DailyUsdRates d WHERE d.date >= :startDate ORDER BY d.date ASC")

    List<DailyUsdRates> findRecentRates(@Param("startDate") LocalDate startDate);
}
