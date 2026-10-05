package de.verdox.currencyrates.currencyrates.repository;

import de.verdox.currencyrates.currencyrates.model.MiningNetworkSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MiningNetworkSnapshotRepository extends JpaRepository<MiningNetworkSnapshot, String> {
}
