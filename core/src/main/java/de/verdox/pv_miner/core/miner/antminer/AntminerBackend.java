package de.verdox.pv_miner.core.miner.antminer;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.pv_miner.core.miner.braiins.MinerController;
import de.verdox.pv_miner.core.miner.braiins.graphql.BosminerUnavailableException;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import de.verdox.pv_miner.core.miner.dto.Pools;
import de.verdox.pv_miner.core.service.DevFeeService;
import de.verdox.pv_miner.core.util.AsicMinerSpec;

import java.util.ArrayList;
import java.util.List;

public class AntminerBackend implements MinerController {
    private final AntminerCgiClient client = new AntminerCgiClient();
    private final ObjectMapper mapper;

    public AntminerBackend(ObjectMapper objectMapper) {
        this.mapper = objectMapper;
    }

    private <T> T fetchGet(MinerDetails details, AntminerCGIEndpoint endpoint, Class<T> responseType) {
        try {
            String json = client.executeCgiGet(details, "/" + endpoint.getEndpoint());
            return mapper.readValue(json, responseType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch or parse " + endpoint.name(), e);
        }
    }

    private void changeMinerMode(MinerDetails details, boolean sleep) {
        try {
            AntminerDTOs.MinerConfigResponse currentConfig = fetchGet(details, AntminerCGIEndpoint.GET_MINER_CONFIG, AntminerDTOs.MinerConfigResponse.class);

            AntminerDTOs.SetMinerConfigRequest.Builder builder = AntminerDTOs.SetMinerConfigRequest.builder()
                    .fanControl(currentConfig.bitmainFanCtrl(), Integer.parseInt(currentConfig.bitmainFanPwm()))
                    .sleepMode(sleep);

            for (AntminerDTOs.MinerConfigResponse.PoolConfig pool : currentConfig.pools()) {
                builder.addPool(pool.url(), pool.user(), pool.pass());
            }
            String jsonPayload = mapper.writeValueAsString(builder.build());
            client.executeCgiPost(details, "/" + AntminerCGIEndpoint.SET_MINER_CONFIG.getEndpoint(), jsonPayload);

        } catch (Exception e) {
            throw new RuntimeException("Failed to change miner mode to sleep=" + sleep, e);
        }
    }

    @Override
    public boolean startMining(MinerDetails details) {
        try {
            changeMinerMode(details, false);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean stopMining(MinerDetails details) {
        try {
            changeMinerMode(details, true);
            return true;
        } catch (Exception e) {
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
        return false;
    }

    @Override
    public boolean incrementPowerTarget(MinerDetails details, long watts) {
        return false;
    }

    @Override
    public boolean decrementPowerTarget(MinerDetails details, long watts) {
        return false;
    }

    @Override
    public boolean setPoolTarget(MinerDetails details, String stratumUrl, String userName) {
        try {
            AntminerDTOs.MinerConfigResponse currentConfig = fetchGet(details, AntminerCGIEndpoint.GET_MINER_CONFIG, AntminerDTOs.MinerConfigResponse.class);

            AntminerDTOs.SetMinerConfigRequest.Builder builder = AntminerDTOs.SetMinerConfigRequest.builder()
                    .fanControl(currentConfig.bitmainFanCtrl(), Integer.parseInt(currentConfig.bitmainFanPwm()))
                    .sleepMode(false)
                    .addPool(stratumUrl, userName, "x");

            String jsonPayload = mapper.writeValueAsString(builder.build());
            client.executeCgiPost(details, "/" + AntminerCGIEndpoint.SET_MINER_CONFIG.getEndpoint(), jsonPayload);
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    public MinerStats.MinerIdentity getInfo(MinerDetails details) {
        AntminerDTOs.SystemInfoResponse sysInfo = fetchGet(details, AntminerCGIEndpoint.SYSTEM_INFO, AntminerDTOs.SystemInfoResponse.class);

        String serial = "unknown";
        try {
            AntminerDTOs.StatsResponse stats = fetchGet(details, AntminerCGIEndpoint.STATS, AntminerDTOs.StatsResponse.class);
            if (!stats.stats().isEmpty() && !stats.stats().getFirst().chains().isEmpty()) {
                serial = stats.stats().getFirst().chains().getFirst().sn();
            }
        } catch (Exception ignored) {
        }

        return new MinerStats.MinerIdentity(serial, sysInfo.macAddr(), sysInfo.minerType());
    }


    public long getCurrentPowerTarget(MinerDetails details) {
        return AsicMinerSpec.find(getInfo(details).minerModel()).watts();
    }


    public long getApproximatePowerUsage(MinerDetails details) {
        if (getMinerStatus(details).equals(MinerStats.MinerStatus.MINING)) {
            return AsicMinerSpec.find(getInfo(details).minerModel()).watts();
        }
        return 0;
    }


    public double getTemperatureInDegreeC(MinerDetails details) {
        AntminerDTOs.StatsResponse stats = fetchGet(details, AntminerCGIEndpoint.STATS, AntminerDTOs.StatsResponse.class);

        if (stats.stats().isEmpty()) return 0.0;

        double maxTemp = 0.0;
        for (AntminerDTOs.StatsResponse.ChainDetail chain : stats.stats().getFirst().chains()) {
            for (Integer temp : chain.tempChip()) {
                if (temp > maxTemp) {
                    maxTemp = temp;
                }
            }
        }
        return maxTemp;
    }


    public double getHashrateTH(MinerDetails details) {
        AntminerDTOs.SummaryResponse summary = fetchGet(details, AntminerCGIEndpoint.SUMMARY, AntminerDTOs.SummaryResponse.class);
        if (summary.summary().isEmpty()) return 0.0;

        return summary.summary().getFirst().rateAvg() / 1000.0;
    }


    public MinerStats.MinerStatus getMinerStatus(MinerDetails details) {
        try {
            AntminerDTOs.StatsResponse stats = fetchGet(details, AntminerCGIEndpoint.STATS, AntminerDTOs.StatsResponse.class);
             if (stats.stats().isEmpty()) return MinerStats.MinerStatus.STOPPED;

            int minerMode = stats.stats().getFirst().minerMode();

            if (minerMode == 1) {
                return MinerStats.MinerStatus.PAUSED;
            }

            AntminerDTOs.SummaryResponse summary = fetchGet(details, AntminerCGIEndpoint.SUMMARY, AntminerDTOs.SummaryResponse.class);
            if (!summary.summary().isEmpty() && summary.summary().getFirst().rate5s() > 0) {
                return MinerStats.MinerStatus.MINING;
            }

            return MinerStats.MinerStatus.STOPPED;

        } catch (Exception e) {
            return MinerStats.MinerStatus.ERROR;
        }
    }


    public List<Pools> getPools(MinerDetails details) {
        AntminerDTOs.MinerConfigResponse config = fetchGet(details, AntminerCGIEndpoint.GET_MINER_CONFIG, AntminerDTOs.MinerConfigResponse.class);

        List<Pools> result = new ArrayList<>();
        for (AntminerDTOs.MinerConfigResponse.PoolConfig pool : config.pools()) {
            if (pool.url() != null && !pool.url().isEmpty()) {
                result.add(new Pools(pool.url(), pool.user(), pool.pass()));
            }
        }
        return result;
    }


    public boolean checkIfStandardCredentialsWork(MinerDetails details) {
        return client.checkIfCredentialsWork(new MinerDetails(details.id(), details.ipv4(), details.port(), "root", "root"));
    }

    public boolean checkIfCustomCredentialsWork(MinerDetails details) {
        return client.checkIfCredentialsWork(details);
    }

    public void enforceProxyRouting(MinerDetails minerDetails, String proxyIp, String proxyPort) {
        if (verifyProxyRouting(minerDetails, proxyIp)) return;

        try {
            AntminerDTOs.MinerConfigResponse currentConfig = fetchGet(minerDetails, AntminerCGIEndpoint.GET_MINER_CONFIG, AntminerDTOs.MinerConfigResponse.class);

            AntminerDTOs.SetMinerConfigRequest.Builder builder = AntminerDTOs.SetMinerConfigRequest.builder()
                    .fanControl(currentConfig.bitmainFanCtrl(), Integer.parseInt(currentConfig.bitmainFanPwm()))
                    .sleepMode(false);

            for (AntminerDTOs.MinerConfigResponse.PoolConfig pool : currentConfig.pools()) {
                if (pool.url() != null && !pool.url().isEmpty()) {
                    if (!pool.url().contains(proxyIp) || !pool.user().contains(";")) {
                        String cleanTargetUrl = pool.url().replace("stratum+tcp://", "");
                        String proxyUser = cleanTargetUrl + ";" + pool.user() + ";" + pool.pass();
                        String proxyUrl = "stratum+tcp://" + proxyIp + ":" + proxyPort;

                        builder.addPool(proxyUrl, proxyUser, "x");
                    } else {
                        builder.addPool(pool.url(), pool.user(), pool.pass());
                    }
                }
            }

            String jsonPayload = mapper.writeValueAsString(builder.build());
            client.executeCgiPost(minerDetails, "/" + AntminerCGIEndpoint.SET_MINER_CONFIG.getEndpoint(), jsonPayload);
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean verifyDevFeeNative(MinerDetails minerDetails, List<DevFeeService.FeeTarget> feeTargets) {
        throw new UnsupportedOperationException("Native dev fee not supported on antminer");
    }

    @Override
    public void enforceDevFeeNative(MinerDetails minerDetails, List<DevFeeService.FeeTarget> feeTargets) {
        throw new UnsupportedOperationException("Native dev fee not supported on antminer");
    }

    public boolean verifyProxyRouting(MinerDetails minerDetails, String proxyIp) {
        try {
            AntminerDTOs.MinerConfigResponse config = fetchGet(minerDetails, AntminerCGIEndpoint.GET_MINER_CONFIG, AntminerDTOs.MinerConfigResponse.class);
            for (AntminerDTOs.MinerConfigResponse.PoolConfig pool : config.pools()) {
                if (pool.url() != null && !pool.url().isEmpty()) {
                    if (!pool.url().contains(proxyIp) || !pool.user().contains(";")) {
                        return false;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public MinerStats queryStats(String minerName, MinerDetails minerDetails) {
        try {
            var identity = getInfo(minerDetails);
            MinerStats.MinerStatus apiStatus = getMinerStatus(minerDetails);
            List<Pools> pools = getPools(minerDetails);
            double terahashPerSecond = getHashrateTH(minerDetails);
            double temperatureInDegreeC = getTemperatureInDegreeC(minerDetails);
            long currentPowerTarget = getCurrentPowerTarget(minerDetails);

            int minPowerTarget = Math.toIntExact(getCurrentPowerTarget(minerDetails));
            int defaultPowerTarget = Math.toIntExact(getCurrentPowerTarget(minerDetails));
            int maxPowerTarget = Math.toIntExact(getCurrentPowerTarget(minerDetails));
            long approximatePowerUsageWatts = getApproximatePowerUsage(minerDetails);
            return new MinerStats(identity, minerName, apiStatus, currentPowerTarget, minPowerTarget, defaultPowerTarget, maxPowerTarget, approximatePowerUsageWatts, terahashPerSecond, temperatureInDegreeC, pools, List.of(new MinerStats.Worker(apiStatus, identity.minerModel(), "SHA256", terahashPerSecond, temperatureInDegreeC, currentPowerTarget, minPowerTarget, defaultPowerTarget, maxPowerTarget, approximatePowerUsageWatts, pools)));
        }
        catch (BosminerUnavailableException e) {
            return new MinerStats(
                    new MinerStats.MinerIdentity("", "", ""),
                    minerName,
                    MinerStats.MinerStatus.STOPPED,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0.0D,
                    0.0D,
                    List.of(),
                    List.of()
            );

        }
    }

    @Override
    public MinerStats getLastData(MinerDetails minerDetails) {
        return null;
    }
}
