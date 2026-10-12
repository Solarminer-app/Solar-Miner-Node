package de.verdox.pv_miner.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.pv_miner.core.miner.MiningOS;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.twentyoneenergy.TwentyOneEnergyClient;
import de.verdox.pv_miner.core.util.LanHttp;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class MinerDiscoveryService {
    private static final Logger LOGGER = Logger.getLogger(MinerDiscoveryService.class.getName());
    private static final Duration AGENT_DISCOVERY_TIMEOUT = Duration.ofSeconds(5);

    public record DetectedMiner(MiningOS os, String model) {
    }

    private final ObjectMapper objectMapper;

    public MinerDiscoveryService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MinerDiscoveryService.DetectedMiner identifyMinerDetails(String ipv4) {
        return identifyMinerDetails(ipv4, 8084);
    }

    /** Package-private seam so tests can point the agent probe at a loopback server. */
    MinerDiscoveryService.DetectedMiner identifyMinerDetails(String ipv4, int agentPort) {
        if (ipv4 == null || ipv4.isBlank()) {
            return null;
        }

        MinerDiscoveryService.DetectedMiner agent = checkSolarMinerAgent(ipv4, agentPort);
        if (agent != null) {
            return agent;
        }

        MinerDiscoveryService.DetectedMiner heater = checkTwentyOneEnergy(ipv4);
        if (heater != null) return heater;

        MinerDiscoveryService.DetectedMiner customAsic = checkAsicMiner(ipv4);
        if (customAsic != null) {
            return customAsic;
        }

        return checkAntminerStock(ipv4);
    }

    private MinerDiscoveryService.DetectedMiner checkTwentyOneEnergy(String ipv4) {
        try {
            TwentyOneEnergyClient client = new TwentyOneEnergyClient(new MinerDetails(null, ipv4, 80, "", ""), new ObjectMapper());
            if (!client.status().operational()) return null;
            var system = client.system();
            if (system.productId() == null || system.productId().isBlank()) return null;
            return new MinerDiscoveryService.DetectedMiner(MiningOS.TWENTY_ONE_ENERGY, system.model() == null || system.model().isBlank() ? "21energy heater" : system.model());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private MinerDiscoveryService.DetectedMiner checkSolarMinerAgent(String ipv4, int agentPort) {
        try {
            LanHttp.Response response = LanHttp.get("http://" + ipv4 + ":" + agentPort + "/api/agent/external/identity",
                    (int) AGENT_DISCOVERY_TIMEOUT.toMillis(), (int) AGENT_DISCOVERY_TIMEOUT.toMillis(), Map.of());
            int status = response.statusCode();
            String body = response.body();

            if (status != 200) {
                LOGGER.log(Level.INFO, "Does not seem to be a pc-agent at: " + ipv4 + " [" + status + "]\n" + body);
                return null;
            }
            Identity identity = objectMapper.readValue(body, Identity.class);
            if (!"solarminer-pc-agent".equals(identity.kind())) return null;
            LOGGER.log(Level.INFO, "Found Solar Miner Agent on IP: " + ipv4);
            // The operator label from the agent wins over the fixed product model name.
            String label = identity.name() != null && !identity.name().isBlank() ? identity.name() : identity.model();
            return new MinerDiscoveryService.DetectedMiner(MiningOS.AGENT,
                    label == null || label.isBlank() ? "Solarminer PC Agent" : label);
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Mirrors the PC-Agent {@code Identity} record; unknown fields are ignored.
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record Identity(String kind, int powerControlProtocolVersion, String model, String name) {
    }

    private MinerDiscoveryService.DetectedMiner checkAsicMiner(String ipv4) {
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
                os = MiningOS.LUX_OS;
            }

            if (os != null) {
                String defaultModel = switch (os) {
                    case BRAIINS -> "Braiins OS - Miner";
                    case VNISH -> "Vnish - Miner";
                    case LUX_OS -> "LuxOS - Miner";
                    default -> "Unknown Miner";
                };
                String model = parseJsonValue(response, "Model", defaultModel);
                LOGGER.log(Level.INFO, "Found " + os + " Miner (" + model + ") on IP: " + ipv4);
                return new MinerDiscoveryService.DetectedMiner(os, model);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Prüft, ob ein Miner mit Antminer Stock Firmware unter der IP läuft,
     * indem der HTTP 401 Unauthorized (Digest Auth) Header provoziert wird.
     */
    private MinerDiscoveryService.DetectedMiner checkAntminerStock(String ipv4) {
        try {
            LanHttp.Response response = LanHttp.get("http://" + ipv4 + "/", 500, 500, Map.of());

            if (response.statusCode() == 401) {
                String authHeader = response.header("WWW-Authenticate");
                authHeader = authHeader == null ? "" : authHeader;

                if (authHeader.contains("antMiner")) {
                    LOGGER.log(Level.INFO, "Found Antminer (Stock Firmware) on IP: " + ipv4);
                    return new MinerDiscoveryService.DetectedMiner(MiningOS.ANTMINER_STOCK_OS, "Antminer (Stock)");
                }
            }
        } catch (Exception ignored) {
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
