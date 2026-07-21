package de.verdox.pv_miner.miningcontroller;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "miner_efficiency_profiles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_miner_efficiency_profile_bucket",
                columnNames = {"miner_id", "power_target_bucket_watts"}
        )
)
@Getter
@Setter
public class MinerEfficiencyProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "miner_id", nullable = false)
    private UUID minerId;

    @Column(name = "power_target_bucket_watts", nullable = false)
    private int powerTargetBucketWatts;

    @Column(name = "learned_efficiency_j_th", nullable = false)
    private double learnedEfficiencyJTh;

    @Column(name = "sample_count", nullable = false)
    private long sampleCount;

    @Column(name = "average_temperature_celsius")
    private Double averageTemperatureCelsius;

    @Column(name = "last_observed_at", nullable = false)
    private Instant lastObservedAt;
}
