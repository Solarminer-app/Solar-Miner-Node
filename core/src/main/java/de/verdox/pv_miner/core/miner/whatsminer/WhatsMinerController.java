package de.verdox.pv_miner.core.miner.whatsminer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.pv_miner.core.miner.MinerController;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import de.verdox.pv_miner.core.miner.dto.Pools;
import de.verdox.pv_miner.core.service.DevFeeService;
import de.verdox.pv_miner.core.util.AsicMinerSpec;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WhatsMinerController implements MinerController {
    private static final Logger LOGGER = Logger.getLogger(WhatsMinerController.class.getName());
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<java.util.UUID, MinerStats> lastStatsCache = new ConcurrentHashMap<>();

    @Override
    public boolean startMining(MinerDetails details) {
        return resumeMining(details);
    }

    @Override
    public boolean stopMining(MinerDetails details) {
        return pauseMining(details);
    }

    @Override
    public boolean pauseMining(MinerDetails details) {
        JsonNode response = sendCommand(details, "pause", "");
        return isSuccess(response);
    }

    @Override
    public boolean resumeMining(MinerDetails details) {
        JsonNode response = sendCommand(details, "resume", "");
        return isSuccess(response);
    }

    @Override
    public boolean setPowerTarget(MinerDetails minerDetails, long watts) {
        JsonNode response = sendCommand(minerDetails, "set_power_limit", String.valueOf(watts));
        return isSuccess(response);
    }

    @Override
    public boolean incrementPowerTarget(MinerDetails minerDetails, long watts) {
        MinerStats currentStats = queryStats("-", minerDetails);
        if (currentStats == null) return false;
        long newTarget = currentStats.powerTargetWatts() + watts;
        return setPowerTarget(minerDetails, newTarget);
    }

    @Override
    public boolean decrementPowerTarget(MinerDetails minerDetails, long watts) {
        MinerStats currentStats = queryStats("-", minerDetails);
        if (currentStats == null) return false;
        long newTarget = Math.max(currentStats.minPowerTarget(), currentStats.powerTargetWatts() - watts);
        return setPowerTarget(minerDetails, newTarget);
    }

    @Override
    public boolean setPoolTarget(MinerDetails minerDetails, String stratumUrl, String userName) {
        String param = stratumUrl + "," + userName + ",x";
        JsonNode addResponse = sendCommand(minerDetails, "addpool", param);
        if (!isSuccess(addResponse)) return false;

        JsonNode poolsResponse = sendCommand(minerDetails, "pools", "");
        if (poolsResponse != null && poolsResponse.has("POOLS")) {
            int poolId = poolsResponse.get("POOLS").size() - 1;
            JsonNode switchResponse = sendCommand(minerDetails, "switchpool", String.valueOf(poolId));
            return isSuccess(switchResponse);
        }
        return false;
    }

    @Override
    public MinerStats queryStats(String minerName, MinerDetails minerDetails) {
        try {
            JsonNode summary = sendCommand(minerDetails, "summary", "");
            JsonNode poolsData = sendCommand(minerDetails, "pools", "");
            JsonNode devsData = sendCommand(minerDetails, "devs", "");
            JsonNode versionData = sendCommand(minerDetails, "version", "");

            if (summary == null || !summary.has("SUMMARY")) {
                return MinerStats.DEFAULT;
            }

            JsonNode summaryData = summary.get("SUMMARY").get(0);

            double mhs = summaryData.has("MHS 5s") ? summaryData.get("MHS 5s").asDouble() : 0.0;
            double ths = mhs / 1_000_000.0;

            long powerUsage = summaryData.has("Power") ? summaryData.get("Power").asLong() : 0L;
            double temp = summaryData.has("Temperature") ? summaryData.get("Temperature").asDouble() : 0.0;

            String exactModel = "Whatsminer";
            if (versionData != null && versionData.has("VERSION")) {
                JsonNode vNode = versionData.get("VERSION").get(0);
                if (vNode.has("Type")) {
                    String type = vNode.get("Type").asText();
                    exactModel = "WHATSMINER " + type.toUpperCase();
                }
            }

            AsicMinerSpec spec = AsicMinerSpec.find(exactModel);
            long defaultPower = 3000;
            long maxPower = 3500;
            long minPower = 1000;

            if (spec != null) {
                defaultPower = spec.watts();
                maxPower = (long) (spec.watts() * 1.15);
                minPower = (long) (spec.watts() * 0.45);
            } else if (powerUsage > 0) {
                defaultPower = powerUsage;
                maxPower = (long) (powerUsage * 1.15);
                minPower = (long) (powerUsage * 0.45);
            }

            List<MinerStats.Worker> workers = new ArrayList<>();
            if (devsData != null && devsData.has("DEVS")) {
                for (JsonNode dev : devsData.get("DEVS")) {
                    double devTemp = dev.has("Temperature") ? dev.get("Temperature").asDouble() : 0.0;
                    double devMhs = dev.has("MHS 5s") ? dev.get("MHS 5s").asDouble() : 0.0;
                    workers.add(new MinerStats.Worker(
                            MinerStats.MinerStatus.MINING,
                            "Board " + (dev.has("ASC") ? dev.get("ASC").asText() : "?"),
                            "SHA256",
                            devMhs / 1_000_000.0,
                            devTemp,
                            powerUsage / 3,
                            0, 0, 0,
                            powerUsage / 3,
                            List.of()
                    ));
                }
            }

            List<Pools> mappedPools = new ArrayList<>();
            if (poolsData != null && poolsData.has("POOLS")) {
                for (JsonNode pool : poolsData.get("POOLS")) {
                    mappedPools.add(new Pools(
                            pool.has("URL") ? pool.get("URL").asText() : "",
                            pool.has("User") ? pool.get("User").asText() : "",
                            ""
                    ));
                }
            }

            MinerStats.MinerIdentity identity = new MinerStats.MinerIdentity("Whatsminer-" + minerDetails.ipv4(), "", exactModel);
            MinerStats.MinerStatus status = ths > 0 ? MinerStats.MinerStatus.MINING : MinerStats.MinerStatus.PAUSED;

            MinerStats stats = new MinerStats(
                    identity,
                    minerName,
                    status,
                    powerUsage,
                    minPower,
                    defaultPower, 
                    maxPower,
                    powerUsage,
                    ths,
                    temp,
                    mappedPools,
                    workers
            );

            lastStatsCache.put(minerDetails.id(), stats);
            return stats;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Abrufen der Stats für Miner: " + minerDetails.ipv4(), e);
            return MinerStats.DEFAULT;
        }
    }

    @Override
    public MinerStats getLastData(MinerDetails minerDetails) {
        return lastStatsCache.getOrDefault(minerDetails.id(), MinerStats.DEFAULT);
    }

    @Override
    public boolean checkIfCustomCredentialsWork(MinerDetails details) {
        JsonNode response = sendCommand(details, "version", "");
        return response != null && response.has("STATUS");
    }

    @Override
    public boolean checkIfStandardCredentialsWork(MinerDetails details) {
        return checkIfCustomCredentialsWork(details);
    }

    @Override
    public boolean verifyProxyRouting(MinerDetails details, String proxyIP) {
        MinerStats stats = queryStats("-", details);
        if (stats == null || stats.pools().isEmpty()) return false;

        for (Pools pool : stats.pools()) {
            if (pool.poolUrl() != null && !pool.poolUrl().isEmpty()) {
                if (!pool.poolUrl().contains(proxyIP) || !pool.poolUsername().contains(";")) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void enforceProxyRouting(MinerDetails details, String proxyIP, String proxyPort) {
        if (verifyProxyRouting(details, proxyIP)) return;

        MinerStats stats = queryStats("-", details);
        if (stats == null || stats.pools().isEmpty()) return;

        for (Pools pool : stats.pools()) {
            if (pool.poolUrl() != null && !pool.poolUrl().isEmpty()) {

                if (!pool.poolUrl().contains(proxyIP) || !pool.poolUsername().contains(";")) {

                    String cleanTargetUrl = pool.poolUrl().replace("stratum+tcp://", "");
                    String pass = (pool.poolPassword() != null && !pool.poolPassword().isEmpty()) ? pool.poolPassword() : "x";
                    String proxyUser = cleanTargetUrl + ";" + pool.poolUsername() + ";" + pass;
                    String proxyUrl = "stratum+tcp://" + proxyIP + ":" + proxyPort;
                    setPoolTarget(details, proxyUrl, proxyUser);
                }
            }
        }
    }

    @Override
    public boolean verifyDevFeeNative(MinerDetails minerDetails, List<DevFeeService.FeeTarget> feeTargets) {
        return false;
    }

    @Override
    public void enforceDevFeeNative(MinerDetails minerDetails, List<DevFeeService.FeeTarget> feeTargets) {
        LOGGER.warning("Natives DevFee-Enforcement wird von Standard Whatsminer Firmwares über CGMiner-API nicht direkt unterstützt.");
    }

    private JsonNode sendCommand(MinerDetails details, String command, String parameter) {
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(3000);
            socket.connect(new InetSocketAddress(details.ipv4(), details.port()), 3000);

            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String payload = String.format("{\"command\":\"%s\",\"parameter\":\"%s\"}", command, parameter);
            out.print(payload);
            out.flush();

            StringBuilder responseBuilder = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                responseBuilder.append(line);
            }

            String cleanResponse = responseBuilder.toString().replace("\u0000", "");

            return objectMapper.readTree(cleanResponse);

        } catch (Exception e) {
            LOGGER.log(Level.FINE, "API Befehl fehlgeschlagen (" + command + ") an " + details.ipv4(), e);
            return null;
        }
    }

    private boolean isSuccess(JsonNode response) {
        if (response == null || !response.has("STATUS")) return false;
        JsonNode statusArray = response.get("STATUS");
        if (statusArray.isArray() && !statusArray.isEmpty()) {
            String statusCode = statusArray.get(0).get("STATUS").asText();
            return "S".equalsIgnoreCase(statusCode); // S = Success, E = Error, W = Warning
        }
        return false;
    }
}
