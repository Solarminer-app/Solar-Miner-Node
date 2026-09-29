package de.verdox.solarminer.pcagent.xmr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.verdox.solarminer.pcagent.dto.Pools;
import de.verdox.solarminer.pcagent.mining.ProxyConfigurationService;
import de.verdox.solarminer.pcagent.xmr.download.XmrDownloadService;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.NetworkIF;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.net.URI;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class XmrConfigService {

    private static final Logger LOGGER = Logger.getLogger(XmrConfigService.class.getName());
    private final ObjectMapper objectMapper;
    private final ProxyConfigurationService proxyConfigurationService;

    public XmrConfigService(ObjectMapper objectMapper, ProxyConfigurationService proxyConfigurationService) {
        this.objectMapper = objectMapper;
        this.proxyConfigurationService = proxyConfigurationService;
    }

    public void configureXmrig(Path configPath, String poolUrl, String wallet, boolean useTls) throws IOException {
        if (useTls || !proxyConfigurationService.matches(poolUrl, "monero") || !validProxyLogin(wallet)) {
            throw new IllegalArgumentException("XMRig must use the configured SolarMiner Monero proxy and pool;wallet;pass login");
        }
        File configFile = configPath.toFile();
        java.nio.file.Files.createDirectories(configPath.toAbsolutePath().getParent());
        ObjectNode rootNode = objectMapper.createObjectNode();

        ObjectNode httpNode = rootNode.putObject("http");
        httpNode.put("enabled", true);
        httpNode.put("host", "127.0.0.1");
        httpNode.put("port", 1999);
        httpNode.put("access-token", "token");
        httpNode.put("restricted", true);

        rootNode.put("autosave", false);
        rootNode.put("donate-level", 0);

        rootNode.putObject("cpu").put("max-threads-hint", 100);
        rootNode.put("opencl", false);
        rootNode.put("cuda", false);

        boolean isNicehash = wallet.toLowerCase().contains("nicehash");
        String workerId = generateDeterministicWorkerId();
        ArrayNode poolsNode = rootNode.putArray("pools");
        ObjectNode poolNode = objectMapper.createObjectNode();
        poolNode.put("coin", "monero");
        poolNode.put("algo", "rx/0");
        poolNode.put("url", poolUrl);
        poolNode.put("user", wallet);
        poolNode.put("pass", "x");
        poolNode.put("rig-id", workerId);
        poolNode.put("tls", false);
        poolNode.put("keepalive", true);
        poolNode.put("nicehash", isNicehash);
        poolsNode.add(poolNode);

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile, rootNode);
        LOGGER.info("Successfully generated proxy-only XMRig config.json");
    }

    public boolean isProxyRouteConfigured() {
        try {
            JsonNode root = objectMapper.readTree(XmrDownloadService.CONFIG_PATH.toFile());
            if (root == null) return false;
            JsonNode pools = root.path("pools");
            if (!pools.isArray() || pools.size() != 1) return false;
            JsonNode pool = pools.get(0);
            return proxyConfigurationService.matches(pool.path("url").asText(), "monero")
                    && validProxyLogin(pool.path("user").asText())
                    && !pool.path("tls").asBoolean();
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean validProxyLogin(String login) {
        if (login == null) return false;
        String[] parts = login.split(";", -1);
        if (parts.length != 3 || parts[1].isBlank() || parts[2].isBlank()) return false;
        try {
            URI upstream = URI.create(parts[0].contains("://") ? parts[0] : "stratum+tcp://" + parts[0]);
            return ("stratum+tcp".equals(upstream.getScheme()) || "stratum+ssl".equals(upstream.getScheme()))
                    && upstream.getHost() != null && upstream.getPort() > 0 && upstream.getPort() <= 65535;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public void setMaxThreads(Path configPath, int maxThreads) {
        File configFile = configPath.toFile();
        if (!configFile.exists()) return;

        try {
            ObjectNode rootNode = (ObjectNode) objectMapper.readTree(configFile);

            ObjectNode cpuNode = (ObjectNode) rootNode.get("cpu");
            if (cpuNode != null) {
                cpuNode.put("max-threads-hint", calculateThreadHintPercentage(maxThreads));
            } else {
                rootNode.putObject("cpu").put("max-threads-hint", calculateThreadHintPercentage(maxThreads));
            }

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile, rootNode);
            LOGGER.info("Updated XMRig config.json with max threads: " + maxThreads);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to update max threads in config.json: " + e.getMessage(), e);
        }
    }

    private int calculateThreadHintPercentage(int desiredThreads) {
        SystemInfo systemInfo = new SystemInfo();
        int totalThreads = systemInfo.getHardware().getProcessor().getLogicalProcessorCount();
        int percentage = (int) Math.round(((double) desiredThreads / totalThreads) * 100);
        return Math.min(100, Math.max(1, percentage));
    }

    public Pools readUserPoolFromConfig() {
        File configFile = XmrDownloadService.CONFIG_PATH.toFile();

        if (!configFile.exists()) {
            LOGGER.warning("config.json not found at "+configFile.toPath().toAbsolutePath()+" for reading pools. Returning default values.");
            return new Pools("Unknown", "Unknown", "Unknown");
        }

        try {
            JsonNode rootNode = objectMapper.readTree(configFile);
            JsonNode poolsNode = rootNode.get("pools");

            if (poolsNode != null && poolsNode.isArray() && !poolsNode.isEmpty()) {
                JsonNode firstPool = poolsNode.get(0);

                String url = firstPool.path("url").asText("Unknown");
                String user = firstPool.path("user").asText("Unknown");
                String pass = firstPool.path("pass").asText("Unknown");

                return new Pools(url, user, pass);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to read pools from config.json: " + e.getMessage(), e);
        }

        return new Pools("Unknown", "Unknown", "Unknown");
    }

    private String generateDeterministicWorkerId() {
        SystemInfo systemInfo = new SystemInfo();

        String rawCpuName = systemInfo.getHardware().getProcessor().getProcessorIdentifier().getName();
        String cleanCpuName = rawCpuName
                .replaceAll("(R|TM|CPU|@.*|\\(.*?\\))", "")
                .replaceAll("[^a-zA-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");

        String macSuffix = "0000";
        List<NetworkIF> networkIFs = systemInfo.getHardware().getNetworkIFs();
        for (NetworkIF net : networkIFs) {
            String mac = net.getMacaddr();
            if (mac != null && !mac.isBlank() && !mac.equals("00:00:00:00:00:00")) {
                String cleanMac = mac.replace(":", "").toUpperCase();
                if (cleanMac.length() >= 4) {
                    macSuffix = cleanMac.substring(cleanMac.length() - 4);
                    break;
                }
            }
        }
        return cleanCpuName + "-" + macSuffix;
    }
}
