package de.verdox.pv_miner.discovery;

import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.configfetcher.ConfigFetcherService;
import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MiningOS;
import de.verdox.pv_miner_extensions.inverter.modbustcp.ModbusConfigStorage;
import de.verdox.pv_miner_extensions.inverter.rest.RestConfigStorage;
import de.verdox.solarminer.modbustcp.ModbusConfigCreatorTemplate;
import de.verdox.solarminer.modbustcp.TCPModbusClient;
import de.verdox.solarminer.rest.RestConfigCreatorTemplate;
import org.springframework.stereotype.Service;

import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.logging.Logger;

@Service
public class DiscoveryService {
    public record DiscoveredModbusDevice(String ipAddress, String matchingProfileName) {
    }

    public record DiscoveredRestDevice(String ipAddress, int port, String matchingProfileName, boolean requiresAuth) {
    }

    private static final Logger LOGGER = Logger.getLogger(DiscoveryService.class.getName());
    private final int[] TARGET_REST_PORTS = {80, 443, 8080, 8123};

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(500))
            .build();
    private final MinerApiClient minerApiClient;
    private final ConfigFetcherService configFetcherService;

    public record MinerInfo(String model, String ipAddress, MiningOS os) {
    }

    public DiscoveryService(MinerApiClient minerApiClient, ConfigFetcherService configFetcherService) {
        this.minerApiClient = minerApiClient;
        this.configFetcherService = configFetcherService;
    }

    public void discoverRestDevices(String subnetPrefix, Consumer<DiscoveredRestDevice> onDeviceFound, Runnable onScanComplete) {
        CompletableFuture.runAsync(() -> {
            Semaphore rateLimiter = new Semaphore(30);

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 1; i <= 254; i++) {
                    final String ip = subnetPrefix + i;

                    for (int port : TARGET_REST_PORTS) {
                        executor.submit(() -> {
                            try {
                                rateLimiter.acquire();

                                if (isPortOpen(ip, port, 200)) {
                                    probeRestProfiles(ip, port, onDeviceFound);
                                }
                            } catch (Exception ignored) {
                            } finally {
                                rateLimiter.release();
                            }
                        });
                    }
                }
            }
            onScanComplete.run();
        });
    }

    private void probeRestProfiles(String ip, int port, Consumer<DiscoveredRestDevice> onDeviceFound) {
        String protocol = (port == 443) ? "https://" : "http://";
        String baseUrl = protocol + ip + ":" + port;

        RestConfigStorage restStorage = SpringContextHelper.getBean(RestConfigStorage.class);
        List<String> localRestNames = new ArrayList<>();

        try {
            var pvSiteTemplate = RestConfigCreatorTemplate.HOME_ASSISTANT_PV;
            localRestNames = restStorage.getSavedConfigs(pvSiteTemplate);

            for (String name : localRestNames) {
                try {
                    var config = restStorage.loadConfig(pvSiteTemplate, name);
                    if (config == null || config.getConfigEntries().isEmpty()) continue;

                    var testEntry = config.getConfigEntries().values().iterator().next();
                    String probeUrl = baseUrl + testEntry.urlExtension();

                    if (executeRestProbe(probeUrl, ip, port, name, onDeviceFound)) {
                        return;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error loading REST-Configs while scanning: " + e.getMessage());
        }

        for (var profile : configFetcherService.getCachedProfiles()) {
            if (!profile.supportedProtocols().contains("Rest-API")) {
                continue;
            }
            if (localRestNames.contains(profile.name())) {
                continue;
            }

            var configOpt = configFetcherService.getRestPVConfig(profile.name());
            if (configOpt.isEmpty() || configOpt.get().getConfigEntries().isEmpty()) {
                continue;
            }

            var testEntry = configOpt.get().getConfigEntries().values().iterator().next();
            String probeUrl = baseUrl + testEntry.urlExtension();

            if (executeRestProbe(probeUrl, ip, port, profile.name(), onDeviceFound)) {
                return;
            }
        }
    }

    private boolean executeRestProbe(String probeUrl, String ip, int port, String profileName, Consumer<DiscoveredRestDevice> onDeviceFound) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(probeUrl))
                    .timeout(Duration.ofMillis(800))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status == 200 || status == 201) {
                onDeviceFound.accept(new DiscoveredRestDevice(ip, port, profileName, false));
                return true;
            } else if (status == 401 || status == 403) {
                onDeviceFound.accept(new DiscoveredRestDevice(ip, port, profileName, true));
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public void discoverMiners(String subnetPrefix, Consumer<MinerInfo> onMinerFound, Runnable onScanComplete) {
        CompletableFuture.runAsync(() -> {
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 1; i <= 254; i++) {
                    final String ip = subnetPrefix + i;
                    executor.submit(() -> {
                        try {
                            var detectedMiner = minerApiClient.identifyMiningOS(ip);

                            if (detectedMiner != null) {
                                onMinerFound.accept(new MinerInfo(detectedMiner.model(), ip, detectedMiner.os()));
                            }
                        } catch (Exception ignored) {
                        }
                    });
                }
            }
            onScanComplete.run();
        });
    }

    public void discoverModbusDevices(String subnetPrefix, int modbusTCPPort, Consumer<DiscoveredModbusDevice> onDeviceFound, Runnable onScanComplete) {
        CompletableFuture.runAsync(() -> {
            Semaphore rateLimiter = new Semaphore(20);

            List<String> ipsToScan = new ArrayList<>();
            ipsToScan.add("127.0.0.1");
            for (int i = 1; i <= 254; i++) {
                ipsToScan.add(subnetPrefix + i);
            }

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (String ip : ipsToScan) {
                    executor.submit(() -> {
                        try {
                            rateLimiter.acquire();

                            if (isPortOpen(ip, modbusTCPPort, 200)) {

                                String matchingProfile = tryMatchModbusProfiles(ip, modbusTCPPort);
                                if (matchingProfile != null) {
                                    onDeviceFound.accept(new DiscoveredModbusDevice(ip, matchingProfile));
                                }
                            }
                        } catch (Exception ignored) {
                        } finally {
                            rateLimiter.release();
                        }
                    });
                }
            }
            onScanComplete.run();
        });
    }

    private boolean isPortOpen(String ip, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String tryMatchModbusProfiles(String ip, int port) {
        String sunSpecProfileName = tryMatchSunSpec(ip, port);
        if (sunSpecProfileName != null) {
            return sunSpecProfileName;
        }

        ModbusConfigStorage modbusStorage = SpringContextHelper.getBean(ModbusConfigStorage.class);
        List<String> localModbusNames = new ArrayList<>();

        try {
            var pvSiteTemplate = ModbusConfigCreatorTemplate.PV_SITE;
            localModbusNames = modbusStorage.getSavedConfigs(pvSiteTemplate);

            for (String name : localModbusNames) {
                try {
                    var config = modbusStorage.loadConfig(pvSiteTemplate, name);
                    if (config == null) continue;

                    try (TCPModbusClient modbusClient = new TCPModbusClient(ip, port, 1)) {
                        if (config.getFingerprint() != null) {
                            if (modbusClient.verifyFingerprint(config.getFingerprint())) {
                                return name;
                            }
                        } else if (!config.getConfigEntries().isEmpty()) {
                            var testEntry = config.getConfigEntries().values().iterator().next();
                            modbusClient.read(testEntry);
                            return name;
                        }
                    } catch (Exception ignored) {
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error loading modbus configs for scanning: " + e.getMessage());
        }

        for (var profile : configFetcherService.getCachedProfiles()) {
            if (!profile.supportedProtocols().contains("Modbus-TCP")) {
                continue;
            }
            if (localModbusNames.contains(profile.name())) {
                continue;
            }

            var configOpt = configFetcherService.getModbusConfig(profile.name());
            if (configOpt.isEmpty()) {
                continue;
            }

            var config = configOpt.get();

            try (TCPModbusClient modbusClient = new TCPModbusClient(ip, 502, 1)) {
                if (config.getFingerprint() != null) {
                    if (modbusClient.verifyFingerprint(config.getFingerprint())) {
                        return profile.name();
                    }
                    continue;
                }

                if (!config.getConfigEntries().isEmpty()) {
                    var testEntry = config.getConfigEntries().values().iterator().next();
                    modbusClient.read(testEntry);
                    return profile.name();
                }
            } catch (Exception e) {

            }
        }
        return null;
    }

    private String tryMatchSunSpec(String ip, int port) {
        try (TCPModbusClient client = new TCPModbusClient(ip, port, 1)) {

            int baseAddress = client.findSunSpecBaseAddress();
            if (baseAddress == -1) {
                return null;
            }

            int model1Address = baseAddress + 2;

            String manufacturer = client.readSunSpecString(model1Address + 2, 16).replaceAll("[^a-zA-Z0-9_]", "").trim();
            String model = client.readSunSpecString(model1Address + 18, 16).replaceAll("[^a-zA-Z0-9_]", "").trim();
            String profileName = "SunSpec_" + manufacturer + "_" + model;

            ModbusConfigStorage modbusStorage = SpringContextHelper.getBean(ModbusConfigStorage.class);
            if (modbusStorage.doesConfigExistOnDisk(ModbusConfigCreatorTemplate.PV_SITE, profileName)) {
                return profileName;
            }

            Map<Integer, Integer> blockAddresses = client.scanSunSpecBlocks(model1Address);

            SunspecConfigService sunspecService = SpringContextHelper.getBean(SunspecConfigService.class);
            ModbusConfig generatedConfig = sunspecService.createConfigFromSunSpec(blockAddresses, client);

            if (!generatedConfig.getConfigEntries().isEmpty()) {
                modbusStorage.save(ModbusConfigCreatorTemplate.PV_SITE, profileName, generatedConfig);
                LOGGER.info("Generated SunSpec profile: " + profileName);
                return profileName;
            } else {
                LOGGER.warning("SunSpec is compatible but does not provide the expected models.");
            }

        } catch (Exception e) {
            LOGGER.warning("Error while scanning SunSpec for " + ip + ": " + e.getMessage());
        }
        return null;
    }

    public static String getLocalSubnetPrefix() {
        try {
            var interfaces = NetworkInterface.getNetworkInterfaces();

            for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }

                var addresses = networkInterface.getInetAddresses();
                for (InetAddress address : Collections.list(addresses)) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        String ip = address.getHostAddress();
                        return ip.substring(0, ip.lastIndexOf('.') + 1);
                    }
                }
            }
        } catch (Exception e) {
        }
        return "192.168.178.";
    }
}