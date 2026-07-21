package de.verdox.pv_miner.miningcontroller;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MinerEfficiencyProfileRepository extends JpaRepository<MinerEfficiencyProfile, Long> {
    List<MinerEfficiencyProfile> findByMinerIdOrderByPowerTargetBucketWatts(UUID minerId);

    Optional<MinerEfficiencyProfile> findByMinerIdAndPowerTargetBucketWatts(UUID minerId, int powerTargetBucketWatts);
}
