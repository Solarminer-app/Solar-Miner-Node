package de.verdox.pv_miner.core.service;

import de.verdox.pv_miner.core.miner.DevFeeConstants;
import de.verdox.pv_miner.core.miner.MiningOS;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import de.verdox.pv_miner.shared.dto.DevFeeOverviewDto;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@RegisterReflectionForBinding({
        DevFeeService.FeeTarget.class,
        DevFeeOverviewDto.class,
        DevFeeOverviewDto.AllocationDto.class
})
@Service
public class DevFeeService {
    private static final Logger LOGGER = Logger.getLogger(DevFeeService.class.getName());
    private static final long ENFORCEMENT_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(1);
    private static final long TARGET_CACHE_MS = TimeUnit.MINUTES.toMillis(1);

    private final Map<String, Long> lastCheckTimes = new ConcurrentHashMap<>();
    private final Map<String, CachedFeeTargets> feeTargetCache = new ConcurrentHashMap<>();
    private final ProxyDiscoveryService proxyDiscoveryService;
    private final RestClient restClient;

    public DevFeeService(
            ProxyDiscoveryService proxyDiscoveryService,
            RestClient.Builder restClientBuilder
    ) {
        this.proxyDiscoveryService = proxyDiscoveryService;
        this.restClient = restClientBuilder.build();
    }

    public void enforceDevFee(MinerStats.MinerIdentity minerIdentity, MinerService minerService, MiningOS miningOS, MinerDetails minerDetails) {
        enforceDevFee(minerIdentity, minerService, miningOS, minerDetails, null);
    }

    public void enforceDevFee(MinerStats.MinerIdentity minerIdentity, MinerService minerService, MiningOS miningOS, MinerDetails minerDetails, String referralCode) {
        String coin = "bitcoin";

        if (miningOS.supportsNativeSplitting()) {
            enforceNativeDevFee(coin, minerIdentity, minerService, miningOS, minerDetails, referralCode);
        } else {
            enforceProxyRouting(coin, minerIdentity, minerService, miningOS, minerDetails);
        }
    }

    public void enforceProxyRouting(String coin, MinerStats.MinerIdentity minerIdentity, MinerService minerService, MiningOS miningOS, MinerDetails minerDetails) {
        long currentTime = System.currentTimeMillis();
        Long lastCheckTime = lastCheckTimes.getOrDefault(minerIdentity.macAddress(), 0L);

        if (lastCheckTime != null && (currentTime - lastCheckTime) < ENFORCEMENT_COOLDOWN_MS) {
            return;
        }

        try {
            if (!minerService.verifyProxyRouting(miningOS, minerDetails)) {
                LOGGER.info("Enforcing proxy routing on " + minerDetails.ipv4() + " [" + miningOS + "]");
                minerService.enforceProxyRouting(miningOS, minerDetails);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to check or enforce proxy routing on " + minerDetails.ipv4(), e);
        } finally {
            lastCheckTimes.put(minerIdentity.macAddress(), currentTime);
        }
    }

    private void enforceNativeDevFee(String coin, MinerStats.MinerIdentity minerIdentity, MinerService minerService, MiningOS miningOS, MinerDetails minerDetails, String referralCode) {
        long currentTime = System.currentTimeMillis();
        Long lastCheckTime = lastCheckTimes.getOrDefault(minerIdentity.macAddress(), 0L);

        if (lastCheckTime != null && (currentTime - lastCheckTime) < ENFORCEMENT_COOLDOWN_MS) {
            return;
        }

        var feeTargets = resolveFeeTargets(coin, referralCode);

        try {
            feeTargets = feeTargets.stream().map(feeTarget -> feeTarget.withWorkerName(minerIdentity)).toList();
            if (!minerService.verifyDevFeeNative(miningOS, minerDetails, feeTargets)) {
                LOGGER.info("Enforcing dev fee on " + minerDetails.ipv4() + " [" + miningOS + "]");
                minerService.enforceDevFeeNative(miningOS, minerDetails, feeTargets);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to check or enforce dev fee on " + minerDetails.ipv4(), e);
        } finally {
            lastCheckTimes.put(minerIdentity.macAddress(), currentTime);
        }
    }

    public static String sanitizeWorkerName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return DevFeeConstants.DEV_FEE_POOL_USER_SHA256;
        }
        String sanitized = rawName.replace(" ", "_");
        sanitized = sanitized.replace(":", "");
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9_\\-]", "");
        return sanitized;
    }

    public List<FeeTarget> fetchFeeTargets(String coin) {
        return fetchFeeTargets(coin, null);
    }

    public List<FeeTarget> fetchFeeTargets(String coin, String referralCode) {
        String normalizedReferral = normalizeReferral(referralCode);
        String cacheKey = coin.toLowerCase(Locale.ROOT) + ":" + Objects.toString(normalizedReferral, "");
        CachedFeeTargets cached = feeTargetCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.loadedAt() < TARGET_CACHE_MS) {
            return cached.targets();
        }

        try {
            List<FeeTarget> targets = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("http")
                            .host(proxyDiscoveryService.getCurrentProxyIp())
                            .port(8090)
                            .path("/api/v1/fees/{coin}/targets")
                            .queryParamIfPresent("referral", java.util.Optional.ofNullable(normalizedReferral))
                            .build(coin))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            List<FeeTarget> result = targets == null ? List.of() : List.copyOf(targets);
            feeTargetCache.put(cacheKey, new CachedFeeTargets(result, now));
            return result;
        } catch (RestClientException e) {
            LOGGER.log(Level.WARNING, "Could not fetch fee targets for coin: " + coin, e);
            return List.of();
        }
    }

    public List<FeeTarget> resolveFeeTargets(String coin, String referralCode) {
        String normalizedReferral = normalizeReferral(referralCode);
        if (normalizedReferral == null) {
            return fetchFeeTargets(coin);
        }
        List<FeeTarget> referralTargets = fetchFeeTargets(coin, normalizedReferral);
        if (hasReferralTarget(referralTargets, normalizedReferral)) {
            return referralTargets;
        }
        return fetchFeeTargets(coin);
    }

    public boolean validateReferral(String coin, String referralCode) {
        String normalizedReferral = normalizeReferral(referralCode);
        return normalizedReferral != null && hasReferralTarget(fetchFeeTargets(coin, normalizedReferral), normalizedReferral);
    }

    public DevFeeOverviewDto getOverview(String coin, String referralCode) {
        String normalizedReferral = normalizeReferral(referralCode);
        List<FeeTarget> requestedTargets = fetchFeeTargets(coin, normalizedReferral);
        boolean referralValid = normalizedReferral == null || hasReferralTarget(requestedTargets, normalizedReferral);
        List<FeeTarget> effectiveTargets = referralValid
                ? requestedTargets
                : fetchFeeTargets(coin);
        boolean backendAvailable = !requestedTargets.isEmpty() || !effectiveTargets.isEmpty();

        if (!backendAvailable) {
            return new DevFeeOverviewDto(
                    false,
                    100.0 - DevFeeConstants.DevFeePercentage,
                    DevFeeConstants.DevFeePercentage,
                    normalizedReferral,
                    normalizedReferral == null,
                    List.of()
            );
        }

        Map<String, DevFeeOverviewDto.AllocationDto> allocations = new LinkedHashMap<>();
        for (FeeTarget target : effectiveTargets) {
            boolean referralTarget = normalizedReferral != null && isReferralTarget(target, normalizedReferral);
            String type = referralTarget ? "REFERRAL" : "SOLARMINER";
            String name = referralTarget
                    ? firstNonBlank(target.beneficiaryName(), target.referralCode(), normalizedReferral)
                    : firstNonBlank(target.beneficiaryName(), "SolarMiner");
            String key = type + ":" + name;
            allocations.compute(key, (ignored, current) -> new DevFeeOverviewDto.AllocationDto(
                    type,
                    name,
                    target.percentage() + (current == null ? 0 : current.percentage())
            ));
        }

        double totalFee = allocations.values().stream()
                .mapToDouble(DevFeeOverviewDto.AllocationDto::percentage)
                .sum();
        return new DevFeeOverviewDto(
                backendAvailable,
                Math.max(0, 100.0 - totalFee),
                totalFee,
                normalizedReferral,
                referralValid,
                List.copyOf(allocations.values())
        );
    }

    static boolean hasReferralTarget(List<FeeTarget> targets, String referralCode) {
        return targets != null && targets.stream().anyMatch(target -> isReferralTarget(target, referralCode));
    }

    static boolean isReferralTarget(FeeTarget target, String referralCode) {
        if (target == null || referralCode == null) return false;
        String normalized = referralCode.trim();
        if (normalized.isEmpty()) return false;
        if (equalsIgnoreCase(target.referralCode(), normalized)) return true;
        if ("REFERRAL".equalsIgnoreCase(target.beneficiaryType())
                && (equalsIgnoreCase(target.beneficiaryName(), normalized)
                || equalsIgnoreCase(target.targetId(), normalized))) return true;
        return equalsIgnoreCase(target.targetId(), "referral:" + normalized)
                || equalsIgnoreCase(target.targetId(), "referral-" + normalized);
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "SolarMiner";
    }

    private static String normalizeReferral(String referralCode) {
        return referralCode == null || referralCode.isBlank() ? null : referralCode.trim();
    }

    public record FeeTarget(
            String targetId,
            String poolAddress,
            String workerName,
            String password,
            double percentage,
            String beneficiaryType,
            String beneficiaryName,
            String referralCode
    ) {
        public FeeTarget withWorkerName(MinerStats.MinerIdentity minerIdentity) {
            return new FeeTarget(targetId, poolAddress, workerName+sanitizeWorkerName(minerIdentity.minerModel() + " " + minerIdentity.macAddress()), password, percentage, beneficiaryType, beneficiaryName, referralCode);
        }
    }

    private record CachedFeeTargets(List<FeeTarget> targets, long loadedAt) {
    }
}
