package de.verdox.pv_miner.miningpool;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MiningCoinDailyRewardRepository extends JpaRepository<MiningCoinDailyReward, UUID> {
    Optional<MiningCoinDailyReward> findBySiteIdAndCoinAndPayoutAddressAndDate(UUID siteId, String coin,
                                                                                 String payoutAddress, LocalDate date);
    List<MiningCoinDailyReward> findBySiteIdAndDateBetween(UUID siteId, LocalDate from, LocalDate to);
}
