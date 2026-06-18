package de.verdox.pv_miner.pvsite;

import de.verdox.pv_miner.util.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;

import java.time.LocalDate;
import java.util.Objects;

@Embeddable
public class BitcoinSale implements Comparable<BitcoinSale> {

    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    @Column(name = "amount_btc", nullable = false)
    private double amountBtc;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "fiatValueAmount")),
            @AttributeOverride(name = "currencyCode", column = @Column(name = "fiatValueCurrency"))
    })
    private Money fiatValue;

    public BitcoinSale() {
    }

    public BitcoinSale(LocalDate saleDate, double amountBtc, Money fiatValue) {
        this.saleDate = saleDate;
        this.amountBtc = amountBtc;
        this.fiatValue = fiatValue;
    }

    public LocalDate getSaleDate() {
        return saleDate;
    }

    public void setSaleDate(LocalDate saleDate) {
        this.saleDate = saleDate;
    }

    public double getAmountBtc() {
        return amountBtc;
    }

    public void setAmountBtc(double amountBtc) {
        this.amountBtc = amountBtc;
    }

    public Money getFiatValue() {
        return fiatValue;
    }

    public void setFiatValue(Money fiatValue) {
        this.fiatValue = fiatValue;
    }

    @Override
    public int compareTo(BitcoinSale o) {
        return o.saleDate.compareTo(this.saleDate); // Absteigend (Neueste zuerst)
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BitcoinSale that = (BitcoinSale) o;
        return Double.compare(that.amountBtc, amountBtc) == 0 &&
                Objects.equals(saleDate, that.saleDate) &&
                Objects.equals(fiatValue, that.fiatValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(saleDate, amountBtc, fiatValue);
    }
}