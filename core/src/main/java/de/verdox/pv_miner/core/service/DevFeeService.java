package de.verdox.pv_miner.core.service;

import de.verdox.pv_miner.core.miner.DevFeeConstants;
import de.verdox.pv_miner.core.miner.MiningOS;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@RegisterReflectionForBinding(DevFeeService.FeeTarget.class)
@Service
public class DevFeeService {
    private static final Logger LOGGER = Logger.getLogger(DevFeeService.class.getName());
    private static final long ENFORCEMENT_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(1);

    private final Map<String, Long> lastCheckTimes = new ConcurrentHashMap<>();
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
        String coin = "bitcoin";

        if (miningOS.supportsNativeSplitting()) {
            enforceNativeDevFee(coin, minerIdentity, minerService, miningOS, minerDetails);
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

    private void enforceNativeDevFee(String coin, MinerStats.MinerIdentity minerIdentity, MinerService minerService, MiningOS miningOS, MinerDetails minerDetails) {
        long currentTime = System.currentTimeMillis();
        Long lastCheckTime = lastCheckTimes.getOrDefault(minerIdentity.macAddress(), 0L);

        if (lastCheckTime != null && (currentTime - lastCheckTime) < ENFORCEMENT_COOLDOWN_MS) {
            return;
        }

        var feeTargets = fetchFeeTargets(coin);

        try {
            feeTargets = feeTargets.stream().map(feeTarget -> feeTarget.withWorkerName(minerIdentity)).toList();
            if (!minerService.verifyDevFeeNative(miningOS, minerDetails, feeTargets)) {
                LOGGER.info("Enforcing dev fee on " + minerDetails.ipv4() + " [" + miningOS + "]");
                minerService.enforceDevFeeNative(miningOS, minerDetails, feeTargets);
            }
            else {
                LOGGER.info(minerDetails.ipv4() + " [" + miningOS + "] has dev fee set up!");
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
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("http")
                            .host(proxyDiscoveryService.getCurrentProxyIp())
                            .port(8090)
                            .path("/api/v1/fees/{coin}/targets")
                            .build(coin))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (RestClientException e) {
            LOGGER.log(Level.WARNING, "Could not fetch fee targets for coin: " + coin, e);
            return List.of();
        }
    }

    public record FeeTarget(
            String targetId,
            String poolAddress,
            String workerName,
            String password,
            double percentage
    ) {
        public FeeTarget withWorkerName(MinerStats.MinerIdentity minerIdentity) {
            return new FeeTarget(targetId, poolAddress, workerName+sanitizeWorkerName(minerIdentity.minerModel() + " " + minerIdentity.macAddress()), password, percentage);
        }
    }
}
