package de.verdox.currencyrates.currencyrates.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.Map;

/** USD closing/current snapshots for crypto assets supported by SolarMiner. */
@Entity
@Table(name = "daily_coin_prices")
@Getter @Setter
public class DailyCoinPrices {
    @Id private LocalDate date;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "coin_price_entries", joinColumns = @JoinColumn(name = "price_date"))
    @MapKeyColumn(name = "coin_code", length = 16)
    @Column(name = "usd_price", columnDefinition = "DECIMAL(30,12)")
    private Map<String, Double> prices;
    public DailyCoinPrices() { }
    public DailyCoinPrices(LocalDate date, Map<String, Double> prices) { this.date = date; this.prices = prices; }
}
