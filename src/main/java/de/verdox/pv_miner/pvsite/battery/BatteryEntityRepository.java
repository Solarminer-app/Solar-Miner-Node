package de.verdox.pv_miner.pvsite.battery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BatteryEntityRepository extends JpaRepository<BatteryEntity, UUID> {
}
