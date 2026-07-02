package de.verdox.pv_miner.pvsite;

import de.verdox.pv_miner.util.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

import java.time.LocalDate;
import java.util.Objects;

@Embeddable
public class HistoricalPrice implements Comparable<HistoricalPrice> {

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Embedded
    private Money price;

    public HistoricalPrice() {
    }

    public HistoricalPrice(LocalDate validFrom, Money price) {
        this.validFrom = validFrom;
        this.price = price;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public Money getPrice() {
        return price;
    }

    public void setPrice(Money price) {
        this.price = price;
    }

    @Override
    public int compareTo(HistoricalPrice o) {
        return o.validFrom.compareTo(this.validFrom);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HistoricalPrice that = (HistoricalPrice) o;
        return Objects.equals(validFrom, that.validFrom) && Objects.equals(price, that.price);
    }

    @Override
    public int hashCode() {
        return Objects.hash(validFrom, price);
    }
}