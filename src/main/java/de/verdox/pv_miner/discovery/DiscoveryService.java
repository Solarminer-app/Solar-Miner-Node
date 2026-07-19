package de.verdox.pv_miner.discovery;

import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.configfetcher.ConfigFetcherService;
import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MiningOS;
import de.verdox.pv_miner.pvsite.SunspecConfigService;
import de.verdox.pv_miner_extensions.device.modbus.ModbusConfigStorage;
import de.verdox.pv_miner_extensions.device.rest.RestConfigStorage;
import de.verdox.solarminer.modbustcp.ModbusConfig;
import de.verdox.solarminer.modbustcp.ModbusConfigCreatorTemplate;
import de.verdox.solarminer.modbustcp.TCPModbusClient;
import de.verdox.solarminer.rest.RestPVClient;
import de.verdox.solarminer.rest.RestPVConfig;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;

@Service
public class DiscoveryService {
    public record DiscoveredModbusDevice(String ipAddress, String matchingProfileName) {}
    public record DiscoveredRestDevice(String ipAddress, int port, String matchingProfileName, boolean requiresAuth) {}
    public record MinerInfo(String model, String ipAddress, MiningOS os) {}

    private static final Logger LOGGER = Logger.getLogger(DiscoveryService.class.getName());
    private final int[] TARGET_REST_PORTS = {80, 443, 8080, 8123};

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();
    private final MinerApiClient minerApiClient;
    private final ConfigFetcherService configFetcherService;

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
            localRestNames = restStorage.getSavedConfigs();
            for (String name : localRestNames) {
                try {
                    var config = restStorage.loadConfig(name);
                    if (config == null || config.getSections().isEmpty()) continue;

                    var firstSection = config.getSections().values().iterator().next();
                    if (firstSection.getEntries().isEmpty()) continue;

                    var testEntry = firstSection.getEntries().values().iterator().next();
                    if (executeRestProbe(baseUrl, testEntry, ip, port, name, onDeviceFound)) return;
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            LOGGER.warning("Error loading REST-Configs while scanning: " + e.getMessage());
        }

        for (var profile : configFetcherService.getCachedProfiles()) {
            if (!profile.supportedProtocols().contains("Rest-API") || localRestNames.contains(profile.name())) continue;
            var configOpt = configFetcherService.getRestPVConfig(profile.name());
            if (configOpt.isEmpty() || configOpt.get().getSections().isEmpty()) continue;

            var firstSection = configOpt.get().getSections().values().iterator().next();
            if (firstSection.getEntries().isEmpty()) continue;

            var testEntry = firstSection.getEntries().values().iterator().next();
            if (executeRestProbe(baseUrl, testEntry, ip, port, profile.name(), onDeviceFound)) return;
        }
    }

    private boolean executeRestProbe(String baseUrl, RestPVConfig.Entry<?> testEntry, String ip, int port,
                                     String profileName, Consumer<DiscoveredRestDevice> onDeviceFound) {
        String probeUrl = baseUrl + testEntry.urlExtension();
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(probeUrl)).timeout(Duration.ofMillis(800)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 200 || status == 201) {
                try (RestPVClient client = new RestPVClient(baseUrl, "")) {
                    client.read(testEntry);
                }
                onDeviceFound.accept(new DiscoveredRestDevice(ip, port, profileName, false));
                return true;
            } else if (status == 401 || status == 403) {
                onDeviceFound.accept(new DiscoveredRestDevice(ip, port, profileName, true));
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public void discoverModbusDevices(String subnetPrefix, int modbusTCPPort, Consumer<DiscoveredModbusDevice> onDeviceFound, Runnable onScanComplete) {
        CompletableFuture.runAsync(() -> {
            Semaphore rateLimiter = new Semaphore(20);
            List<String> ipsToScan = new ArrayList<>();
            ipsToScan.add("127.0.0.1");
            for (int i = 1; i <= 254; i++) ipsToScan.add(subnetPrefix + i);

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (String ip : ipsToScan) {
                    executor.submit(() -> {
                        try {
                            rateLimiter.acquire();
                            if (isPortOpen(ip, modbusTCPPort, 200)) {
                                String matchingProfile = tryMatchModbusProfiles(ip, modbusTCPPort);
                                if (matchingProfile != null) onDeviceFound.accept(new DiscoveredModbusDevice(ip, matchingProfile));
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
        } catch (Exception e) { return false; }
    }

    private String tryMatchModbusProfiles(String ip, int port) {
        String sunSpecProfileName = tryMatchSunSpec(ip, port);
        if (sunSpecProfileName != null) return sunSpecProfileName;

        ModbusConfigStorage modbusStorage = SpringContextHelper.getBean(ModbusConfigStorage.class);
        List<String> localModbusNames = new ArrayList<>();

        try {
            localModbusNames = modbusStorage.getSavedConfigs();
            for (String name : localModbusNames) {
                try {
                    var config = modbusStorage.loadConfig(name);
                    if (config == null || config.getFingerprint() == null) continue;

                    try (TCPModbusClient modbusClient = new TCPModbusClient(ip, port, 1)) {
                        if (modbusClient.verifyFingerprint(config.getAddressOffset(), config.getFingerprint())) return name;
                    } catch (Exception ignored) {}
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            LOGGER.warning("Error loading modbus configs for scanning: " + e.getMessage());
        }

        for (var profile : configFetcherService.getCachedProfiles()) {
            if (!profile.supportedProtocols().contains("Modbus-TCP") || localModbusNames.contains(profile.name())) continue;
            var configOpt = configFetcherService.getModbusConfig(profile.name());
            if (configOpt.isEmpty()) continue;

            var config = configOpt.get();
            // A readable measurement register is not a device identity. Profiles without a verified
            // expected value remain available for manual selection but must never win auto-discovery.
            if (config.getFingerprint() == null) continue;
            try (TCPModbusClient modbusClient = new TCPModbusClient(ip, port, 1)) {
                if (modbusClient.verifyFingerprint(config.getAddressOffset(), config.getFingerprint())) return profile.name();
            } catch (Exception e) {}
        }
        return null;
    }

    private String tryMatchSunSpec(String ip, int port) {
        try (TCPModbusClient client = new TCPModbusClient(ip, port, 1)) {
            int baseAddress = client.findSunSpecBaseAddress();
            if (baseAddress == -1) return null;
            int model1Address = baseAddress + 2;
            String manufacturer = client.readSunSpecString(model1Address + 2, 16).replaceAll("[^a-zA-Z0-9_]", "").trim();
            String model = client.readSunSpecString(model1Address + 18, 16).replaceAll("[^a-zA-Z0-9_]", "").trim();
            String identity = (manufacturer + "_" + model).replaceAll("^_+|_+$", "");
            String profileName = "SunSpec_" + (identity.isBlank() ? ip.replace('.', '_') : identity);

            ModbusConfigStorage modbusStorage = SpringContextHelper.getBean(ModbusConfigStorage.class);
            if (modbusStorage.doesConfigExistOnDisk(profileName)) {
                ModbusConfig existing = modbusStorage.loadConfig(profileName);
                boolean componentProfile = existing.getSections().values().stream().anyMatch(section ->
                        ModbusConfigCreatorTemplate.INVERTER.id().equals(section.getTemplateId())
                                || ModbusConfigCreatorTemplate.BATTERY.id().equals(section.getTemplateId())
                                || ModbusConfigCreatorTemplate.SMART_METER.id().equals(section.getTemplateId()));
                if (componentProfile) return profileName;
            }
            Map<Integer, Integer> blockAddresses = client.scanSunSpecBlocks(model1Address);
            SunspecConfigService sunspecService = SpringContextHelper.getBean(SunspecConfigService.class);
            ModbusConfig generatedConfig = sunspecService.createConfigFromSunSpec(blockAddresses, client);

            if (!generatedConfig.getSections().isEmpty()) {
                modbusStorage.save(profileName, generatedConfig);
                LOGGER.info("Generated SunSpec profile: " + profileName);
                return profileName;
            }
        } catch (Exception e) {
            LOGGER.warning("Error while scanning SunSpec for " + ip + ": " + e.getMessage());
        }
        return null;
    }

    /** Inspects one endpoint. SunSpec profiles are generated and persisted as part of this call. */
    public DiscoveredModbusDevice inspectModbusDevice(String host, int port) {
        if (!isPortOpen(host, port, 800)) return null;
        String profile = tryMatchModbusProfiles(host, port);
        return profile == null ? null : new DiscoveredModbusDevice(host, profile);
    }

    public DiscoveredRestDevice inspectRestDevice(String host, int port) {
        if (!isPortOpen(host, port, 800)) return null;
        AtomicReference<DiscoveredRestDevice> result = new AtomicReference<>();
        probeRestProfiles(host, port, device -> result.compareAndSet(null, device));
        return result.get();
    }

    public static String getLocalSubnetPrefix() {
        try {
            var interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) continue;
                var addresses = networkInterface.getInetAddresses();
                for (InetAddress address : Collections.list(addresses)) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        String ip = address.getHostAddress();
                        return ip.substring(0, ip.lastIndexOf('.') + 1);
                    }
                }
            }
        } catch (Exception e) {}
        return "192.168.178.";
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
}
