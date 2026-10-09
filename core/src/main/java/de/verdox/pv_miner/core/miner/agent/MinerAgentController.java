package de.verdox.pv_miner.core.miner.agent;

import com.fasterxml.jackson.databind.JsonNode;
import de.verdox.pv_miner.core.miner.DevFeeConstants;
import de.verdox.pv_miner.core.miner.braiins.MinerController;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import de.verdox.pv_miner.core.service.DevFeeService;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MinerAgentController implements MinerController {

    private final Map<UUID, MinerStats> lastQueriedStats = new ConcurrentHashMap<>();

    @Override
    public boolean startMining(MinerDetails details) {
        try {
            var restClient = RestClient.builder().baseUrl("http://" + details.ipv4() + ":" + details.port()).build();
            return Boolean.TRUE.equals(restClient.post().uri("/api/agent/external/resume").retrieve().body(Boolean.class));
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public boolean stopMining(MinerDetails details) {
        try {
            var restClient = RestClient.builder().baseUrl("http://" + details.ipv4() + ":" + details.port()).build();
            return Boolean.TRUE.equals(restClient.post().uri("/api/agent/external/pause").retrieve().body(Boolean.class));
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public boolean pauseMining(MinerDetails details) {
        return stopMining(details);
    }

    @Override
    public boolean resumeMining(MinerDetails details) {
        return startMining(details);
    }

    @Override
    public boolean setPowerTarget(MinerDetails details, long watts) {
        try {
            var restClient = RestClient.builder().baseUrl("http://" + details.ipv4() + ":" + details.port()).build();
            Map<?, ?> result = restClient.post().uri(uriBuilder -> uriBuilder.path("/api/agent/external/power-target")
                    .queryParam("watts", watts).build()).retrieve().body(Map.class);
            return result != null;
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public boolean incrementPowerTarget(MinerDetails details, long watts) {
        try {
            var restClient = RestClient.builder().baseUrl("http://" + details.ipv4() + ":" + details.port()).build();
            return Boolean.TRUE.equals(restClient.post().uri(uriBuilder -> uriBuilder.path("/api/agent/external/power-target/increment").queryParam("watts", watts).build()).retrieve().body(Boolean.class));
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public boolean decrementPowerTarget(MinerDetails details, long watts) {
        try {
            var restClient = RestClient.builder().baseUrl("http://" + details.ipv4() + ":" + details.port()).build();
            return Boolean.TRUE.equals(restClient.post().uri(uriBuilder -> uriBuilder.path("/api/agent/external/power-target/decrement").queryParam("watts", watts).build()).retrieve().body(Boolean.class));
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public boolean setPoolTarget(MinerDetails details, String stratumUrl, String userName) {
        try {
            var restClient = RestClient.builder().baseUrl("http://" + details.ipv4() + ":" + details.port()).build();
            String proxyHost = java.net.URI.create(stratumUrl).getHost();
            if (proxyHost == null || !Boolean.TRUE.equals(restClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/api/agent/external/proxy").queryParam("host", proxyHost).build())
                    .retrieve().body(Boolean.class))) return false;
            return Boolean.TRUE.equals(restClient.post().uri(uriBuilder -> uriBuilder.path("/api/agent/external/pool-configuration").queryParam("poolUrl", stratumUrl).queryParam("poolUser", userName).queryParam("devFeePercentage", DevFeeConstants.DevFeePercentage).build()).retrieve().body(Boolean.class));
        } catch (Throwable e) {
            return false;
        }
    }

    /** Keep the agent's embedded proxy and its payout-default lookup on the site's referral. */
    public boolean setReferral(MinerDetails details, String referral) {
        try {
            var restClient = RestClient.builder().baseUrl("http://" + details.ipv4() + ":" + details.port()).build();
            return Boolean.TRUE.equals(restClient.post().uri(uriBuilder -> uriBuilder.path("/api/agent/external/referral")
                    .queryParam("key", referral == null ? "" : referral).build())
                    .retrieve().body(Boolean.class));
        } catch (Throwable e) { return false; }
    }

    public boolean configurePearl(MinerDetails details, String poolUrl, String proxyUrl, String wallet, String worker, String devices, String referral) {
        try {
            var restClient = RestClient.builder().baseUrl("http://" + details.ipv4() + ":" + details.port()).build();
            String effectiveProxy = prepareProxy(restClient, proxyUrl, "pearlUrl");
            if (effectiveProxy == null) return false;
            if (!setReferral(details, referral)) return false;
            return Boolean.TRUE.equals(restClient.post().uri("/api/agent/external/pearl/configuration")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("poolUrl", poolUrl, "proxyUrl", effectiveProxy, "wallet", wallet, "worker", worker, "devices", devices == null ? "all" : devices))
                    .retrieve().body(Boolean.class));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean configureMonero(MinerDetails details, String poolUrl, String proxyUrl, String wallet, String worker, String referral) {
        try {
            var restClient = RestClient.builder().baseUrl("http://" + details.ipv4() + ":" + details.port()).build();
            if (prepareProxy(restClient, proxyUrl, "moneroUrl") == null) return false;
            if (!setReferral(details, referral)) return false;
            return Boolean.TRUE.equals(restClient.post().uri("/api/agent/external/monero/configuration")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("poolUrl", poolUrl, "wallet", wallet, "worker", worker))
                    .retrieve().body(Boolean.class));
        } catch (Exception e) {
            return false;
        }
    }

    private static String prepareProxy(RestClient restClient, String externalProxyUrl, String urlField) {
        JsonNode current = restClient.get().uri("/api/agent/external/proxy").retrieve().body(JsonNode.class);
        if (current == null) return null;
        if (!"standalone".equals(current.path("mode").asText())) {
            if (externalProxyUrl == null || externalProxyUrl.isBlank()) return null;
            String proxyHost = java.net.URI.create(externalProxyUrl).getHost();
            if (proxyHost == null || !Boolean.TRUE.equals(restClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/api/agent/external/proxy").queryParam("host", proxyHost).build())
                    .retrieve().body(Boolean.class))) return null;
            current = restClient.get().uri("/api/agent/external/proxy").retrieve().body(JsonNode.class);
        }
        String effectiveUrl = current == null ? null : current.path(urlField).asText(null);
        return effectiveUrl == null || effectiveUrl.isBlank() ? null : effectiveUrl;
    }

    /** The agent is the source of truth; a saved Node assignment alone does not prove the route. */
    public AgentCoinConfigurations coinConfigurations(MinerDetails details) {
        try {
            var restClient = RestClient.builder().baseUrl("http://" + details.ipv4() + ":" + details.port()).build();
            JsonNode overview = restClient.get().uri("/api/agent/external/overview").retrieve().body(JsonNode.class);
            if (overview == null) return null;
            return new AgentCoinConfigurations(route(overview.path("moneroConfiguration")),
                    route(overview.path("pearlConfiguration")));
        } catch (Exception e) {
            return null;
        }
    }

    /** C10 capability payload is intentionally opaque here so newer agents remain additive. */
    public JsonNode economicCapabilities(MinerDetails details) {
        try {
            var restClient = RestClient.builder().baseUrl("http://" + details.ipv4() + ":" + details.port()).build();
            return restClient.get().uri("/api/agent/external/capabilities").retrieve().body(JsonNode.class);
        } catch (Exception e) {
            return null;
        }
    }

    public JsonNode applyEconomicPlan(MinerDetails details, JsonNode plan) {
        try {
            var restClient = RestClient.builder().baseUrl("http://" + details.ipv4() + ":" + details.port()).build();
            return restClient.post().uri("/api/agent/external/economic-plan")
                    .contentType(MediaType.APPLICATION_JSON).body(plan).retrieve().body(JsonNode.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static CoinRoute route(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return null;
        return new CoinRoute(node.path("poolUrl").asText(null), node.path("wallet").asText(null),
                node.path("worker").asText(null), node.path("devices").asText(null));
    }

    public record AgentCoinConfigurations(CoinRoute monero, CoinRoute pearl) { }
    public record CoinRoute(String poolUrl, String wallet, String worker, String devices) { }

    @Override
    public MinerStats queryStats(String minerName, MinerDetails details) {
        try {
            var restClient = RestClient.builder().baseUrl("http://" + details.ipv4() + ":" + details.port()).build();
            MinerStats stats = restClient.get().uri("/api/agent/external/status").retrieve().body(MinerStats.class);
            Map<?, ?> range = restClient.get().uri("/api/agent/external/power-control").retrieve().body(Map.class);
            if (stats == null || range == null) return stats;
            double hardwareTemperature = readHardwareTemperature(restClient);
            long min = number(range.get("minPowerWatts"), stats.minPowerTarget());
            long max = number(range.get("maxPowerWatts"), stats.maxPowerTarget());
            long target = number(range.get("currentTargetWatts"), stats.powerTargetWatts());
            List<MinerStats.Worker> workers = stats.workers() == null ? List.of() : stats.workers().stream()
                    .map(worker -> miningOnlyPower(worker))
                    .toList();
            long usage = workers.isEmpty()
                    ? stats.miningStatus() == MinerStats.MinerStatus.MINING
                        ? number(range.get("currentUsageWatts"), stats.approximatedPowerUsageWatts()) : 0
                    : workers.stream().mapToLong(MinerStats.Worker::approximatedPowerUsageWatts).sum();
            long standard = number(range.get("defaultPowerWatts"), stats.defaultPowerTarget());
            boolean dynamicEnabled = Boolean.TRUE.equals(range.get("dynamicPowerScalingEnabled"));
            boolean externalEnabled = Boolean.TRUE.equals(range.get("externalControlEnabled"));
            // Zero limits make the Node treat locally withdrawn permission as unavailable
            // capacity. Dynamic regulation is optional: a permitted agent may still be used
            // as a fixed-power start/stop miner.
            if (!externalEnabled) {
                min = 0;
                max = 0;
                standard = 0;
                target = 0;
            } else if (!dynamicEnabled) {
                long fixedPower = standard > 0 ? standard : max;
                min = fixedPower;
                max = fixedPower;
                target = fixedPower;
            }
            return new MinerStats(stats.minerIdentity(), stats.name(), stats.miningStatus(), target, min, standard, max,
                    usage, stats.terahashPerSecond(), Math.max(stats.temperatureCelsius(), hardwareTemperature),
                    stats.pools(), workers);
        } catch (Throwable e) {
            return MinerStats.DEFAULT;
        }
    }

    /** Idle worker draw may be real device consumption, but it is not mining consumption. */
    private static MinerStats.Worker miningOnlyPower(MinerStats.Worker worker) {
        if (worker.miningStatus() == MinerStats.MinerStatus.MINING) return worker;
        return new MinerStats.Worker(worker.miningStatus(), worker.workerDisplayName(), worker.currentAlgorithm(),
                worker.terahashPerSecond(), worker.temperatureCelsius(), worker.powerTargetWatts(),
                worker.minPowerTarget(), worker.defaultPowerTarget(), worker.maxPowerTarget(), 0,
                worker.pools(), worker.hardwareType(), worker.hardwareModel(), worker.deviceId(),
                worker.acceptedShares(), worker.rejectedShares());
    }

    /** The agent's hardware sensors are a separate endpoint from its mining-worker statistics. */
    private static double readHardwareTemperature(RestClient restClient) {
        try {
            JsonNode telemetry = restClient.get().uri("/api/agent/external/telemetry").retrieve().body(JsonNode.class);
            JsonNode metrics = telemetry == null ? null : telemetry.path("metrics");
            if (metrics == null || !metrics.isObject()) return 0;
            double maximum = 0;
            var fields = metrics.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                JsonNode metric = entry.getValue();
                if (!entry.getKey().toLowerCase(java.util.Locale.ROOT).contains("temperature")
                        || !metric.path("available").asBoolean(false)
                        || !"°C".equals(metric.path("unit").asText())) continue;
                double value = metric.path("value").asDouble(Double.NaN);
                if (Double.isFinite(value) && value >= -20 && value <= 150) maximum = Math.max(maximum, value);
            }
            return maximum;
        } catch (Throwable ignored) {
            // Older agents may not expose hardware telemetry; retain their worker temperature.
            return 0;
        }
    }

    private static long number(Object value, long fallback) {
        return value instanceof Number n ? n.longValue() : fallback;
    }

    @Override
    public MinerStats getLastData(MinerDetails minerDetails) {
        return lastQueriedStats.getOrDefault(minerDetails.id(), null);
    }

    @Override
    public boolean checkIfCustomCredentialsWork(MinerDetails details) {
        return true;
    }

    @Override
    public boolean checkIfStandardCredentialsWork(MinerDetails details) {
        return true;
    }

    public boolean verifyProxyRouting(MinerDetails details, String proxyIp) {
        try {
            var restClient = RestClient.builder().baseUrl("http://" + details.ipv4() + ":" + details.port()).build();
            Map<?, ?> proxy = restClient.get().uri("/api/agent/external/proxy").retrieve().body(Map.class);
            return proxy != null && proxyIp.equals(proxy.get("host")) && Boolean.TRUE.equals(proxy.get("reachable"));
        } catch (Exception e) {
            return false;
        }
    }

    public void enforceProxyRouting(MinerDetails details, String proxyIP, String proxyPort) {
        try {
            var restClient = RestClient.builder().baseUrl("http://" + details.ipv4() + ":" + details.port()).build();
            restClient.post().uri(uriBuilder -> uriBuilder.path("/api/agent/external/proxy")
                    .queryParam("host", proxyIP).build()).retrieve().body(Boolean.class);
        } catch (Exception ignored) { }
    }

    @Override
    public boolean verifyDevFeeNative(MinerDetails minerDetails, List<DevFeeService.FeeTarget> feeTargets) {
        return false;
    }

    @Override
    public void enforceDevFeeNative(MinerDetails minerDetails, List<DevFeeService.FeeTarget> feeTargets) {

    }
}
