package de.verdox.currencyrates.currencyrates.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Map;

@Setter
@Getter
@Entity
@Table(name = "daily_usd_rates")
public class DailyUsdRates {
    @Id
    private LocalDate date;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "currency_rate_entries",
            joinColumns = @JoinColumn(name = "rate_date")
    )
    @MapKeyColumn(name = "currency_code", length = 10)
    @Column(name = "rate", columnDefinition = "DECIMAL(24,12)")
    private Map<String, Double> rates;

    public DailyUsdRates() {
    }

    public DailyUsdRates(LocalDate date, Map<String, Double> rates) {
        this.date = date;
        this.rates = rates;
    }
}