package de.verdox.pv_miner.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import de.verdox.pv_miner.discovery.DiscoveryService;
import de.verdox.pv_miner.dto.MiningPageDto;
import de.verdox.pv_miner.dto.MiningPageRequests.*;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.entity.EntityService;
import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.MiningOS;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.miningcontroller.MinerClusterService;
import de.verdox.pv_miner.miningpool.MiningPoolEntity;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteRef;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import de.verdox.pv_miner_extensions.miner.AgentMinerEntity;
import de.verdox.pv_miner_extensions.miner.AntminerEntity;
import de.verdox.pv_miner_extensions.miner.BraiinsOSAsicMinerEntity;
import de.verdox.pv_miner_extensions.pools.braiins.BraiinsPoolEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pv-site/{siteId}/mining")
@CrossOrigin(origins = "http://localhost:3000")
@Tag(name = "Mining")
public class MiningController {
    private static final long ELECTRICAL_RISK_THRESHOLD_WATTS = 3_200;

    private final PVSiteRepository pvSiteRepository;
    private final MinerClusterService clusterService;
    private final EntityQueryService entityQueryService;
    private final EntityService entityService;
    private final DiscoveryService discoveryService;
    private final MinerApiClient minerApiClient;

    public MiningController(PVSiteRepository pvSiteRepository, MinerClusterService clusterService, EntityQueryService entityQueryService, EntityService entityService, DiscoveryService discoveryService, MinerApiClient minerApiClient) {
        this.pvSiteRepository = pvSiteRepository;
        this.clusterService = clusterService;
        this.entityQueryService = entityQueryService;
        this.entityService = entityService;
        this.discoveryService = discoveryService;
        this.minerApiClient = minerApiClient;
    }

    @GetMapping
    public MiningPageDto getMiningPage(@PathVariable UUID siteId) {
        PVSiteEntity site = findSite(siteId);
        List<MiningPageDto.ClusterDto> clusters = clusterService.getAvailableClusterNames().stream().sorted(String.CASE_INSENSITIVE_ORDER).map(name -> {
            MinerClusterService.ClusterInstance instance = clusterService.getCluster(siteId, name);
            List<MiningPageDto.MinerDto> miners = instance.getAssignedMiners().stream().sorted(minerComparator()).map(this::toMinerDto).toList();
            return new MiningPageDto.ClusterDto(name, instance.isRunning(), miners);
        }).toList();

        List<MiningPageDto.MinerDto> unassigned = clusterService.getUnassignedMiners(site).stream().sorted(minerComparator()).map(this::toMinerDto).toList();

        List<MiningPageDto.MinerDto> connectedMiners = site.getMiners().stream().sorted(minerComparator()).map(this::toMinerDto).toList();

        List<MiningPageDto.PoolDto> connectedPools = site.getConnectedMiningPools().stream().sorted(Comparator.comparing(pool -> pool.getUrlIdentifier(), String.CASE_INSENSITIVE_ORDER)).map(pool -> new MiningPageDto.PoolDto(pool.getId(), pool instanceof BraiinsPoolEntity ? "BRAIINS" : pool.getClass().getSimpleName(), pool.getUrlIdentifier(), pool.getStratumV1Url())).toList();

        double totalHashrate = site.getMiners().stream().map(miner -> entityQueryService.getLastResult(miner, MinerStats.DEFAULT)).mapToDouble(stats -> stats == null ? 0 : stats.terahashPerSecond()).sum();

        return new MiningPageDto(site.getName(), clusters.size(), (int) clusters.stream().filter(MiningPageDto.ClusterDto::running).count(), site.getMiners().size(), totalHashrate, clusters, connectedMiners, unassigned, connectedPools, minerApiClient.getDevFeeOverview(site.getReferralCode()));
    }

    @PostMapping("/referral")
    public ResponseEntity<Void> saveReferral(@PathVariable UUID siteId, @RequestBody ReferralRequest request) {
        PVSiteEntity site = findSite(siteId);
        String referralCode = requireText(request.referralCode(), "Referral code is required");
        if (referralCode.length() > 128 || !referralCode.matches("[A-Za-z0-9._-]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Referral code has an invalid format");
        }
        if (!minerApiClient.validateReferral(referralCode)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Referral is not known by the SolarMiner backend");
        }
        site.setReferralCode(referralCode);
        pvSiteRepository.save(site);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/referral")
    public ResponseEntity<Void> deleteReferral(@PathVariable UUID siteId) {
        PVSiteEntity site = findSite(siteId);
        site.setReferralCode(null);
        pvSiteRepository.save(site);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/miners/discovery")
    public CompletableFuture<List<MiningPageDto.DiscoveredMinerDto>> discoverMiners(@PathVariable UUID siteId, @RequestParam String subnet) {
        PVSiteEntity site = findSite(siteId);
        String normalizedSubnet = normalizeSubnet(subnet);
        Set<String> connectedIps = site.getMiners().stream().map(MinerEntity::getIP).collect(Collectors.toSet());
        List<MiningPageDto.DiscoveredMinerDto> discovered = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<List<MiningPageDto.DiscoveredMinerDto>> result = new CompletableFuture<>();

        discoveryService.discoverMiners(normalizedSubnet, miner -> {
            if (!connectedIps.contains(miner.ipAddress())) {
                discovered.add(new MiningPageDto.DiscoveredMinerDto(miner.model(), miner.ipAddress(), miner.os().name()));
            }
        }, () -> result.complete(discovered.stream().sorted(Comparator.comparing(MiningPageDto.DiscoveredMinerDto::ipAddress)).toList()));
        return result;
    }

    @PostMapping("/miners")
    public ResponseEntity<Void> connectMiner(@PathVariable UUID siteId, @RequestBody MinerConnectionRequest request) {
        PVSiteEntity site = findSite(siteId);
        String ipAddress = requireText(request.ipAddress(), "IP address is required");
        if (site.getMiners().stream().anyMatch(miner -> ipAddress.equals(miner.getIP()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Miner is already connected");
        }

        MiningOS operatingSystem;
        try {
            operatingSystem = MiningOS.valueOf(requireText(request.operatingSystem(), "Operating system is required"));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported mining operating system");
        }

        String username = request.username() == null || request.username().isBlank() ? "root" : request.username().trim();
        String password = request.password() == null || request.password().isBlank() ? "root" : request.password();
        MinerEntity<?> miner = createMiner(operatingSystem, ipAddress, username, password);
        miner.setName(request.model() == null || request.model().isBlank() ? ipAddress : request.model().trim());

        if (operatingSystem != MiningOS.AGENT) {
            boolean connectionWorks = minerApiClient.checkIfCustomCredentialsWork(operatingSystem, miner.getDetails());
            if (!connectionWorks) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Miner credentials could not be verified");
            }
        }

        try {
            MinerStats stats = entityQueryService.query(miner);
            miner.setMinPowerTarget(stats.minPowerTarget() > 0 ? stats.minPowerTarget() : 800);
            miner.setMaxPowerTarget(stats.defaultPowerTarget() > 0 ? stats.defaultPowerTarget() : 1200);
        } catch (Throwable ignored) {
            // The monitoring service will populate the technical limits after the miner was persisted.
        }

        entityService.save(miner, site);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/pools")
    public ResponseEntity<Void> connectPool(@PathVariable UUID siteId, @RequestBody PoolConnectionRequest request) {
        PVSiteEntity site = findSite(siteId);
        String type = requireText(request.type(), "Pool type is required");
        if (!"BRAIINS".equalsIgnoreCase(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported mining pool type");
        }
        if (site.getConnectedMiningPools().stream().anyMatch(pool -> pool instanceof BraiinsPoolEntity)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Braiins Pool is already connected");
        }

        BraiinsPoolEntity pool = new BraiinsPoolEntity();
        pool.setAuthToken(requireText(request.accessToken(), "Access token is required"));
        if (!entityQueryService.ping(pool, 10, TimeUnit.SECONDS).join()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Pool connection could not be verified");
        }

        entityService.save(pool, site);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/miners/{minerId}")
    public ResponseEntity<Void> deleteMiner(@PathVariable UUID siteId, @PathVariable UUID minerId) {
        PVSiteEntity site = findSite(siteId);
        MinerEntity<?> miner = site.getMiners().stream().filter(candidate -> minerId.equals(candidate.getId())).findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Miner not found on this PV site"));

        entityService.delete(miner);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/pools/{poolId}")
    public ResponseEntity<Void> deletePool(@PathVariable UUID siteId, @PathVariable UUID poolId) {
        PVSiteEntity site = findSite(siteId);
        MiningPoolEntity<?> pool = site.getConnectedMiningPools().stream().filter(candidate -> poolId.equals(candidate.getId())).findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mining pool not found on this PV site"));

        entityService.delete(pool);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/clusters/{clusterName}/start")
    public ResponseEntity<Void> startCluster(@PathVariable UUID siteId, @PathVariable String clusterName) throws Exception {
        PVSiteEntity site = findSite(siteId);
        MinerClusterService.ClusterInstance cluster = clusterService.getCluster(siteId, clusterName);
        if (cluster.getAssignedMiners().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        clusterService.startCluster(clusterName, new PVSiteRef(site.getId(), pvSiteRepository));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/clusters/{clusterName}/stop")
    public ResponseEntity<Void> stopCluster(@PathVariable UUID siteId, @PathVariable String clusterName) {
        findSite(siteId);
        clusterService.stopCluster(siteId, clusterName);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/clusters/{clusterName}/miners")
    public ResponseEntity<Void> assignMiners(@PathVariable UUID siteId, @PathVariable String clusterName, @RequestBody MinerSelectionRequest request) {
        PVSiteEntity site = findSite(siteId);
        Set<UUID> requestedIds = Set.copyOf(request.minerIds());
        Set<MinerEntity<?>> miners = site.getMiners().stream().filter(miner -> requestedIds.contains(miner.getId())).filter(miner -> miner.getClusterName() == null || miner.getClusterName().isBlank()).collect(Collectors.toSet());

        if (miners.size() != requestedIds.size()) {
            return ResponseEntity.badRequest().build();
        }

        clusterService.getCluster(siteId, clusterName).assignMiners(miners);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/clusters/{clusterName}/miners/remove")
    public ResponseEntity<Void> removeMiners(@PathVariable UUID siteId, @PathVariable String clusterName, @RequestBody MinerSelectionRequest request) {
        findSite(siteId);
        Set<UUID> requestedIds = Set.copyOf(request.minerIds());
        MinerClusterService.ClusterInstance cluster = clusterService.getCluster(siteId, clusterName);
        Set<MinerEntity<?>> miners = cluster.getAssignedMiners().stream().filter(miner -> requestedIds.contains(miner.getId())).collect(Collectors.toSet());

        if (miners.size() != requestedIds.size()) {
            return ResponseEntity.badRequest().build();
        }

        cluster.removeMiners(miners);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/miners/{minerId}/power-targets")
    public ResponseEntity<Void> updateMinerPowerTargets(@PathVariable UUID siteId, @PathVariable UUID minerId, @RequestBody MinerPowerTargetRequest request) {
        PVSiteEntity site = findSite(siteId);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Power target settings are required");
        }

        MinerEntity<?> miner = site.getMiners().stream().filter(candidate -> minerId.equals(candidate.getId())).findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Miner not found on this PV site"));

        if (!miner.getOS().supportsDynamicPowerScaling()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Miner does not support dynamic power scaling");
        }

        MinerStats stats = entityQueryService.getLastResult(miner, MinerStats.DEFAULT);
        if (stats == null || stats.minPowerTarget() <= 0 || stats.maxPowerTarget() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hardware power limits are currently unavailable");
        }

        long hardwareMinimum = stats.minPowerTarget();
        long hardwareMaximum = stats.maxPowerTarget();
        long minimum = request.minimumPowerWatts();
        long maximum = request.maximumPowerWatts();

        if (minimum < hardwareMinimum || minimum > hardwareMaximum || maximum < hardwareMinimum || maximum > hardwareMaximum) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Power targets must stay within the hardware limits");
        }
        if (minimum > maximum) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Minimum power target must not exceed maximum power target");
        }
        if (maximum > ELECTRICAL_RISK_THRESHOLD_WATTS && !request.electricalRiskAcknowledged()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Electrical risk acknowledgement is required above 3200 watts");
        }
        if (minimum > Integer.MAX_VALUE || maximum > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Power target is too large");
        }

        miner.setMinPowerTarget((int) minimum);
        miner.setMaxPowerTarget((int) maximum);
        entityService.save(miner, site);
        return ResponseEntity.noContent().build();
    }

    private PVSiteEntity findSite(UUID siteId) {
        return pvSiteRepository.findById(siteId).orElseThrow(() -> new IllegalArgumentException("PV-Site nicht gefunden"));
    }

    private MinerEntity<?> createMiner(MiningOS operatingSystem, String ipAddress, String username, String password) {
        if (operatingSystem == MiningOS.BRAIINS) {
            BraiinsOSAsicMinerEntity miner = new BraiinsOSAsicMinerEntity();
            miner.setHost(ipAddress);
            miner.setPort(50051);
            miner.setUsername(username);
            miner.setPassword(password);
            return miner;
        }
        if (operatingSystem == MiningOS.ANTMINER_STOCK_OS) {
            AntminerEntity miner = new AntminerEntity();
            miner.setHost(ipAddress);
            miner.setPort(80);
            miner.setUsername(username);
            miner.setPassword(password);
            return miner;
        }
        if (operatingSystem == MiningOS.AGENT) {
            AgentMinerEntity miner = new AgentMinerEntity();
            miner.setHost(ipAddress);
            miner.setPort(8084);
            return miner;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported mining operating system");
    }

    private String normalizeSubnet(String subnet) {
        String value = requireText(subnet, "Subnet is required");
        if (!value.matches("^(25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.?$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid IPv4 subnet");
        }
        return value.endsWith(".") ? value : value + ".";
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private Comparator<MinerEntity<?>> minerComparator() {
        return Comparator.comparing(miner -> miner.getName() == null ? miner.getIP() : miner.getName(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
    }

    private MiningPageDto.MinerDto toMinerDto(MinerEntity<?> miner) {
        MinerStats stats = entityQueryService.getLastResult(miner, MinerStats.DEFAULT);
        if (stats == null) {
            stats = MinerStats.DEFAULT;
        }

        return new MiningPageDto.MinerDto(miner.getId(), miner.getName(), miner.getIP(), stats.minerIdentity().minerModel(), stats.miningStatus().name(), stats.terahashPerSecond(), stats.approximatedPowerUsageWatts(), stats.temperatureCelsius(), miner.getCurrentMiningPoolTarget(), stats.minPowerTarget(), stats.defaultPowerTarget() > 0 ? stats.defaultPowerTarget() : stats.maxPowerTarget(), stats.maxPowerTarget(), miner.getMinPowerTarget(), miner.getMaxPowerTarget(), miner.getOS().supportsDynamicPowerScaling(), miner.getPowerStepSizeWatts(), miner.getMinRunTimeMinutes(), miner.getMinIdleTimeMinutes(), miner.getPowerChangeLockTimeMinutes());
    }
}
