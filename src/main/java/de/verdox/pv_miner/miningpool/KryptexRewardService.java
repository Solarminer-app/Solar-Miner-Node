package de.verdox.pv_miner.miningpool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Actual credited daily rewards reported by Kryptex, independent of unpaid balances and payouts. */
@Service
public class KryptexRewardService {
    private static final Logger LOGGER = Logger.getLogger(KryptexRewardService.class.getName());
    private final MiningTargetRepository targets;
    private final WatchedWalletRepository wallets;
    private final KryptexPoolApiService poolBalances;
    private final MiningCoinDailyRewardRepository rewards;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final Map<UUID, Instant> refreshed = new ConcurrentHashMap<>();

    public KryptexRewardService(MiningTargetRepository targets, WatchedWalletRepository wallets,
                                KryptexPoolApiService poolBalances,
                                MiningCoinDailyRewardRepository rewards, ObjectMapper mapper) {
        this.targets = targets;
        this.wallets = wallets;
        this.poolBalances = poolBalances;
        this.rewards = rewards;
        this.mapper = mapper;
    }

    public Snapshot snapshot(UUID siteId, LocalDate from, LocalDate to) {
        refresh(siteId);
        Set<Route> routes = supportedRoutes(siteId);
        return new Snapshot(rewards.findBySiteIdAndDateBetween(siteId, from, to), routes);
    }

    public void invalidate(UUID siteId) { refreshed.remove(siteId); }

    public synchronized void refresh(UUID siteId) {
        Instant previous = refreshed.get(siteId);
        if (previous != null && previous.isAfter(Instant.now().minusSeconds(900))) return;
        Set<Route> routes = supportedRoutes(siteId);
        Map<String, Map<LocalDate, BigDecimal>> prices = new HashMap<>();
        boolean complete = true;
        for (Route route : routes) {
            try {
                if (MiningCoin.from(route.coin()).kryptexTicker() == null)
                    throw new IllegalStateException("Kryptex reward history is unavailable for " + route.coin());
                Map<LocalDate, BigDecimal> priceByDay = prices.computeIfAbsent(route.coin(), coin -> {
                    try { return parsePrices(fetch("/api/v1/coin/" + ticker(coin) + "/price/chart?time_range=year")); }
                    catch (Exception e) { LOGGER.log(Level.WARNING, "Kryptex price chart unavailable for " + coin, e); return Map.of(); }
                });
                JsonNode chart = fetch("/" + ticker(route.coin()) + "/api/v1/miner/reward-chart/" + route.address());
                Map<LocalDate, BigDecimal> daily = parseRewards(chart);
                Instant fetchedAt = Instant.now();
                for (var point : daily.entrySet()) {
                    MiningCoinDailyReward row = rewards.findBySiteIdAndCoinAndPayoutAddressAndDate(siteId,
                            route.coin(), route.address(), point.getKey()).orElseGet(MiningCoinDailyReward::new);
                    row.setSiteId(siteId);
                    row.setCoin(route.coin());
                    row.setPayoutAddress(route.address());
                    row.setDate(point.getKey());
                    row.setAmount(point.getValue());
                    if (priceByDay.containsKey(point.getKey())) row.setPriceUsd(priceByDay.get(point.getKey()));
                    row.setFetchedAt(fetchedAt);
                    rewards.save(row);
                }
            } catch (Exception e) {
                complete = false;
                LOGGER.log(Level.WARNING, "Kryptex reward chart unavailable for " + route.coin(), e);
            }
        }
        refreshed.put(siteId, Instant.now().minusSeconds(complete ? 0 : 840));
    }

    @Scheduled(fixedDelay = 21_600_000, initialDelay = 60_000)
    public void refreshAll() {
        Set<UUID> siteIds = new HashSet<>(targets.findAll().stream().map(MiningTargetEntity::getSiteId).toList());
        wallets.findAll().stream().map(WatchedWalletEntity::getSiteId).forEach(siteIds::add);
        for (UUID siteId : siteIds) refresh(siteId);
    }

    private JsonNode fetch(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://pool.kryptex.com" + path))
                .timeout(Duration.ofSeconds(8)).header("Accept", "application/json").GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("Kryptex HTTP " + response.statusCode());
        return mapper.readTree(response.body());
    }

    static Set<Route> supportedRoutes(List<MiningTargetEntity> targets) {
        Set<Route> routes = new HashSet<>();
        for (MiningTargetEntity target : targets) {
            if (target.getPayoutAddress() == null || target.getPayoutAddress().isBlank()) continue;
            MiningCoin coin;
            try { coin = MiningCoin.from(target.getCoin()); }
            catch (IllegalArgumentException ignored) { continue; }
            if (coin.kryptexTicker() == null) continue;
            try {
                String host = URI.create(target.getStratumUrl()).getHost();
                if (host != null && (host.equalsIgnoreCase("kryptex.network")
                        || host.toLowerCase(Locale.ROOT).endsWith(".kryptex.network")))
                    routes.add(new Route(target.getCoin(), target.getPayoutAddress()));
            } catch (IllegalArgumentException ignored) { }
        }
        return routes;
    }

    private Set<Route> supportedRoutes(UUID siteId) {
        Set<Route> routes = ConcurrentHashMap.newKeySet();
        routes.addAll(supportedRoutes(targets.findBySiteIdOrderByPriorityAsc(siteId)));
        wallets.findBySiteIdOrderByLabelAsc(siteId).parallelStream().forEach(wallet -> {
            try {
                MiningCoin coin = MiningCoin.from(wallet.getCoin());
                if (coin == MiningCoin.BITCOIN) return;
                // A public address is only treated as a pool-account route when Kryptex recognizes it.
                // DCR stays visible as unavailable because the PC-Agent's current Suprnova route has no adapter.
                if (coin.kryptexTicker() == null || poolBalances.read(coin, wallet.getAddress()).amount() != null)
                    routes.add(new Route(wallet.getCoin(), wallet.getAddress()));
            } catch (IllegalArgumentException ignored) { }
        });
        return routes;
    }

    static Map<LocalDate, BigDecimal> parseRewards(JsonNode chart) {
        if (!chart.isArray()) throw new IllegalArgumentException("Invalid reward chart");
        Map<LocalDate, BigDecimal> result = new HashMap<>();
        for (JsonNode point : chart) {
            JsonNode amount = point.path("reward");
            JsonNode time = point.path("timestamp");
            if (!amount.isNumber() || !time.isNumber() || amount.decimalValue().signum() < 0) continue;
            LocalDate date = Instant.ofEpochMilli(time.longValue()).atZone(ZoneOffset.UTC).toLocalDate();
            result.merge(date, amount.decimalValue(), BigDecimal::add);
        }
        return result;
    }

    static Map<LocalDate, BigDecimal> parsePrices(JsonNode chart) {
        if (!chart.isArray()) throw new IllegalArgumentException("Invalid price chart");
        Map<LocalDate, PricePoint> latest = new HashMap<>();
        for (JsonNode point : chart) {
            JsonNode price = point.path("price");
            JsonNode time = point.path("timestamp");
            if (!price.isNumber() || !time.isNumber() || price.decimalValue().signum() <= 0) continue;
            LocalDate date = Instant.ofEpochMilli(time.longValue()).atZone(ZoneOffset.UTC).toLocalDate();
            latest.merge(date, new PricePoint(time.longValue(), price.decimalValue()),
                    (left, right) -> left.timestamp() >= right.timestamp() ? left : right);
        }
        return latest.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                entry -> entry.getValue().price()));
    }

    private static String ticker(String coin) {
        MiningCoin value = MiningCoin.from(coin);
        if (value.kryptexTicker() == null) throw new IllegalArgumentException("Kryptex has no data provider for " + coin);
        return value.kryptexTicker();
    }
    private record PricePoint(long timestamp, BigDecimal price) { }

    public record Route(String coin, String address) { }
    public record Snapshot(List<MiningCoinDailyReward> rows, Set<Route> activeRoutes) { }
}
