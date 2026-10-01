package de.verdox.pv_miner.miningpool;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "watched_wallets")
@Getter
@Setter
public class WatchedWalletEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "site_id", nullable = false)
    private UUID siteId;
    @Column(nullable = false, length = 120)
    private String label;
    @Column(nullable = false, length = 16)
    private String coin = "bitcoin";
    @Column(nullable = false, length = 160)
    private String address;
}
