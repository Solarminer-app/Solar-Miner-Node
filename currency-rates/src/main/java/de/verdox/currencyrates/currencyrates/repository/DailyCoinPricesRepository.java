package de.verdox.currencyrates.currencyrates.repository;
import de.verdox.currencyrates.currencyrates.model.DailyCoinPrices;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
public interface DailyCoinPricesRepository extends JpaRepository<DailyCoinPrices, LocalDate> { }
