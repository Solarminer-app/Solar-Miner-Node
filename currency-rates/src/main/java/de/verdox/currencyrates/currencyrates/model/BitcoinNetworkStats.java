package de.verdox.currencyrates.currencyrates.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "bitcoin_network_stats")
public class BitcoinNetworkStats {

    @Id
    @Getter
    private LocalDate date;

    @Column(name = "mining_difficulty", nullable = false)
    @Getter
    @Setter
    private long miningDifficulty;

    @Column(name = "hash_rate_ths", nullable = false)
    @Getter
    @Setter
    private double hashRateInThs;

    @Column(name = "price_in_dollar", nullable = false)
    @Getter
    @Setter
    private double priceInDollar;

    @Column(name = "block_subsidy", nullable = false)
    @Getter
    @Setter
    private int blockSubsidy;

    @Column(name = "average_tx_fee_24h", nullable = false)
    @Getter
    @Setter
    private int averageTxPrice24h;

    public BitcoinNetworkStats() {
    }

    public BitcoinNetworkStats(LocalDate date) {
        this.date = date;
    }
}