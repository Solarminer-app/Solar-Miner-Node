package de.verdox.pv_miner.pvsite.smartmeter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SmartMeterRepository extends JpaRepository<SmartMeterEntity, UUID> {
}
