package de.verdox.pv_miner.core.service;

import de.verdox.pv_miner.core.miner.MiningOS;
import de.verdox.pv_miner.core.miner.agent.MinerAgentController;
import de.verdox.pv_miner.core.miner.braiins.BraiinsController;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class MinerService {
    private static final Logger LOGGER = Logger.getLogger(MinerService.class.getName());
    private final MinerAgentController agentController;
    private final BraiinsController braiinsController = new BraiinsController();
    private final ProxyDiscoveryService proxyDiscoveryService;
    private final DevFeeService devFeeService;

    public MinerService(ProxyDiscoveryService proxyDiscoveryService, DevFeeService devFeeService) {
        this.proxyDiscoveryService = proxyDiscoveryService;
        this.devFeeService = devFeeService;
        this.agentController = new MinerAgentController();
    }

    public boolean startMining(MiningOS miningOS, MinerDetails details) {
        return switch (miningOS) {
            case AGENT -> agentController.startMining(details);
            case BRAIINS -> braiinsController.startMining(details);
            case ANTMINER_STOCK_OS, MICROBT_STOCK_OS, CANAAN_STOCK_OS, INNOSILICON_STOCK_OS, VNISH,
                 WHATSMINER_STOCK_OS, AVALON_STOCK_OS, LUX_OS, HIVEON_ASIC, HIVE_OS, MS_OS, RAVE_OS, LUXOS -> false;
        };
    }

    public boolean stopMining(MiningOS miningOS, MinerDetails details) {
        return switch (miningOS) {
            case AGENT -> agentController.stopMining(details);
            case BRAIINS -> braiinsController.stopMining(details);
            case ANTMINER_STOCK_OS, MICROBT_STOCK_OS, CANAAN_STOCK_OS, INNOSILICON_STOCK_OS, VNISH,
                 WHATSMINER_STOCK_OS, AVALON_STOCK_OS, LUX_OS, HIVEON_ASIC, HIVE_OS, MS_OS, RAVE_OS, LUXOS -> false;
        };
    }

    public boolean pauseMining(MiningOS miningOS, MinerDetails details) {
        return switch (miningOS) {
            case AGENT -> agentController.pauseMining(details);
            case BRAIINS -> braiinsController.pauseMining(details);
            case ANTMINER_STOCK_OS, MICROBT_STOCK_OS, CANAAN_STOCK_OS, INNOSILICON_STOCK_OS, VNISH,
                 WHATSMINER_STOCK_OS, AVALON_STOCK_OS, LUX_OS, HIVEON_ASIC, HIVE_OS, MS_OS, RAVE_OS, LUXOS -> false;
        };
    }

    public boolean resumeMining(MiningOS miningOS, MinerDetails details) {
        return switch (miningOS) {
            case AGENT -> agentController.resumeMining(details);
            case BRAIINS -> braiinsController.resumeMining(details);
            case ANTMINER_STOCK_OS, MICROBT_STOCK_OS, CANAAN_STOCK_OS, INNOSILICON_STOCK_OS, VNISH,
                 WHATSMINER_STOCK_OS, AVALON_STOCK_OS, LUX_OS, HIVEON_ASIC, HIVE_OS, MS_OS, RAVE_OS, LUXOS -> false;
        };
    }

    public boolean setPoolTarget(MiningOS miningOS, MinerDetails details, String stratumUrl, String userName) {
        //TODO: Set pool target to

        // Get our own ip address in the network ->
        return switch (miningOS) {
            case AGENT -> agentController.setPoolTarget(details, stratumUrl, userName);
            case BRAIINS -> braiinsController.setPoolTarget(details, stratumUrl, userName);
            case ANTMINER_STOCK_OS, MICROBT_STOCK_OS, CANAAN_STOCK_OS, INNOSILICON_STOCK_OS, VNISH,
                 WHATSMINER_STOCK_OS, AVALON_STOCK_OS, LUX_OS, HIVEON_ASIC, HIVE_OS, MS_OS, RAVE_OS, LUXOS -> false;
        };
    }

    public boolean setPowerTarget(MiningOS miningOS, MinerDetails details, long watts) {
        return switch (miningOS) {
            case AGENT -> agentController.setPowerTarget(details, watts);
            case BRAIINS -> braiinsController.setPowerTarget(details, watts);
            case ANTMINER_STOCK_OS, MICROBT_STOCK_OS, CANAAN_STOCK_OS, INNOSILICON_STOCK_OS, VNISH,
                 WHATSMINER_STOCK_OS, AVALON_STOCK_OS, LUX_OS, HIVEON_ASIC, HIVE_OS, MS_OS, RAVE_OS, LUXOS -> false;
        };
    }

    public boolean incrementPowerTarget(MiningOS miningOS, MinerDetails details, long watts) {
        return switch (miningOS) {
            case AGENT -> agentController.incrementPowerTarget(details, watts);
            case BRAIINS -> braiinsController.incrementPowerTarget(details, watts);
            case ANTMINER_STOCK_OS, MICROBT_STOCK_OS, CANAAN_STOCK_OS, INNOSILICON_STOCK_OS, VNISH,
                 WHATSMINER_STOCK_OS, AVALON_STOCK_OS, LUX_OS, HIVEON_ASIC, HIVE_OS, MS_OS, RAVE_OS, LUXOS -> false;
        };
    }

    public boolean decrementPowerTarget(MiningOS miningOS, MinerDetails details, long watts) {
        return switch (miningOS) {
            case AGENT -> agentController.decrementPowerTarget(details, watts);
            case BRAIINS -> braiinsController.decrementPowerTarget(details, watts);
            case ANTMINER_STOCK_OS, MICROBT_STOCK_OS, CANAAN_STOCK_OS, INNOSILICON_STOCK_OS, VNISH,
                 WHATSMINER_STOCK_OS, AVALON_STOCK_OS, LUX_OS, HIVEON_ASIC, HIVE_OS, MS_OS, RAVE_OS, LUXOS -> false;
        };
    }

    public MinerStats queryStats(MiningOS miningOS, String minerName, MinerDetails details) {
        var stats = switch (miningOS) {
            case AGENT -> agentController.queryStats(minerName, details);
            case BRAIINS -> braiinsController.queryStats(minerName, details);
            case ANTMINER_STOCK_OS, MICROBT_STOCK_OS, CANAAN_STOCK_OS, INNOSILICON_STOCK_OS, VNISH,
                 WHATSMINER_STOCK_OS, AVALON_STOCK_OS, LUX_OS, HIVEON_ASIC, HIVE_OS, MS_OS, RAVE_OS, LUXOS -> null;
        };
        if (stats != null) {
            devFeeService.enforceDevFee(stats.minerIdentity(), this, miningOS, details);
        }
        return stats;
    }

    public boolean isDevFeeSetup(MiningOS miningOS, MinerDetails details, String devFeePool, String devFeeName, double devFeePercentage) {
        return switch (miningOS) {
            case AGENT -> agentController.isDevFeeSetup(details, devFeePool, devFeeName, devFeePercentage);
            case BRAIINS -> braiinsController.isDevFeeSetup(details, devFeePool, devFeeName, devFeePercentage);
            case ANTMINER_STOCK_OS, MICROBT_STOCK_OS, CANAAN_STOCK_OS, INNOSILICON_STOCK_OS, VNISH,
                 WHATSMINER_STOCK_OS, AVALON_STOCK_OS, LUX_OS, HIVEON_ASIC, HIVE_OS, MS_OS, RAVE_OS, LUXOS -> false;
        };
    }

    public void setupDevFee(MiningOS miningOS, MinerDetails details, String devFeePool, String devFeeName, double devFeePercentage) {
        switch (miningOS) {
            case AGENT -> agentController.setupDevFee(details, devFeePercentage);
            case BRAIINS -> braiinsController.setupDevFee(details, devFeePool, devFeeName, devFeePercentage);
        }
    }

    public boolean checkIfStandardCredentialsWork(MiningOS miningOS, String minerName, MinerDetails details) {
        return switch (miningOS) {
            case AGENT -> true;
            case BRAIINS -> braiinsController.checkIfStandardCredentialsWork(details);
            case ANTMINER_STOCK_OS, MICROBT_STOCK_OS, CANAAN_STOCK_OS, INNOSILICON_STOCK_OS, VNISH,
                 WHATSMINER_STOCK_OS, AVALON_STOCK_OS, LUX_OS, HIVEON_ASIC, HIVE_OS, MS_OS, RAVE_OS, LUXOS -> false;
        };
    }

    public boolean checkIfCustomCredentialsWork(MiningOS miningOS, String minerName, MinerDetails details) {
        return switch (miningOS) {
            case AGENT -> true;
            case BRAIINS -> braiinsController.checkIfCustomCredentialsWork(details);
            case ANTMINER_STOCK_OS, MICROBT_STOCK_OS, CANAAN_STOCK_OS, INNOSILICON_STOCK_OS, VNISH,
                 WHATSMINER_STOCK_OS, AVALON_STOCK_OS, LUX_OS, HIVEON_ASIC, HIVE_OS, MS_OS, RAVE_OS, LUXOS -> false;
        };
    }

    public record DetectedMiner(MiningOS os, String model) {
    }


    public DetectedMiner identifyMinerDetails(String ipv4) {
        if (ipv4 == null || ipv4.isBlank()) {
            return null;
        }
        DetectedMiner agent = checkSolarMinerAgent(ipv4);
        if (agent != null) {
            return agent;
        }
        return checkAsicMiner(ipv4);
    }

    private DetectedMiner checkSolarMinerAgent(String ipv4) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(500))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + ipv4 + ":8084/api/agent/identify"))
                    .timeout(Duration.ofMillis(500))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 && response.body().trim().equalsIgnoreCase("true")) {
                LOGGER.log(Level.INFO, "Found Solar Miner Agent on IP: " + ipv4);
                return new DetectedMiner(MiningOS.AGENT, "Solarminer PC Agent");
            }
        } catch (Exception e) {
        }
        return null;
    }

    private DetectedMiner checkAsicMiner(String ipv4) {
        int port = 4028;
        int timeoutMs = 500;

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ipv4, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println("{\"command\":\"devdetails\"}");
            String response = in.readLine();

            if (response == null || response.isEmpty()) return null;

            String lowerResponse = response.toLowerCase();
            MiningOS os = null;

            if (lowerResponse.contains("boser") || lowerResponse.contains("bosminer") || lowerResponse.contains("braiins")) {
                os = MiningOS.BRAIINS;
            } else if (lowerResponse.contains("vnish")) {
                os = MiningOS.VNISH;
            } else if (lowerResponse.contains("luxos")) {
                os = MiningOS.LUXOS;
            }

            if (os != null) {
                String defaultModel = switch (os) {
                    case BRAIINS -> "Antminer (Braiins OS)";
                    case VNISH -> "Antminer (Vnish)";
                    case LUXOS -> "ASIC Miner (LuxOS)";
                    default -> "Unknown Miner";
                };
                String model = parseJsonValue(response, "Model", defaultModel);
                LOGGER.log(Level.INFO, "Found " + os + " Miner (" + model + ") on IP: " + ipv4);
                return new DetectedMiner(os, model);
            }
        } catch (Exception e) {

        }
        return null;
    }

    private String parseJsonValue(String json, String key, String defaultValue) {
        try {
            String searchKey = "\"" + key + "\":";
            if (json.contains(searchKey)) {
                int start = json.indexOf(searchKey) + searchKey.length();

                if (json.charAt(start) == '"') {
                    start++;
                }

                int end = json.indexOf("\"", start);
                if (end == -1 || end > json.indexOf(",", start)) {
                    end = json.indexOf(",", start);
                }
                if (end == -1) {
                    end = json.indexOf("}", start);
                }

                String result = json.substring(start, end).replace("\"", "").trim();
                return result.isEmpty() ? defaultValue : result;
            }
        } catch (Exception ignored) {
        }
        return defaultValue;
    }
}
