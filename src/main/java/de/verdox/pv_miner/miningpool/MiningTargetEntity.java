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
@Table(name = "mining_targets")
@Getter
@Setter
public class MiningTargetEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(nullable = false, length = 32)
    private String algorithm = "SHA256";

    @Column(nullable = false, length = 16)
    private String coin = "bitcoin";

    @Column(name = "payout_coin", nullable = false, length = 16)
    private String payoutCoin = "bitcoin";

    @Column(name = "payout_address", length = 160)
    private String payoutAddress;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "stratum_url", nullable = false, length = 255)
    private String stratumUrl;

    @Column(name = "worker_prefix", nullable = false, length = 160)
    private String workerPrefix;

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private boolean enabled = true;
}
