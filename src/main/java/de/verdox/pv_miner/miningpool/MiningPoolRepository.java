package de.verdox.pv_miner.miningpool;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MiningPoolRepository extends JpaRepository<MiningPoolEntity<?>, UUID> {
}
