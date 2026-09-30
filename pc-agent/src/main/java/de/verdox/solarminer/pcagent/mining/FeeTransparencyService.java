package de.verdox.solarminer.pcagent.mining;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.solarminer.pcagent.pearl.PearlMinerService;
import de.verdox.solarminer.pcagent.xmr.XmrConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Operator-facing fee model. Dynamic SolarMiner/referral shares come from the proxy;
 * fixed entries document upstream fees and their source rather than silently estimating them.
 */
@Service
public class FeeTransparencyService {
    private final ObjectMapper mapper;
    private final ProxyConfigurationService proxy;
    private final ReferralConfigurationService referral;
    private final XmrConfigService xmr;
    private final PearlMinerService pearl;
    private final int apiPort;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public FeeTransparencyService(ObjectMapper mapper, ProxyConfigurationService proxy,
                                  ReferralConfigurationService referral, XmrConfigService xmr,
                                  PearlMinerService pearl,
                                  @Value("${solarminer.agent.proxy.api-port:8090}") int apiPort) {
        this.mapper = mapper; this.proxy = proxy; this.referral = referral; this.xmr = xmr; this.pearl = pearl; this.apiPort = apiPort;
    }

    public List<FeeOverview> overview() { return List.of(forCoin("monero"), forCoin("pearl")); }

    private FeeOverview forCoin(String coin) {
        List<FeePart> parts = new ArrayList<>();
        boolean routeAvailable = false;
        try {
            if (proxy.host() != null) {
                String referralQuery = referral.get().isBlank() ? "" : "?referral=" + referral.get();
                HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + proxy.host() + ":" + apiPort
                                + "/api/v1/fees/" + coin + "/targets" + referralQuery))
                        .timeout(Duration.ofSeconds(3)).GET().build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonNode targets = mapper.readTree(response.body());
                    if (targets.isArray()) for (JsonNode target : targets) {
                        double percentage = target.path("percentage").asDouble();
                        if (percentage <= 0) continue;
                        boolean house = target.path("house").asBoolean(false);
                        parts.add(new FeePart(house ? "SOLARMINER" : "REFERRER", house ? "SolarMiner" : "Referrer " + referral.get(),
                                percentage, true, "Vom SolarMiner Fee-Backend geladen"));
                        routeAvailable = true;
                    }
                }
            }
        } catch (Exception ignored) { }
        String pool = poolFor(coin);
        FeeReference poolFee = poolFee(pool);
        parts.add(new FeePart("POOL", poolFee.label, poolFee.percentage, poolFee.known, poolFee.source));
        FeeReference minerFee = "monero".equals(coin)
                ? new FeeReference("XMRig Entwickler-Spende", 1.0, true, "https://xmrig.com/docs/miner/config/network")
                : new FeeReference("SRBMiner-MULTI Entwicklergebühr", 2.0, true, "https://github.com/doktor83/SRBMiner-Multi");
        parts.add(new FeePart("MINER", minerFee.label, minerFee.percentage, minerFee.known, minerFee.source));
        return new FeeOverview(coin, referral.get(), routeAvailable, parts);
    }

    private String poolFor(String coin) {
        if ("pearl".equals(coin)) return pearl.configuration() == null ? null : pearl.configuration().poolUrl();
        try { return xmr.readUserPoolFromConfig().poolUsername().split(";", -1)[0]; } catch (Exception ignored) { return null; }
    }

    private static FeeReference poolFee(String pool) {
        String host;
        try { host = pool == null ? "" : URI.create(pool).getHost(); } catch (Exception ignored) { host = ""; }
        if (host != null && host.endsWith("kryptex.network")) return new FeeReference("Kryptex Pool", 1.0, true, "https://pool.kryptex.com");
        if (host != null && host.endsWith("nicehash.com")) return new FeeReference("NiceHash Pool", 2.0, true, "https://www.nicehash.com");
        return new FeeReference("Eigener/noch nicht katalogisierter Pool", 0, false, "Poolgebühr unbekannt – bitte beim Pool prüfen");
    }

    private record FeeReference(String label, double percentage, boolean known, String source) { }
    public record FeeOverview(String coin, String referral, boolean routeAvailable, List<FeePart> parts) { }
    public record FeePart(String kind, String label, double percentage, boolean known, String source) { }
}
