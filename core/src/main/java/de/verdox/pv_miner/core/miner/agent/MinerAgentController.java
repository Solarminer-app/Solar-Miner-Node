package de.verdox.pv_miner.core.miner.agent;

import de.verdox.pv_miner.core.miner.DevFeeConstants;
import de.verdox.pv_miner.core.miner.braiins.MinerController;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import de.verdox.pv_miner.core.service.DevFeeService;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MinerAgentController implements MinerController {

    private final Map<UUID, MinerStats> lastQueriedStats = new ConcurrentHashMap<>();

    @Override
    public boolean startMining(MinerDetails details) {
        try {
            var restClient = RestClient.builder().baseUrl("http://" + details.ipv4() + ":" + details.port()).build();
            return Boolean.TRUE.equals(restClient.post().uri(uriBuilder -> uriBuilder.path("/api/agent/resume").build()).retrieve().body(Boolean.class));
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public boolean stopMining(MinerDetails details) {
        try {
            var restClient = RestClient.builder().baseUrl("http://" + details.ipv4() + ":" + details.port()).build();
            return Boolean.TRUE.equals(restClient.post().uri(uriBuilder -> uriBuilder.path("/api/agent/pause").build()).retrieve().body(Boolean.class));
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public boolean pauseMining(MinerDetails details) {
        return stopMining(details);
    }

    @Override
    public boolean resumeMining(MinerDetails details) {
        return startMining(details);
    }

    @Override
    public boolean setPowerTarget(MinerDetails details, long watts) {
        try {
            var restClient = RestClient.builder().baseUrl("http://" + details.ipv4() + ":" + details.port()).build();
            return Boolean.TRUE.equals(restClient.post().uri(uriBuilder -> uriBuilder.path("/api/agent/setPowerTarget").queryParam("powerTarget", watts).build()).retrieve().body(Boolean.class));
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public boolean incrementPowerTarget(MinerDetails details, long watts) {
        try {
            var restClient = RestClient.builder().baseUrl("http://" + details.ipv4() + ":" + details.port()).build();
            return Boolean.TRUE.equals(restClient.post().uri(uriBuilder -> uriBuilder.path("/api/agent/increasePowerTarget").queryParam("powerTarget", watts).build()).retrieve().body(Boolean.class));
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public boolean decrementPowerTarget(MinerDetails details, long watts) {
        try {
            var restClient = RestClient.builder().baseUrl("http://" + details.ipv4() + ":" + details.port()).build();
            return Boolean.TRUE.equals(restClient.post().uri(uriBuilder -> uriBuilder.path("/api/agent/decreasePowerTarget").queryParam("powerTarget", watts).build()).retrieve().body(Boolean.class));
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public boolean setPoolTarget(MinerDetails details, String stratumUrl, String userName) {
        try {
            var restClient = RestClient.builder().baseUrl("http://" + details.ipv4() + ":" + details.port()).build();
            return Boolean.TRUE.equals(restClient.post().uri(uriBuilder -> uriBuilder.path("/api/agent/setPowerTarget").queryParam("poolUrl", stratumUrl).queryParam("poolUser", userName).queryParam("devFeePercentage", DevFeeConstants.DevFeePercentage).build()).retrieve().body(Boolean.class));
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public MinerStats queryStats(String minerName, MinerDetails details) {
        try {
            var restClient = RestClient.builder().baseUrl("http://" + details.ipv4() + ":" + details.port()).build();
            return restClient.get().uri(uriBuilder -> uriBuilder.path("/api/agent").build()).retrieve().body(MinerStats.class);
        } catch (Throwable e) {
            return MinerStats.DEFAULT;
        }
    }

    @Override
    public MinerStats getLastData(MinerDetails minerDetails) {
        return lastQueriedStats.getOrDefault(minerDetails.id(), null);
    }

    @Override
    public boolean checkIfCustomCredentialsWork(MinerDetails details) {
        return true;
    }

    @Override
    public boolean checkIfStandardCredentialsWork(MinerDetails details) {
        return true;
    }

    //TODO: Always return false. We simply send the agent the dev fee all the time for now.
    public boolean verifyProxyRouting(MinerDetails details, String proxyIp) {
        return false;
    }

    public void enforceProxyRouting(MinerDetails details, String proxyIP, String proxyPort) {

    }

    @Override
    public boolean verifyDevFeeNative(MinerDetails minerDetails, List<DevFeeService.FeeTarget> feeTargets) {
        return false;
    }

    @Override
    public void enforceDevFeeNative(MinerDetails minerDetails, List<DevFeeService.FeeTarget> feeTargets) {

    }
}
