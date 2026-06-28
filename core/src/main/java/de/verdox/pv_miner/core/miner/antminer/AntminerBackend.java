package de.verdox.pv_miner.core.miner.antminer;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.pv_miner.core.miner.braiins.graphql.BosminerUnavailableException;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import de.verdox.pv_miner.core.miner.dto.Pools;
import de.verdox.pv_miner.core.util.AsicMinerSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AntminerBackend {
    private final AntminerCgiClient client = new AntminerCgiClient();
    private final ObjectMapper mapper;

    public static void main(String[] args) {
        AntminerBackend antminerBackend = new AntminerBackend(new ObjectMapper());

        MinerDetails minerDetails = new MinerDetails(UUID.randomUUID(), "192.168.178.22", 80, "root", "root");
    }

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


    public boolean startMining(MinerDetails details) {
        try {
            changeMinerMode(details, false);
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    public boolean stopMining(MinerDetails details) {
        try {
            changeMinerMode(details, true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    public boolean pauseMining(MinerDetails details) {
        return stopMining(details);
    }


    public boolean resumeMining(MinerDetails details) {
        return startMining(details);
    }


    public boolean setPowerTarget(MinerDetails details, long watts) {
        return false;
    }


    public boolean incrementPowerTarget(MinerDetails details, long watts) {
        return false;
    }


    public boolean decrementPowerTarget(MinerDetails details, long watts) {
        return false;
    }


    public boolean setPoolTarget(MinerDetails details, String stratumUrl, String userName, boolean alsoSetDevFee) {
        try {
            AntminerDTOs.MinerConfigResponse currentConfig = fetchGet(details, AntminerCGIEndpoint.GET_MINER_CONFIG, AntminerDTOs.MinerConfigResponse.class);

            AntminerDTOs.SetMinerConfigRequest.Builder builder = AntminerDTOs.SetMinerConfigRequest.builder()
                    .fanControl(currentConfig.bitmainFanCtrl(), Integer.parseInt(currentConfig.bitmainFanPwm()))
                    .sleepMode(false)
                    .addPool(stratumUrl, userName, "x");

            if (currentConfig.pools().size() > 1) {
                builder.addPool(currentConfig.pools().get(1).url(), currentConfig.pools().get(1).user(), currentConfig.pools().get(1).pass());
            }

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


    public void enforceAndReplaceDevFee(MinerDetails minerDetails, String poolUrl, String miningAddress, double feePercentage) {
        //TODO: Replace sha256 pools with our proxy pool -> provide the original pool as username so our proxy knows.
    }


    public boolean verifyDevFee(MinerDetails minerDetails, String expectedUrl, String expectedAddress, double expectedPercentage) {
        //TODO: Check if proxy is used.
        return true;
    }

    public MinerStats queryStats(String minerName, MinerDetails minerDetails) {
        try {
            var identity = getInfo(minerDetails);
            MinerStats.MinerStatus apiStatus = getMinerStatus(minerDetails);
            List<Pools> pools = getPools(minerDetails);
            double terahashPerSecond = getHashrateTH(minerDetails);
            double temperatureInDegreeC = getTemperatureInDegreeC(minerDetails);
            long currentPowerTarget = getCurrentPowerTarget(minerDetails);

            int minPowerTarget = Math.toIntExact(getCurrentPowerTarget(minerDetails));
            int maxPowerTarget = Math.toIntExact(getCurrentPowerTarget(minerDetails));
            long approximatePowerUsageWatts = getApproximatePowerUsage(minerDetails);
            return new MinerStats(identity, minerName, apiStatus, currentPowerTarget, minPowerTarget, maxPowerTarget, approximatePowerUsageWatts, terahashPerSecond, temperatureInDegreeC, pools, List.of(new MinerStats.Worker(apiStatus, identity.minerModel(), "SHA256", terahashPerSecond, temperatureInDegreeC, currentPowerTarget, minPowerTarget, maxPowerTarget, approximatePowerUsageWatts, pools)));
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
                    0.0D,
                    0.0D,
                    List.of(),
                    List.of()
            );

        }
    }
}
