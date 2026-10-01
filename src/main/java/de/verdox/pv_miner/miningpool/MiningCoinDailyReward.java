package de.verdox.pv_miner.miningpool;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "mining_coin_daily_rewards", uniqueConstraints = @UniqueConstraint(columnNames =
        {"site_id", "coin", "payout_address", "reward_date"}))
@Getter
@Setter
public class MiningCoinDailyReward {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "site_id", nullable = false)
    private UUID siteId;
    @Column(nullable = false, length = 16)
    private String coin;
    @Column(name = "payout_address", nullable = false, length = 160)
    private String payoutAddress;
    @Column(name = "reward_date", nullable = false)
    private LocalDate date;
    @Column(nullable = false, precision = 30, scale = 18)
    private BigDecimal amount;
    @Column(name = "price_usd", precision = 30, scale = 12)
    private BigDecimal priceUsd;
    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
}
