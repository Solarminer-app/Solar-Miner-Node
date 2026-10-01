package de.verdox.pv_miner.miningpool;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MiningTargetRepository extends JpaRepository<MiningTargetEntity, UUID> {
    List<MiningTargetEntity> findBySiteIdOrderByPriorityAsc(UUID siteId);
}
