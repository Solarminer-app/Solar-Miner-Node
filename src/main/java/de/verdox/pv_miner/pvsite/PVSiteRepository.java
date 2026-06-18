package de.verdox.pv_miner.pvsite;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PVSiteRepository extends JpaRepository<PVSiteEntity, UUID> {
}
