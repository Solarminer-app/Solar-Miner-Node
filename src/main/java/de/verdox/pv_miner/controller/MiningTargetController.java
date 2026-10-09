package de.verdox.pv_miner.controller;

import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.miningpool.MiningTargetEntity;
import de.verdox.pv_miner.miningpool.MiningCoin;
import de.verdox.pv_miner.miningpool.MiningTargetRepository;
import de.verdox.pv_miner.miningpool.MiningTargetService;
import de.verdox.pv_miner.miningpool.TargetPoolBalanceService;
import de.verdox.pv_miner.miningpool.KryptexRewardService;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import de.verdox.pv_miner_extensions.pools.braiins.BraiinsPoolEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Arrays;
import java.util.UUID;

@RestController
@RequestMapping("/api/pv-site/{siteId}/mining")
@CrossOrigin(origins = "http://localhost:3000")
public class MiningTargetController {
    private final PVSiteRepository sites;
    private final MiningTargetRepository targets;
    private final MiningTargetService service;
    private final EntityQueryService queries;
    private final TargetPoolBalanceService poolBalances;
    private final KryptexRewardService rewards;

    public MiningTargetController(PVSiteRepository sites, MiningTargetRepository targets,
                                  MiningTargetService service, EntityQueryService queries,
                                  TargetPoolBalanceService poolBalances, KryptexRewardService rewards) {
        this.sites = sites;
        this.targets = targets;
        this.service = service;
        this.queries = queries;
        this.poolBalances = poolBalances;
        this.rewards = rewards;
    }

    @GetMapping("/targets")
    public List<TargetDto> getTargets(@PathVariable UUID siteId) {
        requireSite(siteId);
        return service.list(siteId).stream().map(TargetDto::from).toList();
    }

    @GetMapping("/targets/assignments")
    public List<MiningTargetService.Assignment> assignments(@PathVariable UUID siteId) {
        requireSite(siteId);
        return service.assignments(siteId);
    }

    @GetMapping("/coins")
    public List<CoinDto> coins(@PathVariable UUID siteId) {
        requireSite(siteId);
        return Arrays.stream(MiningCoin.values()).map(coin -> new CoinDto(coin.key(), coin.symbol(),
                coin.algorithm(), coin.automaticAssignment(), coin.supportsNodeTargetControl())).toList();
    }

    @PostMapping("/targets")
    public TargetDto saveTarget(@PathVariable UUID siteId, @RequestBody TargetRequest request) {
        requireSite(siteId);
        if (request == null || request.name() == null || request.name().isBlank() || request.name().length() > 120
                || request.stratumUrl() == null || request.stratumUrl().length() > 255
                || request.workerPrefix() == null || request.workerPrefix().isBlank() || request.workerPrefix().length() > 160
                || request.priority() < 1 || request.priority() > 99) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid mining target");
        }
        MiningCoin coin;
        try { coin = MiningCoin.from(request.coin() == null ? "bitcoin" : request.coin()); }
        catch (IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage()); }
        if (!coin.supportsNodeTargetControl()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Node target control is not available for this coin; configure it in the PC-Agent");
        }
        if (!coin.algorithm().equals(request.algorithm()) || !coin.key().equals(request.payoutCoin() == null ? coin.key() : request.payoutCoin())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mining coin, algorithm and payout coin do not match");
        }
        if (!request.workerPrefix().matches("[A-Za-z0-9._-]+") || request.workerPrefix().contains(";")
                || (coin != MiningCoin.BITCOIN && !request.workerPrefix().matches("[A-Za-z0-9_-]{1,32}"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid worker prefix");
        }
        String payoutAddress = request.payoutAddress() == null ? null : request.payoutAddress().trim();
        if (coin != MiningCoin.BITCOIN && !coin.validAddress(payoutAddress)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid payout address is required for this coin");
        }
        if (coin == MiningCoin.BITCOIN && payoutAddress != null && !payoutAddress.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitcoin ASIC targets use a pool account worker, not a payout address");
        }
        try {
            URI uri = URI.create(request.stratumUrl().trim());
            if (!("stratum+tcp".equals(uri.getScheme()) || (coin != MiningCoin.BITCOIN && "stratum+ssl".equals(uri.getScheme()))) || uri.getHost() == null
                    || uri.getPort() < 1 || uri.getPort() > 65535 || uri.getUserInfo() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty())
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Invalid Stratum URL");
            }
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Stratum URL");
        }
        if (service.list(siteId).stream().anyMatch(existing -> existing.getCoin().equals(coin.key()) && existing.getPriority() == request.priority()
                && !existing.getId().equals(request.id()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Priority is already used");
        }
        MiningTargetEntity target = request.id() == null ? new MiningTargetEntity() : targets.findById(request.id())
                .filter(existing -> siteId.equals(existing.getSiteId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mining target not found"));
        boolean wasBitcoin = request.id() != null && "bitcoin".equals(target.getCoin());
        target.setSiteId(siteId);
        target.setAlgorithm(request.algorithm());
        target.setCoin(coin.key());
        target.setPayoutCoin(coin.key());
        target.setPayoutAddress(payoutAddress);
        target.setName(request.name().trim());
        target.setStratumUrl(request.stratumUrl().trim());
        String prefix = request.workerPrefix().trim();
        target.setWorkerPrefix(coin == MiningCoin.BITCOIN && !prefix.endsWith(".") ? prefix + "." : prefix);
        target.setPriority(request.priority());
        target.setEnabled(request.enabled());
        TargetDto saved = TargetDto.from(targets.save(target));
        rewards.invalidate(siteId);
        service.apply(siteId, coin, true);
        if (wasBitcoin && coin != MiningCoin.BITCOIN) service.apply(siteId, MiningCoin.BITCOIN, true);
        return saved;
    }

    @DeleteMapping("/targets/{targetId}")
    public ResponseEntity<Void> deleteTarget(@PathVariable UUID siteId, @PathVariable UUID targetId) {
        requireSite(siteId);
        MiningTargetEntity target = targets.findById(targetId).filter(entry -> siteId.equals(entry.getSiteId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mining target not found"));
        targets.delete(target);
        rewards.invalidate(siteId);
        service.apply(siteId, MiningCoin.from(target.getCoin()), true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/targets/apply")
    public ResponseEntity<Void> applyTargets(@PathVariable UUID siteId,
                                             @RequestParam(defaultValue = "bitcoin") String coin) {
        requireSite(siteId);
        try { service.apply(siteId, MiningCoin.from(coin), true); }
        catch (IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage()); }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/pools/overview")
    public List<PoolOverviewDto> poolOverview(@PathVariable UUID siteId) {
        var site = requireSite(siteId);
        List<PoolOverviewDto> accounts = site.getConnectedMiningPools().stream().map(pool -> {
            Double balance = null;
            Integer workerCount = null;
            Instant balanceUpdatedAt = null;
            String suggestedWorkerPrefix = pool.getUserNameOfAccount();
            if (pool instanceof BraiinsPoolEntity braiins) {
                var data = queries.getLastResult(braiins, null);
                if (data != null) {
                    balance = data.currentPoolBalance();
                    workerCount = data.workerData().size();
                    balanceUpdatedAt = queries.getLastSuccessfulQueryAt(braiins).orElse(null);
                    suggestedWorkerPrefix = data.poolUserName();
                }
            }
            List<AssignedMinerDto> assigned = site.getMiners().stream()
                    .filter(miner -> pool.getStratumV1Url().equals(miner.getCurrentMiningPoolTarget()))
                    .map(miner -> {
                        MinerStats stats = queries.getLastResult(miner, MinerStats.DEFAULT);
                        return new AssignedMinerDto(miner.getId(), miner.getName() == null ? miner.getIP() : miner.getName(),
                                stats == null ? "UNKNOWN" : stats.miningStatus().name());
                    }).toList();
            return new PoolOverviewDto(pool.getId(), "ACCOUNT", "bitcoin", "BTC", pool.getUrlIdentifier(), pool.getStratumV1Url(),
                    balance == null ? null : BigDecimal.valueOf(balance), balanceUpdatedAt, workerCount, assigned, null,
                    suggestedWorkerPrefix == null ? null : suggestedWorkerPrefix + ".");
        }).toList();
        List<MiningTargetService.Assignment> assignments = service.assignments(siteId);
        List<PoolOverviewDto> configuredTargets = service.list(siteId).stream()
                .filter(target -> !"bitcoin".equals(target.getCoin()))
                .map(target -> {
                    var snapshot = poolBalances.read(target);
                    MiningCoin coin = MiningCoin.from(target.getCoin());
                    return new PoolOverviewDto(target.getId(), "TARGET", coin.key(), coin.symbol(), target.getName(),
                            target.getStratumUrl(), snapshot == null ? null : snapshot.amount(),
                            snapshot == null ? null : snapshot.fetchedAt(), null,
                            assignments.stream().filter(assignment -> coin.key().equals(assignment.coin())
                                    && "ASSIGNED".equals(assignment.status()) && target.getId().equals(assignment.targetId()))
                                    .map(assignment -> site.getMiners().stream().filter(miner -> miner.getId().equals(assignment.minerId()))
                                            .findFirst().map(miner -> new AssignedMinerDto(miner.getId(),
                                                    miner.getName() == null ? miner.getIP() : miner.getName(), "ASSIGNED"))
                                            .orElse(null)).filter(java.util.Objects::nonNull).toList(),
                            target.getPayoutAddress(), target.getWorkerPrefix());
                }).toList();
        return java.util.stream.Stream.concat(accounts.stream(), configuredTargets.stream()).toList();
    }

    private de.verdox.pv_miner.pvsite.PVSiteEntity requireSite(UUID siteId) {
        return sites.findById(siteId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PV site not found"));
    }

    public record TargetRequest(UUID id, String coin, String algorithm, String payoutCoin, String payoutAddress,
                                String name, String stratumUrl, String workerPrefix, int priority, boolean enabled) { }
    public record TargetDto(UUID id, String coin, String algorithm, String payoutCoin, String payoutAddress,
                            String name, String stratumUrl, String workerPrefix, int priority, boolean enabled) {
        static TargetDto from(MiningTargetEntity entity) {
            return new TargetDto(entity.getId(), entity.getCoin(), entity.getAlgorithm(), entity.getPayoutCoin(), entity.getPayoutAddress(), entity.getName(), entity.getStratumUrl(),
                    entity.getWorkerPrefix(), entity.getPriority(), entity.isEnabled());
        }
    }
    public record CoinDto(String key, String symbol, String algorithm, boolean automaticAssignment, boolean targetConfigurable) { }
    public record PoolOverviewDto(UUID id, String source, String coin, String balanceCoin, String name, String stratumUrl, BigDecimal balance, Instant balanceUpdatedAt,
                                  Integer poolWorkerCount, List<AssignedMinerDto> assignedMiners, String payoutAddress, String suggestedWorkerPrefix) { }
    public record AssignedMinerDto(UUID id, String name, String status) { }
}
