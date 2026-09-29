package de.verdox.solarminer.pcagent.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import de.verdox.solarminer.pcagent.dto.MinerStats;
import de.verdox.solarminer.pcagent.dto.Pools;
import de.verdox.solarminer.pcagent.mining.MiningService;
import de.verdox.solarminer.pcagent.mining.EarningsForecastService;
import de.verdox.solarminer.pcagent.mining.ProxyConfigurationService;
import de.verdox.solarminer.pcagent.mining.ProxyDiscoveryService;
import de.verdox.solarminer.pcagent.pearl.PearlMinerService;
import de.verdox.solarminer.pcagent.pearl.LocalGpuPowerService;
import de.verdox.solarminer.pcagent.pearl.SrbDownloadService;
import de.verdox.solarminer.pcagent.xmr.XmrConfigService;
import de.verdox.solarminer.pcagent.xmr.XmrMinerService;
import de.verdox.solarminer.pcagent.xmr.download.XmrDownloadService;
import de.verdox.solarminer.pcagent.lowlevel.sensor.WindowsLhmBootstrapService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.List;

@RestController
@RequestMapping("/api/agent")
@Tag(name = "PC mining agent")
public class MiningController {
    private final MiningService miningService;
    private final XmrConfigService xmrConfigService;
    private final ProxyConfigurationService proxyConfigurationService;
    private final PearlMinerService pearlMinerService;
    private final SrbDownloadService srbDownloadService;
    private final XmrDownloadService xmrDownloadService;
    private final LocalGpuPowerService gpuPowerService;
    private final XmrMinerService xmrMinerService;
    private final WindowsLhmBootstrapService lhmBootstrapService;
    private final ProxyDiscoveryService proxyDiscoveryService;
    private final EarningsForecastService earningsForecastService;

    public MiningController(MiningService miningService, XmrConfigService xmrConfigService,
                            PearlMinerService pearlMinerService, LocalGpuPowerService gpuPowerService,
                            XmrMinerService xmrMinerService, ProxyConfigurationService proxyConfigurationService,
                            SrbDownloadService srbDownloadService, XmrDownloadService xmrDownloadService,
                            WindowsLhmBootstrapService lhmBootstrapService,
                            ProxyDiscoveryService proxyDiscoveryService,
                            EarningsForecastService earningsForecastService) {
        this.miningService = miningService;
        this.xmrConfigService = xmrConfigService;
        this.proxyConfigurationService = proxyConfigurationService;
        this.pearlMinerService = pearlMinerService;
        this.srbDownloadService = srbDownloadService;
        this.xmrDownloadService = xmrDownloadService;
        this.gpuPowerService = gpuPowerService;
        this.xmrMinerService = xmrMinerService;
        this.lhmBootstrapService = lhmBootstrapService;
        this.proxyDiscoveryService = proxyDiscoveryService;
        this.earningsForecastService = earningsForecastService;
    }

    @GetMapping("identify")
    public boolean identify() {
        return true;
    }

    @PostMapping("/setPoolConfiguration")
    public boolean setPoolConfiguration(@RequestParam String poolUrl, @RequestParam String poolUser,
                                        @RequestParam double devFeePercentage) throws java.io.IOException {
        if (!lhmBootstrapService.readyForAgent()) return false;
        if (!proxyConfigurationService.matches(poolUrl, "monero")) return false;
        xmrMinerService.hardStopMining();
        xmrConfigService.configureXmrig(XmrDownloadService.CONFIG_PATH, poolUrl, poolUser, false);
        return miningService.useMonero();
    }

    @GetMapping("/proxy")
    public ProxyOverview proxy() {
        return new ProxyOverview(proxyConfigurationService.host(), proxyConfigurationService.moneroUrl(),
                proxyConfigurationService.pearlUrl(), proxyConfigurationService.isReachable(),
                proxyConfigurationService.standalone() ? "standalone" : "external",
                proxyConfigurationService.managedStatus(), proxyConfigurationService.managedDetail(),
                proxyConfigurationService.feeReady("monero"), proxyConfigurationService.feeReady("pearl"));
    }

    @PostMapping("/proxy")
    public boolean configureProxy(@RequestParam String host) {
        if (!lhmBootstrapService.readyForAgent()) return false;
        if (host.equals(proxyConfigurationService.host())) return true;
        if (!proxyConfigurationService.configure(host)) return false;
        return miningService.pauseAll();
    }

    @PostMapping("/proxy/discover")
    public List<ProxyDiscoveryService.ProxyCandidate> discoverProxy() throws java.io.IOException {
        if (!lhmBootstrapService.readyForAgent() || proxyConfigurationService.standalone()) return List.of();
        return proxyDiscoveryService.discover();
    }

    public record ProxyOverview(String host, String moneroUrl, String pearlUrl, boolean reachable,
                                String mode, String managedStatus, String managedDetail,
                                boolean moneroFeeReady, boolean pearlFeeReady) { }

    @PostMapping("/monero/configuration")
    public boolean setMoneroConfiguration(@RequestBody MoneroConfiguration request) throws java.io.IOException {
        if (!lhmBootstrapService.readyForAgent()) return false;
        if (request == null || request.poolUrl() == null || request.wallet() == null || request.worker() == null)
            return false;
        java.net.URI pool;
        try { pool = java.net.URI.create(request.poolUrl()); }
        catch (IllegalArgumentException e) { return false; }
        if (!("stratum+tcp".equals(pool.getScheme()) || "stratum+ssl".equals(pool.getScheme()))
                || pool.getHost() == null || pool.getPort() < 1 || pool.getPort() > 65535
                || pool.getRawUserInfo() != null || (pool.getRawPath() != null && !pool.getRawPath().isEmpty())
                || pool.getRawQuery() != null || pool.getRawFragment() != null
                || !request.wallet().matches("^[1-9A-HJ-NP-Za-km-z]{95,120}$")
                || !request.worker().matches("^[A-Za-z0-9_-]{1,32}$")
                || proxyConfigurationService.moneroUrl() == null) return false;
        xmrMinerService.hardStopMining();
        String login = request.poolUrl() + ";" + request.wallet() + "." + request.worker() + ";x";
        xmrConfigService.configureXmrig(XmrDownloadService.CONFIG_PATH,
                proxyConfigurationService.moneroUrl(), login, false);
        return true;
    }

    public record MoneroConfiguration(String poolUrl, String wallet, String worker) { }

    @PostMapping("/pearl/download")
    public boolean retryPearlDownload() {
        if (!lhmBootstrapService.readyForAgent()) return false;
        return srbDownloadService.retry();
    }

    @PostMapping("/monero/download")
    public boolean installMonero() {
        return lhmBootstrapService.readyForAgent() && xmrDownloadService.retry();
    }

    @PostMapping("/pearl/configuration")
    public boolean setPearlConfiguration(@RequestBody PearlMinerService.Config configuration) throws java.io.IOException {
        if (!lhmBootstrapService.readyForAgent()) return false;
        PearlMinerService.validate(configuration);
        if (!proxyConfigurationService.matches(configuration.proxyUrl(), "pearl"))
            throw new IllegalArgumentException("SolarMiner-Proxy für Pearl fehlt oder stimmt nicht mit der gespeicherten Verbindung überein");
        pearlMinerService.configure(configuration);
        return true;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<java.util.Map<String, String>> invalidConfiguration(IllegalArgumentException exception) {
        String message = exception.getMessage() == null ? "Ungültige Konfiguration" : exception.getMessage();
        return ResponseEntity.badRequest().body(java.util.Map.of("message", message));
    }

    @GetMapping("/coin")
    public String activeCoin() {
        return miningService.activeCoin();
    }

    @GetMapping("/overview")
    public AgentOverview overview() {
        List<LocalGpuPowerService.Gpu> gpus = gpuPowerService.discover();
        MinerStats stats = miningService.getStats(gpus);
        List<EarningsForecastService.Forecast> earnings = earningsForecastService.forecasts(stats.workers());
        String active = miningService.activeCoin();
        boolean xmrConfigured = xmrConfigService.isProxyRouteConfigured();
        boolean pearlConfigured = pearlMinerService.configuration() != null
                && proxyConfigurationService.matches(pearlMinerService.configuration().proxyUrl(), "pearl");
        List<CoinOverview> coins = List.of(
                new CoinOverview("monero", "Monero", "XMR", "CPU", "RandomX",
                        xmrMinerService.getWorkerStats().miningStatus(),
                        xmrConfigured, xmrMinerService.binaryAvailable(), false),
                new CoinOverview("pearl", "Pearl", "PRL", "GPU", "PearlHash",
                        pearlMinerService.status(),
                        pearlConfigured, pearlMinerService.binaryAvailable(), true));
        return new AgentOverview(stats, active, coins, earnings, gpus, proxy(), savedMoneroConfiguration(),
                pearlMinerService.configuration(),
                new DownloadReadiness(xmrDownloadService.status(), xmrDownloadService.detail(), xmrDownloadService.progress()),
                new PearlReadiness(pearlConfigured,
                        pearlMinerService.binaryAvailable(), pearlMinerService.experimentalEnabled(),
                        srbDownloadService.status(), srbDownloadService.detail(), srbDownloadService.progress(),
                        pearlMinerService.lastError(), pearlMinerService.connectionDetail(),
                        pearlMinerService.running(), pearlMinerService.poolHealthy(), pearlMinerService.gpuStates(gpus)));
    }

    private MoneroConfiguration savedMoneroConfiguration() {
        if (!xmrConfigService.isProxyRouteConfigured()) return null;
        Pools pool = xmrConfigService.readUserPoolFromConfig();
        String[] login = pool.poolUsername().split(";", -1);
        if (login.length != 3) return null;
        int workerSeparator = login[1].lastIndexOf('.');
        if (workerSeparator < 1 || workerSeparator == login[1].length() - 1) return null;
        return new MoneroConfiguration(login[0], login[1].substring(0, workerSeparator),
                login[1].substring(workerSeparator + 1));
    }

    public record AgentOverview(MinerStats stats, String activeCoin, List<CoinOverview> coins,
                                List<EarningsForecastService.Forecast> earnings,
                                List<LocalGpuPowerService.Gpu> gpus, ProxyOverview proxy,
                                MoneroConfiguration moneroConfiguration, PearlMinerService.Config pearlConfiguration,
                                DownloadReadiness monero,
                                PearlReadiness pearl) { }

    public record DownloadReadiness(String downloadStatus, String downloadDetail, int downloadProgress) { }

    public record CoinOverview(String id, String name, String ticker, String device, String algorithm,
                               MinerStats.MinerStatus status, boolean configured, boolean binaryAvailable,
                               boolean experimental) { }

    public record PearlReadiness(boolean configured, boolean binaryAvailable, boolean experimentalEnabled,
                                 String downloadStatus, String downloadDetail, int downloadProgress,
                                 String minerError, String connectionDetail, boolean running,
                                 boolean poolHealthy, List<PearlMinerService.GpuState> gpus) { }

    @GetMapping("/earnings")
    public List<EarningsForecastService.Forecast> earnings() {
        return earningsForecastService.forecasts(miningService.getWorkerStats());
    }

    @PostMapping("/miners/{coin}/resume")
    public boolean resumeMiner(@PathVariable String coin) {
        return lhmBootstrapService.readyForAgent() && miningService.resumeMining(coin);
    }

    @PostMapping("/miners/{coin}/pause")
    public boolean pauseMiner(@PathVariable String coin) {
        return miningService.pauseMining(coin);
    }

    @PostMapping("/miners/{coin}/power-target")
    public boolean setMinerPowerTarget(@PathVariable String coin, @RequestParam long powerTarget) {
        return lhmBootstrapService.readyForAgent() && miningService.setTarget(coin, powerTarget);
    }

    @PostMapping("/pearl/gpus/{vendor}/{index}/resume")
    public boolean resumePearlGpu(@PathVariable String vendor, @PathVariable int index) {
        return lhmBootstrapService.readyForAgent() && pearlMinerService.resumeGpuManually(vendor, index);
    }

    @PostMapping("/pearl/gpus/{vendor}/{index}/pause")
    public boolean pausePearlGpu(@PathVariable String vendor, @PathVariable int index) {
        return pearlMinerService.pauseGpuManually(vendor, index);
    }

    @PostMapping("/coin")
    public boolean selectCoin(@RequestParam String coin) {
        if (!lhmBootstrapService.readyForAgent()) return false;
        return miningService.switchCoin(coin);
    }

    @PostMapping("/setPowerTarget")
    public boolean setPowerTarget(@RequestParam long powerTarget) {
        return lhmBootstrapService.readyForAgent() && miningService.setTarget(powerTarget);
    }

    @PostMapping("/increasePowerTarget")
    public boolean increasePowerTarget(@RequestParam long powerTarget) {
        return lhmBootstrapService.readyForAgent() && miningService.increasePowerTarget(powerTarget);
    }

    @PostMapping("/decreasePowerTarget")
    public boolean decreasePowerTarget(@RequestParam long powerTarget) {
        return lhmBootstrapService.readyForAgent() && miningService.decreasePowerTarget(powerTarget);
    }

    @PostMapping("/pause")
    public boolean pause() {
        return miningService.pauseAll();
    }

    @PostMapping("/resume")
    public boolean resume() {
        return lhmBootstrapService.readyForAgent() && miningService.resumeAll();
    }

    @GetMapping
    public MinerStats getMiningStats() {
        return miningService.getStats();
    }
}
