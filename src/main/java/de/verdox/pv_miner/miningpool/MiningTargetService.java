package de.verdox.pv_miner.miningpool;

import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.MinerRepository;
import de.verdox.pv_miner.miner.MiningOS;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class MiningTargetService {
    private static final Logger LOGGER = Logger.getLogger(MiningTargetService.class.getName());
    private final MiningTargetRepository targets;
    private final PVSiteRepository sites;
    private final MinerRepository miners;
    private final MinerApiClient minerApi;
    private final EntityQueryService queries;

    public MiningTargetService(MiningTargetRepository targets, PVSiteRepository sites, MinerRepository miners,
                               MinerApiClient minerApi, EntityQueryService queries) {
        this.targets = targets;
        this.sites = sites;
        this.miners = miners;
        this.minerApi = minerApi;
        this.queries = queries;
    }

    /** Generic pool-target writes are limited to single-algorithm ASICs. Agents use coin-specific commands. */
    public boolean isEligible(MinerEntity<?> miner) {
        return miner.getOS() == MiningOS.BRAIINS || miner.getOS() == MiningOS.ANTMINER_STOCK_OS;
    }

    public List<MiningTargetEntity> list(UUID siteId) {
        return targets.findBySiteIdOrderByPriorityAsc(siteId);
    }

    public void apply(UUID siteId, boolean force) {
        for (MiningCoin coin : MiningCoin.values()) apply(siteId, coin, force);
    }

    public void apply(UUID siteId, MiningCoin coin, boolean force) {
        if (!coin.supportsNodeTargetControl()) return;
        var site = sites.findById(siteId).orElse(null);
        if (site == null) return;
        List<MiningTargetEntity> candidates = list(siteId).stream()
                .filter(target -> target.isEnabled() && coin.key().equals(target.getCoin())
                        && coin.algorithm().equals(target.getAlgorithm()))
                .sorted(Comparator.comparingInt(MiningTargetEntity::getPriority))
                .toList();
        if (candidates.isEmpty()) return;

        for (MinerEntity<?> miner : site.getMiners()) {
            if (coin != MiningCoin.BITCOIN) {
                if (miner.getOS() == MiningOS.AGENT) applyAgent(miner, coin, candidates, site.getReferralCode(), force);
                continue;
            }
            if (!isEligible(miner)) continue;
            // Keep an already configured fallback until the user explicitly reapplies the policy.
            if (!force && candidates.stream().anyMatch(target -> target.getStratumUrl().equals(miner.getCurrentMiningPoolTarget()))) continue;
            MinerStats stats = queries.getLastResult(miner, MinerStats.DEFAULT);
            if (stats == null) stats = MinerStats.DEFAULT;
            MinerStats.MinerIdentity identity = stats.minerIdentity();
            if (identity == null || identity.minerModel() == null || identity.minerModel().isBlank()) {
                identity = new MinerStats.MinerIdentity(miner.getId().toString(), "", "miner_" + miner.getId().toString().substring(0, 8));
            }
            for (MiningTargetEntity target : candidates) {
                try {
                    if (minerApi.setMiningPoolTarget(miner.getOS(), miner.getDetails(), target.getStratumUrl(),
                            target.getWorkerPrefix(), identity, site.getReferralCode())) {
                        miner.setCurrentMiningPoolTarget(target.getStratumUrl());
                        miners.save(miner);
                        break;
                    }
                } catch (Exception exception) {
                    LOGGER.log(Level.WARNING, "Could not apply mining target to miner " + miner.getId(), exception);
                }
            }
        }
    }

    private void applyAgent(MinerEntity<?> miner, MiningCoin coin, List<MiningTargetEntity> candidates,
                            String referralCode, boolean force) {
        var current = minerApi.getAgentCoinConfigurations(miner.getDetails());
        if (current == null) return;
        MinerApiClient.CoinRoute route = coin == MiningCoin.MONERO ? current.monero() : current.pearl();
        if (!force && candidates.stream().anyMatch(target -> matches(route, target, miner.getId()))) return;
        for (MiningTargetEntity target : candidates) {
            try {
                String worker = workerName(target, miner.getId());
                String devices = current.pearl() == null || current.pearl().devices() == null
                        ? "all" : current.pearl().devices();
                boolean accepted = coin == MiningCoin.MONERO
                        ? minerApi.configureMonero(miner.getDetails(), target.getStratumUrl(), target.getPayoutAddress(), worker, referralCode)
                        : minerApi.configurePearl(miner.getDetails(), target.getStratumUrl(), target.getPayoutAddress(), worker, devices, referralCode);
                if (accepted) return;
            } catch (Exception exception) {
                LOGGER.log(Level.WARNING, "Could not apply " + coin.key() + " target to agent " + miner.getId(), exception);
            }
        }
    }

    private static String workerName(MiningTargetEntity target, UUID minerId) {
        String suffix = "-" + minerId.toString().substring(0, 8);
        String prefix = target.getWorkerPrefix();
        return prefix.substring(0, Math.min(prefix.length(), 32 - suffix.length())) + suffix;
    }

    private static boolean matches(MinerApiClient.CoinRoute route, MiningTargetEntity target, UUID minerId) {
        return route != null && target.getStratumUrl().equals(route.poolUrl())
                && target.getPayoutAddress().equals(route.wallet())
                && workerName(target, minerId).equals(route.worker());
    }


    public List<Assignment> assignments(UUID siteId) {
        var site = sites.findById(siteId).orElse(null);
        if (site == null) return List.of();
        List<MiningTargetEntity> configured = list(siteId);
        return site.getMiners().stream().flatMap(miner -> {
            if (isEligible(miner)) {
                var btc = configured.stream().filter(target -> target.isEnabled() && "bitcoin".equals(target.getCoin())
                        && target.getStratumUrl().equals(miner.getCurrentMiningPoolTarget())).findFirst().orElse(null);
                return java.util.stream.Stream.of(new Assignment(miner.getId(), "bitcoin", "SHA256",
                        btc == null ? "PENDING" : "ASSIGNED", btc == null ? null : btc.getId(),
                        btc == null ? null : btc.getName(), miner.getCurrentMiningPoolTarget()));
            }
            if (miner.getOS() != MiningOS.AGENT) return java.util.stream.Stream.empty();
            var current = minerApi.getAgentCoinConfigurations(miner.getDetails());
            return Arrays.stream(new MiningCoin[]{MiningCoin.MONERO, MiningCoin.PEARL}).map(coin -> {
                var route = current == null ? null : coin == MiningCoin.MONERO ? current.monero() : current.pearl();
                var target = configured.stream().filter(entry -> entry.isEnabled() && coin.key().equals(entry.getCoin())
                        && matches(route, entry, miner.getId())).findFirst().orElse(null);
                boolean hasPolicy = configured.stream().anyMatch(entry -> entry.isEnabled() && coin.key().equals(entry.getCoin()));
                String status = current == null ? "UNAVAILABLE" : target != null ? "ASSIGNED"
                        : route != null ? hasPolicy ? "DIFFERENT" : "MANUAL" : hasPolicy ? "PENDING" : "UNCONFIGURED";
                return new Assignment(miner.getId(), coin.key(), coin.algorithm(), status,
                        target == null ? null : target.getId(), target == null ? null : target.getName(),
                        route == null ? null : route.poolUrl());
            });
        }).toList();
    }

    public record Assignment(UUID minerId, String coin, String algorithm, String status, UUID targetId,
                             String targetName, String currentPool) { }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void reconcile() {
        for (UUID siteId : targets.findAll().stream().map(MiningTargetEntity::getSiteId).distinct().toList()) {
            try {
                apply(siteId, false);
            } catch (Exception exception) {
                LOGGER.log(Level.WARNING, "Could not reconcile mining targets for site " + siteId, exception);
            }
        }
    }
}
