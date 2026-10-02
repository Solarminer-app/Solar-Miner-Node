package de.verdox.solarminer.pcagent.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import de.verdox.solarminer.pcagent.dto.MinerStats;
import de.verdox.solarminer.pcagent.dto.Pools;
import de.verdox.solarminer.pcagent.mining.MiningService;
import de.verdox.solarminer.pcagent.mining.EarningsForecastService;
import de.verdox.solarminer.pcagent.mining.PayoutDefaultsService;
import de.verdox.solarminer.pcagent.mining.ReferralConfigurationService;
import de.verdox.solarminer.pcagent.mining.FeeTransparencyService;
import de.verdox.solarminer.pcagent.mining.WalletBalanceService;
import de.verdox.solarminer.pcagent.mining.WindowsDefenderExclusionService;
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
    private final PayoutDefaultsService payoutDefaultsService;
    private final ReferralConfigurationService referralConfigurationService;
    private final FeeTransparencyService feeTransparencyService;
    private final WalletBalanceService walletBalanceService;
    private final WindowsDefenderExclusionService defenderExclusionService;

    public MiningController(MiningService miningService, XmrConfigService xmrConfigService,
                            PearlMinerService pearlMinerService, LocalGpuPowerService gpuPowerService,
                            XmrMinerService xmrMinerService, ProxyConfigurationService proxyConfigurationService,
                            SrbDownloadService srbDownloadService, XmrDownloadService xmrDownloadService,
                            WindowsLhmBootstrapService lhmBootstrapService,
                            ProxyDiscoveryService proxyDiscoveryService,
                            EarningsForecastService earningsForecastService,
                            PayoutDefaultsService payoutDefaultsService,
                            ReferralConfigurationService referralConfigurationService,
                            FeeTransparencyService feeTransparencyService,
                            WalletBalanceService walletBalanceService,
                            WindowsDefenderExclusionService defenderExclusionService) {
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
        this.payoutDefaultsService = payoutDefaultsService;
        this.referralConfigurationService = referralConfigurationService;
        this.feeTransparencyService = feeTransparencyService;
        this.walletBalanceService = walletBalanceService;
        this.defenderExclusionService = defenderExclusionService;
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

    /** The node may set this through the LAN API; local edits are intentionally allowed but not authoritative. */
    @GetMapping("/referral")
    public ReferralOverview referral() { return new ReferralOverview(referralConfigurationService.get()); }

    @PostMapping("/referral")
    public boolean setReferral(@RequestParam String key) {
        boolean updated = referralConfigurationService.set(key);
        if (updated) payoutDefaultsService.invalidate();
        return updated;
    }

    @GetMapping("/fees")
    public List<FeeTransparencyService.FeeOverview> fees() { return feeTransparencyService.overview(); }

    public record ReferralOverview(String key) { }

    @PostMapping("/proxy")
    public boolean configureProxy(@RequestParam String host) {
        if (!lhmBootstrapService.readyForAgent()) return false;
        if (host.equals(proxyConfigurationService.host())) return true;
        if (!proxyConfigurationService.configure(host)) return false;
        payoutDefaultsService.invalidate();
        return miningService.pauseAll();
    }

    @PostMapping("/proxy/mode")
    public boolean configureProxyMode(@RequestParam String mode) {
        if (!lhmBootstrapService.readyForAgent()) return false;
        if (!"local".equals(mode) && !"external".equals(mode)) return false;
        if (!miningService.pauseAll()) return false;
        if (!proxyConfigurationService.setMode(mode)) return false;
        payoutDefaultsService.invalidate();
        return true;
    }

    @PostMapping("/proxy/discover")
    public List<ProxyDiscoveryService.ProxyCandidate> discoverProxy() throws java.io.IOException {
        if (!lhmBootstrapService.readyForAgent()) return List.of();
        return proxyDiscoveryService.discover();
    }

    public record ProxyOverview(String host, String moneroUrl, String pearlUrl, boolean reachable,
                                String mode, String managedStatus, String managedDetail,
                                boolean moneroFeeReady, boolean pearlFeeReady) { }

    @PostMapping("/monero/configuration")
    public boolean setMoneroConfiguration(@RequestBody MoneroConfiguration request) throws java.io.IOException {
        if (request == null || request.worker() == null
                || !request.worker().matches("^[A-Za-z0-9_-]{1,32}$")
                || proxyConfigurationService.moneroUrl() == null) return false;
        String login;
        if (request.wallet() == null || request.wallet().isBlank()) {
            // Without an own payout address the fee-backend decides pool and wallet together,
            // so the local route ends up exactly on the house target the proxy fees towards.
            PayoutDefaultsService.DefaultPayout payout = payoutDefaultsService.resolve("monero").orElse(null);
            if (payout == null) return false;
            login = payout.poolUrl() + ";" + payout.login() + ";x";
            payoutDefaultsService.markDefault("monero", true);
        } else {
            if (request.poolUrl() == null) return false;
            java.net.URI pool;
            try { pool = java.net.URI.create(request.poolUrl()); }
            catch (IllegalArgumentException e) { return false; }
            if (!("stratum+tcp".equals(pool.getScheme()) || "stratum+ssl".equals(pool.getScheme()))
                    || pool.getHost() == null || pool.getPort() < 1 || pool.getPort() > 65535
                    || pool.getRawUserInfo() != null || (pool.getRawPath() != null && !pool.getRawPath().isEmpty())
                    || pool.getRawQuery() != null || pool.getRawFragment() != null
                    || !request.wallet().matches("^[1-9A-HJ-NP-Za-km-z]{95,120}$")) return false;
            login = request.poolUrl() + ";" + request.wallet() + "." + request.worker() + ";x";
            payoutDefaultsService.markDefault("monero", false);
        }
        xmrMinerService.hardStopMining();
        xmrConfigService.configureXmrig(XmrDownloadService.CONFIG_PATH,
                proxyConfigurationService.moneroUrl(), login, false);
        return true;
    }

    public record MoneroConfiguration(String poolUrl, String wallet, String worker) { }

    @PostMapping("/pearl/download")
    public boolean retryPearlDownload() {
        return srbDownloadService.retry();
    }

    @PostMapping("/monero/download")
    public boolean installMonero() {
        return xmrDownloadService.retry();
    }

    @PostMapping("/{coin}/defender-exclusion")
    public ResponseEntity<DefenderExclusionResult> addDefenderExclusion(
            @PathVariable String coin, jakarta.servlet.http.HttpServletRequest request) {
        try {
            if (!java.net.InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress()
                    || !isLocalUiRequest(request))
                return ResponseEntity.status(403).body(new DefenderExclusionResult(false,
                        "Die Defender-Ausnahme muss von der lokalen PC-Agent-Seite angefordert werden."));
            if (!defenderExclusionService.isWindows())
                return ResponseEntity.status(400).body(new DefenderExclusionResult(false,
                        "Diese Funktion ist nur unter Windows verfügbar."));
            java.nio.file.Path directory = switch (coin) {
                case "monero" -> xmrDownloadService.installDirectory();
                case "pearl" -> srbDownloadService.installDirectory();
                default -> null;
            };
            if (directory == null) return ResponseEntity.status(404)
                    .body(new DefenderExclusionResult(false, "Unbekannter Miner."));
            String downloadStatus = "monero".equals(coin)
                    ? xmrDownloadService.status() : srbDownloadService.status();
            if (!"BLOCKED_BY_ANTIVIRUS".equals(downloadStatus))
                return ResponseEntity.status(409).body(new DefenderExclusionResult(false,
                        "Eine Defender-Ausnahme ist nur nach einer erkannten Blockierung verfügbar."));
            defenderExclusionService.addMinerDirectory(coin, directory);
            return ResponseEntity.ok(new DefenderExclusionResult(true,
                    "Windows Defender hat die Ausnahme für " + directory + " bestätigt."));
        } catch (Exception failure) {
            String detail = failure.getMessage() == null ? "Unbekannter Windows-Fehler." : failure.getMessage();
            return ResponseEntity.status(400).body(new DefenderExclusionResult(false, detail));
        }
    }

    public record DefenderExclusionResult(boolean success, String message) { }

    private boolean isLocalUiRequest(jakarta.servlet.http.HttpServletRequest request) {
        String serverName = request.getServerName().toLowerCase(java.util.Locale.ROOT);
        if (!(serverName.equals("localhost") || serverName.equals("127.0.0.1") || serverName.equals("::1")))
            return false;
        String origin = request.getHeader("Origin");
        if (origin == null) return false;
        try {
            java.net.URI uri = java.net.URI.create(origin);
            String originHost = uri.getHost();
            return originHost != null && (originHost.equalsIgnoreCase("localhost")
                    || originHost.equals("127.0.0.1") || originHost.equals("::1"))
                    && uri.getPort() == request.getServerPort()
                    && uri.getScheme().equalsIgnoreCase(request.getScheme());
        } catch (IllegalArgumentException invalidOrigin) {
            return false;
        }
    }

    @PostMapping("/pearl/remove")
    public boolean removePearlMiner() {
        if ("DOWNLOADING".equals(srbDownloadService.status()) || !pearlMinerService.stop()) return false;
        return srbDownloadService.remove();
    }

    @PostMapping("/monero/remove")
    public boolean removeMoneroMiner() {
        if ("DOWNLOADING".equals(xmrDownloadService.status()) || !miningService.pauseMining("monero")) return false;
        return xmrDownloadService.remove();
    }

    @PostMapping("/pearl/configuration")
    public boolean setPearlConfiguration(@RequestBody PearlMinerService.Config configuration) throws java.io.IOException {
        boolean feeBackendPayout = configuration == null
                || configuration.wallet() == null || configuration.wallet().isBlank();
        PearlMinerService.Config effective = feeBackendPayout
                ? withFeeBackendPayout(configuration) : configuration;
        PearlMinerService.validate(effective);
        if (!proxyConfigurationService.matches(effective.proxyUrl(), "pearl"))
            throw new IllegalArgumentException("SolarMiner-Proxy für Pearl fehlt oder stimmt nicht mit der gespeicherten Verbindung überein");
        pearlMinerService.configure(effective);
        payoutDefaultsService.markDefault("pearl", feeBackendPayout);
        return true;
    }

    /**
     * An empty wallet means the fee-backend payout is used. Its pool and worker always belong
     * together, so a half-entered own route is never mixed with the house wallet.
     */
    private PearlMinerService.Config withFeeBackendPayout(PearlMinerService.Config request) {
        if (request == null) throw new IllegalArgumentException("Pearl-Konfiguration fehlt");
        PayoutDefaultsService.DefaultPayout payout = payoutDefaultsService.resolve("pearl").orElseThrow(
                () -> new IllegalArgumentException("Kein SolarMiner-Standard-Auszahlungsziel für Pearl erreichbar"));
        String worker = payout.workerPart() != null ? payout.workerPart()
                : request.worker() == null || request.worker().isBlank() ? "solarminer" : request.worker();
        return new PearlMinerService.Config(payout.poolUrl(), request.proxyUrl(), payout.walletPart(),
                worker, request.devices());
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
        xmrMinerService.ensureDefaultConfiguration();
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
                        pearlConfigured, pearlMinerService.binaryAvailable(), false));
        return new AgentOverview(stats, active, System.getProperty("os.name", "unknown"),
                System.getProperty("os.arch", "unknown"), coins, earnings, gpus, proxy(), savedMoneroConfiguration(),
                pearlMinerService.configuration(),
                new DownloadReadiness(xmrDownloadService.status(), xmrDownloadService.detail(), xmrDownloadService.progress(),
                        xmrMinerService.lastStartError(), xmrDownloadService.installDirectory().toString()),
                new PearlReadiness(pearlConfigured,
                        pearlMinerService.binaryAvailable(),
                        srbDownloadService.status(), srbDownloadService.detail(), srbDownloadService.progress(),
                        pearlMinerService.lastError(), pearlMinerService.connectionDetail(),
                        pearlMinerService.running(), pearlMinerService.poolHealthy(), pearlMinerService.gpuStates(gpus),
                        srbDownloadService.installDirectory().toString()),
                payoutDefaults(), new ReferralOverview(referralConfigurationService.get()), feeTransparencyService.overview());
    }

    /** Fee-backend payout per coin, shown so an empty wallet is never silently an unknown destination. */
    @GetMapping("/payout-defaults")
    public List<PayoutOverview> payoutDefaults() {
        return java.util.List.of("monero", "pearl").stream()
                .map(coin -> {
                    PayoutDefaultsService.DefaultView view = payoutDefaultsService.view(coin);
                    return new PayoutOverview(coin, view.available(), view.targetId(), view.poolUrl(),
                            view.maskedWallet(), payoutDefaultsService.usesDefault(coin));
                }).toList();
    }

    public record PayoutOverview(String coin, boolean available, String targetId, String poolUrl,
                                 String maskedWallet, boolean inUse) { }

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

    @GetMapping("/wallet-balances")
    public List<WalletBalanceService.Balance> walletBalances() {
        MoneroConfiguration monero = savedMoneroConfiguration();
        PearlMinerService.Config pearl = pearlMinerService.configuration();
        return walletBalanceService.balances(
                monero == null ? null : monero.poolUrl(), monero == null ? null : monero.wallet(),
                pearl == null ? null : pearl.poolUrl(), pearl == null ? null : pearl.wallet());
    }

    public record AgentOverview(MinerStats stats, String activeCoin, String platform, String architecture,
                                List<CoinOverview> coins,
                                List<EarningsForecastService.Forecast> earnings,
                                List<LocalGpuPowerService.Gpu> gpus, ProxyOverview proxy,
                                MoneroConfiguration moneroConfiguration, PearlMinerService.Config pearlConfiguration,
                                DownloadReadiness monero,
                                PearlReadiness pearl,
                                List<PayoutOverview> payoutDefaults,
                                ReferralOverview referral,
                                List<FeeTransparencyService.FeeOverview> fees) { }

    public record DownloadReadiness(String downloadStatus, String downloadDetail, int downloadProgress,
                                    String minerError, String installDirectory) { }

    public record CoinOverview(String id, String name, String ticker, String device, String algorithm,
                               MinerStats.MinerStatus status, boolean configured, boolean binaryAvailable,
                               boolean experimental) { }

    public record PearlReadiness(boolean configured, boolean binaryAvailable,
                                 String downloadStatus, String downloadDetail, int downloadProgress,
                                 String minerError, String connectionDetail, boolean running,
                                 boolean poolHealthy, List<PearlMinerService.GpuState> gpus,
                                 String installDirectory) { }

    @GetMapping("/earnings")
    public List<EarningsForecastService.Forecast> earnings() {
        return earningsForecastService.forecasts(miningService.getWorkerStats());
    }

    @PostMapping("/miners/{coin}/resume")
    public boolean resumeMiner(@PathVariable String coin) {
        return miningService.resumeMining(coin);
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
        return miningService.getExternalStats();
    }
}
