package de.verdox.pv_miner.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.pv_miner.core.miner.MiningOS;
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
public class MinerDiscoveryService {
    private static final Logger LOGGER = Logger.getLogger(MinerDiscoveryService.class.getName());

    public record DetectedMiner(MiningOS os, String model) {
    }

    public MinerDiscoveryService(ObjectMapper objectMapper) {
    }

    public MinerDiscoveryService.DetectedMiner identifyMinerDetails(String ipv4) {
        if (ipv4 == null || ipv4.isBlank()) {
            return null;
        }

        // 1. Check for Solar Miner Agent
        MinerDiscoveryService.DetectedMiner agent = checkSolarMinerAgent(ipv4);
        if (agent != null) {
            return agent;
        }

        // 2. Check for ASIC Miners with open CGMiner API (Custom Firmwares)
        MinerDiscoveryService.DetectedMiner customAsic = checkAsicMiner(ipv4);
        if (customAsic != null) {
            return customAsic;
        }

        // 3. Check for Antminer Stock Firmware via HTTP Challenge
        return checkAntminerStock(ipv4);
    }

    private MinerDiscoveryService.DetectedMiner checkSolarMinerAgent(String ipv4) {
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
                return new MinerDiscoveryService.DetectedMiner(MiningOS.AGENT, "Solarminer PC Agent");
            }
        } catch (Exception ignored) {
        }
        return null;
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
                os = MiningOS.LUXOS;
            }

            if (os != null) {
                String defaultModel = switch (os) {
                    case BRAIINS -> "Braiins OS - Miner";
                    case VNISH -> "Vnish - Miner";
                    case LUXOS -> "LuxOS - Miner";
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
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(500))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + ipv4 + "/"))
                    .timeout(Duration.ofMillis(500))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401) {
                String authHeader = response.headers().firstValue("WWW-Authenticate").orElse("");

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