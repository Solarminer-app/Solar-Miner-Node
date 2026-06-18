package de.verdox.pv_miner.pvsite;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PVPanelsRepository extends JpaRepository<PVPanels, UUID> {
}
