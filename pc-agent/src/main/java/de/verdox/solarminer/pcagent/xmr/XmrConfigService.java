package de.verdox.solarminer.pcagent.xmr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.verdox.solarminer.pcagent.dto.Pools;
import de.verdox.solarminer.pcagent.xmr.download.XmrDownloadService;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.NetworkIF;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class XmrConfigService {

    private static final Logger LOGGER = Logger.getLogger(XmrConfigService.class.getName());
    private final ObjectMapper objectMapper;

    public XmrConfigService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void configureXmrig(Path configPath, String poolUrl, String wallet, boolean useTls) {
        File configFile = configPath.toFile();

        try {
            ObjectNode rootNode = objectMapper.createObjectNode();

            ObjectNode httpNode = rootNode.putObject("http");
            httpNode.put("enabled", true);
            httpNode.put("host", "0.0.0.0");
            httpNode.put("port", 1999);
            httpNode.put("access-token", "token");
            httpNode.put("restricted", false);

            rootNode.put("autosave", true);
            rootNode.put("donate-level", 0);

            ObjectNode cpuNode = rootNode.putObject("cpu");
            cpuNode.put("max-threads-hint", 100);

            rootNode.put("opencl", false);
            rootNode.put("cuda", false);

            // NiceHash Erkennung
            boolean isNicehash = poolUrl != null && poolUrl.toLowerCase().contains("nicehash");

            String workerId = generateDeterministicWorkerId();
            LOGGER.info("Generated deterministic Worker-ID: " + workerId);

            ArrayNode poolsNode = rootNode.putArray("pools");
            ObjectNode poolNode = objectMapper.createObjectNode();
            poolNode.put("coin", "monero");
            poolNode.put("algo", "rx/0");
            poolNode.put("url", poolUrl);
            poolNode.put("user", wallet);
            poolNode.put("pass", "x");
            poolNode.put("rig-id", workerId);
            poolNode.put("tls", useTls);
            poolNode.put("keepalive", true);
            poolNode.put("nicehash", isNicehash);

            poolsNode.add(poolNode);

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile, rootNode);
            LOGGER.info("Successfully generated new XMRig config.json from scratch for wallet: " + wallet + " (NiceHash: " + isNicehash + ")");

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to generate config.json: " + e.getMessage(), e);
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