package de.verdox.pv_miner.pvsite.inverter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InverterEntityRepository extends JpaRepository<InverterEntity, UUID> {
}
