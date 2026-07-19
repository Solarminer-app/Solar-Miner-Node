package de.verdox.pv_miner.miner;

import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.shared.dto.DevFeeOverviewDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

@Service
public class MinerApiClient {
    private static final Logger LOGGER = Logger.getLogger(MinerApiClient.class.getName());
    private final RestClient restClient;

    public MinerApiClient(RestClient.Builder restClientBuilder, @Value("${solarmining.core.url:http://localhost:8080/api/miners}") String baseUrl) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    public boolean startMining(MiningOS os, MinerDetails details) {
        return executeStateCommand("/start", os, details);
    }

    public boolean stopMining(MiningOS os, MinerDetails details) {
        return executeStateCommand("/stop", os, details);
    }

    public boolean pauseMining(MiningOS os, MinerDetails details) {
        return executeStateCommand("/pause", os, details);
    }

    public boolean resumeMining(MiningOS os, MinerDetails details) {
        return executeStateCommand("/resume", os, details);
    }

    public boolean setMiningPoolTarget(MiningOS os, MinerDetails details, String stratumUrl, String poolUserName, MinerStats.MinerIdentity minerIdentity) {
        return setMiningPoolTarget(os, details, stratumUrl, poolUserName, minerIdentity, null);
    }

    public boolean setMiningPoolTarget(MiningOS os, MinerDetails details, String stratumUrl, String poolUserName, MinerStats.MinerIdentity minerIdentity, String referralCode) {
        String workerNameForPool = poolUserName + sanitizeWorkerName(minerIdentity.minerModel() + " " + minerIdentity.macAddress());
        return executePoolCommand("/pool-target", new SetPoolRequest(os, details, stratumUrl, workerNameForPool, normalizeReferral(referralCode)));
    }

    public boolean setPowerTarget(MiningOS os, MinerDetails details, long watts) {
        return executePowerCommand("/power-target", new PowerTargetRequest(os, details, watts));
    }

    public boolean incrementPowerTarget(MiningOS os, MinerDetails details, long watts) {
        return executePowerCommand("/power-target/increment", new PowerTargetRequest(os, details, watts));
    }

    public boolean decrementPowerTarget(MiningOS os, MinerDetails details, long watts) {
        return executePowerCommand("/power-target/decrement", new PowerTargetRequest(os, details, watts));
    }

    public boolean checkStandardCredentialsWork(MiningOS os, MinerDetails details) {
        return Boolean.TRUE.equals(restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/check-standard-credentials")
                        .queryParamIfPresent("os", Optional.ofNullable(os))
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(details)
                .retrieve()
                .body(Boolean.class));
    }

    public boolean checkIfCustomCredentialsWork(MiningOS os, MinerDetails details) {
        return Boolean.TRUE.equals(restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/check-custom-credentials")
                        .queryParamIfPresent("os", Optional.ofNullable(os))
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(details)
                .retrieve()
                .body(Boolean.class));
    }

    public MinerStats getStats(MiningOS os, MinerDetails details) {
        return getStats(os, details, null);
    }

    public MinerStats getStats(MiningOS os, MinerDetails details, String referralCode) {
        return restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/stats")
                        .queryParamIfPresent("os", Optional.ofNullable(os))
                        .queryParamIfPresent("referral", Optional.ofNullable(normalizeReferral(referralCode)))
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(details)
                .retrieve()
                .body(MinerStats.class);
    }

    public DevFeeOverviewDto getDevFeeOverview(String referralCode) {
        try {
            DevFeeOverviewDto overview = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/dev-fee/overview")
                            .queryParamIfPresent("referral", Optional.ofNullable(normalizeReferral(referralCode)))
                            .build())
                    .retrieve()
                    .body(DevFeeOverviewDto.class);
            return overview == null
                    ? unavailableFeeOverview(referralCode)
                    : overview;
        } catch (RestClientException exception) {
            LOGGER.warning("Could not load dev fee overview from core: " + exception.getMessage());
            return unavailableFeeOverview(referralCode);
        }
    }

    public boolean validateReferral(String referralCode) {
        String normalized = normalizeReferral(referralCode);
        if (normalized == null) return false;
        try {
            return Boolean.TRUE.equals(restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/dev-fee/referral/validate")
                            .queryParam("referral", normalized)
                            .build())
                    .retrieve()
                    .body(Boolean.class));
        } catch (RestClientException exception) {
            LOGGER.warning("Could not validate referral with core: " + exception.getMessage());
            return false;
        }
    }

    public record DetectedMiner(MiningOS os, String model) {
    }

    public DetectedMiner identifyMiningOS(String ipv4) {
        try {
            return restClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/identify-os").build())
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(ipv4)
                    .retrieve()
                    .body(DetectedMiner.class);
        } catch (RestClientException e) {
            return null;
        }
    }

    private boolean executeStateCommand(String path, MiningOS os, MinerDetails details) {
        try {
            Boolean result = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(path)
                            .queryParamIfPresent("os", Optional.ofNullable(os))
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(details)
                    .retrieve()
                    .body(Boolean.class);

            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean executePowerCommand(String path, PowerTargetRequest request) {
        try {
            Boolean result = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Boolean.class);

            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean executePoolCommand(String path, SetPoolRequest request) {
        try {
            Boolean result = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Boolean.class);

            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    public record MinerDetails(UUID id, String ipv4, int port, String username, String password) {
    }

    public record PowerTargetRequest(MiningOS os, MinerDetails minerDetails, long watts) {
    }

    public record SetPoolRequest(MiningOS os, MinerDetails minerDetails, String stratumUrl, String userName, String referralCode) {
    }

    private static String normalizeReferral(String referralCode) {
        return referralCode == null || referralCode.isBlank() ? null : referralCode.trim();
    }

    private static DevFeeOverviewDto unavailableFeeOverview(String referralCode) {
        String normalized = normalizeReferral(referralCode);
        return new DevFeeOverviewDto(false, 97.5, 2.5, normalized, normalized == null, java.util.List.of());
    }

    public static String sanitizeWorkerName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        String sanitized = rawName.replace(" ", "_");
        sanitized = sanitized.replace(":", "");
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9_\\-]", "");
        return sanitized;
    }
}
