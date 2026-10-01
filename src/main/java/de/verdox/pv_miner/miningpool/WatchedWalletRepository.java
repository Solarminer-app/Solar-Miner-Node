package de.verdox.pv_miner.miningpool;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WatchedWalletRepository extends JpaRepository<WatchedWalletEntity, UUID> {
    List<WatchedWalletEntity> findBySiteIdOrderByLabelAsc(UUID siteId);
    boolean existsBySiteIdAndCoinAndAddress(UUID siteId, String coin, String address);
}
