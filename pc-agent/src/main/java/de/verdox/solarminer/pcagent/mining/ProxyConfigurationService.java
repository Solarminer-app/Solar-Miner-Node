package de.verdox.solarminer.pcagent.mining;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/** The only Stratum destination that locally managed miners may use. */
@Service
public class ProxyConfigurationService {
    private final Path configFile;
    private final int moneroPort;
    private final int pearlPort;
    private final int apiPort;
    private final Path modeFile;
    private final ObjectMapper mapper;
    private final ManagedProxyService managedProxy;
    private final ReferralConfigurationService referralConfigurationService;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private volatile String host;
    private volatile boolean standalone;
    private volatile boolean modeStored;
    private volatile long feeCheckedAt;
    private final java.util.concurrent.ConcurrentHashMap<String, Boolean> feeCache = new java.util.concurrent.ConcurrentHashMap<>();

    public ProxyConfigurationService(
            ObjectMapper mapper, ManagedProxyService managedProxy, ReferralConfigurationService referralConfigurationService,
            @Value("${solarminer.agent.proxy-file:./solarminer-agent/proxy-host.txt}") String configPath,
            @Value("${solarminer.agent.proxy.monero-port:3335}") int moneroPort,
            @Value("${solarminer.agent.proxy.pearl-port:3334}") int pearlPort,
            @Value("${solarminer.agent.proxy.api-port:8090}") int apiPort,
            @Value("${solarminer.agent.proxy-mode-file:./solarminer-agent/proxy-mode.txt}") String modePath,
            @Value("${solarminer.agent.standalone:false}") boolean standalone) {
        this.mapper = mapper;
        this.managedProxy = managedProxy;
        this.referralConfigurationService = referralConfigurationService;
        this.configFile = Path.of(configPath).toAbsolutePath().normalize();
        this.moneroPort = moneroPort;
        this.pearlPort = pearlPort;
        this.apiPort = apiPort;
        this.modeFile = Path.of(modePath).toAbsolutePath().normalize();
        String savedMode = readMode();
        this.modeStored = savedMode != null;
        this.standalone = savedMode == null ? standalone : "local".equals(savedMode);
        try {
            String saved = Files.readString(configFile).strip();
            if (validHost(saved)) host = saved;
        } catch (IOException ignored) { }
    }

    public void activateStoredMode() {
        managedProxy.setStandalone(standalone);
    }

    public synchronized boolean configure(String nextHost) {
        if (standalone) return false;
        if (!validHost(nextHost)) return false;
        try {
            Files.createDirectories(configFile.getParent());
            Path temp = Files.createTempFile(configFile.getParent(), "proxy-", ".tmp");
            try {
                Files.writeString(temp, nextHost);
                try {
                    Files.move(temp, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(temp, configFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
            host = nextHost;
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Persists the operator's routing choice; local mode never overwrites the saved external host. */
    public synchronized boolean setMode(String mode) {
        boolean local = "local".equals(mode);
        if (!local && !"external".equals(mode)) return false;
        if (local == standalone) {
            if (local && !managedProxy.setStandalone(true)) return false;
            if (!writeMode(local)) return false;
            modeStored = true;
            return true;
        }
        if (local && !managedProxy.setStandalone(true)) return false;
        if (!writeMode(local)) {
            if (local) managedProxy.setStandalone(false);
            return false;
        }
        standalone = local;
        modeStored = true;
        if (!local) managedProxy.setStandalone(false);
        return true;
    }

    public boolean configured() { return standalone || host != null; }
    public boolean hasStoredMode() { return modeStored; }
    public boolean standalone() { return standalone; }
    public String managedStatus() { return managedProxy.status(); }
    public String managedDetail() { return managedProxy.detail(); }
    public String host() { return standalone ? "127.0.0.1" : host; }
    public String moneroUrl() { return url(moneroPort); }
    public String pearlUrl() { return url(pearlPort); }

    private String url(int port) {
        String currentHost = host();
        return currentHost == null ? null : "stratum+tcp://" + currentHost + ":" + port;
    }

    public boolean matches(String stratumUrl, String coin) {
        String currentHost = host();
        if (currentHost == null || stratumUrl == null) return false;
        try {
            URI uri = URI.create(stratumUrl);
            int expectedPort = "monero".equals(coin) ? moneroPort : "pearl".equals(coin) ? pearlPort : -1;
            return "stratum+tcp".equals(uri.getScheme()) && currentHost.equalsIgnoreCase(uri.getHost())
                    && uri.getPort() == expectedPort && uri.getRawUserInfo() == null
                    && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
                    && uri.getRawQuery() == null && uri.getRawFragment() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isReachable() {
        String currentHost = host();
        if (currentHost == null) return false;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + currentHost + ":" + apiPort + "/api/network/ip"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && !response.body().isBlank();
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    /** The standalone miner may start only after the local proxy has a real fee target. */
    public boolean feeReady(String coin) {
        String currentHost = host();
        if (currentHost == null || !java.util.Set.of("monero", "pearl").contains(coin)) return false;
        long now = System.currentTimeMillis();
        if (now - feeCheckedAt < 3000) return feeCache.getOrDefault(coin, false);
        synchronized (this) {
            now = System.currentTimeMillis();
            if (now - feeCheckedAt < 3000) return feeCache.getOrDefault(coin, false);
            for (String name : java.util.List.of("monero", "pearl")) {
                try {
                    String referralQuery = referralConfigurationService.get().isBlank() ? "" : "?referral=" + referralConfigurationService.get();
                    HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + currentHost + ":" + apiPort
                                    + "/api/v1/fees/" + name + "/targets" + referralQuery))
                            .timeout(Duration.ofSeconds(3)).GET().build();
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    boolean ready = false;
                    if (response.statusCode() == 200) {
                        JsonNode targets = mapper.readTree(response.body());
                        if (targets.isArray()) for (JsonNode target : targets) {
                            if (target.path("percentage").asDouble() > 0
                                    && !target.path("poolAddress").asText("").isBlank()
                                    && !target.path("workerName").asText("").isBlank()) ready = true;
                        }
                    }
                    feeCache.put(name, ready);
                } catch (Exception e) {
                    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                    feeCache.put(name, false);
                }
            }
            feeCheckedAt = System.currentTimeMillis();
            return feeCache.getOrDefault(coin, false);
        }
    }

    public boolean miningReady(String coin) {
        return (!standalone || managedProxy.running()) && isReachable() && (!standalone || feeReady(coin));
    }

    private static boolean validHost(String value) {
        return value != null && value.length() <= 253 && value.matches("^[A-Za-z0-9][A-Za-z0-9.-]*$")
                && !value.endsWith(".") && !value.contains("..");
    }

    private String readMode() {
        try {
            String saved = Files.readString(modeFile).strip();
            if ("local".equals(saved) || "external".equals(saved)) return saved;
        } catch (IOException ignored) { }
        return null;
    }

    private boolean writeMode(boolean local) {
        try {
            Files.createDirectories(modeFile.getParent());
            Path temp = Files.createTempFile(modeFile.getParent(), "proxy-mode-", ".tmp");
            try {
                Files.writeString(temp, local ? "local" : "external");
                try {
                    Files.move(temp, modeFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(temp, modeFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
