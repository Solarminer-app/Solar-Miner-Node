package de.verdox.pv_miner.dashboard;

import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.globalconstants.GlobalConstantsService;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.miningpool.MiningCoin;
import de.verdox.pv_miner.miningpool.MiningCoinDailyReward;
import de.verdox.pv_miner.miningpool.KryptexRewardService;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Locale;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Aggregates source forecasts without knowing coin-specific network APIs. */
@Service
public class MiningEarningsService {
    private final EntityQueryService queries;
    private final GlobalConstantsService constants;
    private final List<MiningEarningsSource> sources;
    private final KryptexRewardService poolRewards;
    private final Map<UUID, Snapshot> cache = new ConcurrentHashMap<>();

    public MiningEarningsService(EntityQueryService queries, GlobalConstantsService constants,
                                 List<MiningEarningsSource> sources, KryptexRewardService poolRewards) {
        this.queries = queries;
        this.constants = constants;
        this.sources = sources;
        this.poolRewards = poolRewards;
    }

    public synchronized Snapshot snapshot(PVSiteEntity site) {
        Snapshot previous = cache.get(site.getId());
        if (previous != null && previous.updatedAt().plusSeconds(30).isAfter(Instant.now())) return previous;
        Snapshot fresh = calculate(site);
        cache.put(site.getId(), fresh);
        return fresh;
    }

    /** Currency-aware presentation of the conservative forecast used by the dashboard. */
    public CurrencySnapshot currencySnapshot(PVSiteEntity site, CustomCurrency currency, Locale locale) {
        Snapshot base = snapshot(site);
        double currencyPerUsd = constants.getExchangeRate(CustomCurrency.getInstance("USD"), currency);
        double gridPrice = constants.convert(site.getCurrentElectricityPrice(), currency).getRawMoneyAmount();
        double revenueUsd = base.coins().stream().filter(CoinEstimate::available)
                .mapToDouble(CoinEstimate::usdPerDay).sum();
        boolean available = base.complete() && currencyPerUsd > 0 && base.kwhPerDay() != null && base.kwhPerDay() > 0;
        Double currencyPerDay = available ? revenueUsd * currencyPerUsd : null;
        Double currencyPerKwh = available ? currencyPerDay / base.kwhPerDay() : null;
        return new CurrencySnapshot(base.coins(), base.actualPoolCredits(), base.complete(), base.centsPerKwh(),
                base.gridCentsPerKwh(), base.eurPerDay(), base.kwhPerDay(), base.eurPerUsd(),
                base.updatedAt(), base.diagnostics(), currency.getCurrencyCode(), currency.getSymbol(locale),
                currencyPerKwh, gridPrice > 0 ? gridPrice : null, currencyPerDay,
                currencyPerUsd > 0 ? currencyPerUsd : null);
    }

    private Snapshot calculate(PVSiteEntity site) {
        Map<MiningCoin, List<CoinEstimate>> perCoin = new LinkedHashMap<>();
        List<CoinEstimate> unavailable = new ArrayList<>();
        for (var miner : site.getMiners()) {
            MinerStats stats = queries.getLastResult(miner, MinerStats.DEFAULT);
            if (stats.miningStatus() != MinerStats.MinerStatus.MINING) continue;
            MiningEarningsSource source = sources.stream().filter(candidate -> candidate.supports(miner)).findFirst().orElse(null);
            if (source == null) {
                unavailable.add(CoinEstimate.unavailable("unknown", "—", "Keine Prognosequelle für diesen Miner"));
                continue;
            }
            List<CoinEstimate> estimates = source.forecast(miner, stats);
            if (estimates.isEmpty()) unavailable.add(CoinEstimate.unavailable("unknown", "—", "Keine Coin-Prognose vorhanden"));
            for (CoinEstimate estimate : estimates) {
                try {
                    perCoin.computeIfAbsent(MiningCoin.from(estimate.coin()), ignored -> new ArrayList<>()).add(estimate);
                } catch (IllegalArgumentException ignored) {
                    unavailable.add(CoinEstimate.unavailable(estimate.coin(), estimate.ticker(), "Coin ist im Node nicht registriert"));
                }
            }
        }
        List<CoinEstimate> coins = new ArrayList<>();
        perCoin.forEach((coin, entries) -> {
            boolean available = entries.stream().allMatch(CoinEstimate::available);
            List<String> diagnostics = entries.stream().flatMap(entry -> entry.diagnostics().stream()).distinct().toList();
            List<String> sources = entries.stream().flatMap(entry -> entry.sources().stream()).distinct().toList();
            boolean net = entries.stream().allMatch(entry -> "NET_KNOWN_FEES".equals(entry.estimateKind()));
            List<Double> feePercentages = entries.stream().map(CoinEstimate::knownFeePercent).distinct().toList();
            Double knownFeePercent = feePercentages.size() == 1 ? feePercentages.getFirst() : null;
            coins.add(new CoinEstimate(coin.key(), coin.symbol(), available,
                    available ? "" : entries.stream().filter(entry -> !entry.available()).findFirst().map(CoinEstimate::reason).orElse("Daten fehlen"),
                    entries.stream().mapToDouble(CoinEstimate::coinsPerDay).sum(),
                    entries.stream().mapToDouble(CoinEstimate::usdPerDay).sum(),
                    entries.stream().mapToDouble(CoinEstimate::watts).sum(),
                    entries.stream().mapToDouble(CoinEstimate::hashrateHps).sum(),
                    entries.stream().map(CoinEstimate::updatedAt).filter(value -> !value.isBlank()).findFirst().orElse(""), sources, diagnostics,
                    entries.stream().mapToDouble(CoinEstimate::grossCoinsPerDay).sum(),
                    entries.stream().mapToDouble(CoinEstimate::grossUsdPerDay).sum(),
                    knownFeePercent, net ? "NET_KNOWN_FEES" : "GROSS"));
        });
        coins.addAll(unavailable);
        double usdToEur = constants.getExchangeRate(CustomCurrency.getInstance("USD"), CustomCurrency.getInstance("EUR"));
        double gridPrice = constants.convert(site.getCurrentElectricityPrice(), CustomCurrency.getInstance("EUR")).getRawMoneyAmount();
        double revenue = coins.stream().filter(CoinEstimate::available).mapToDouble(CoinEstimate::usdPerDay).sum() * usdToEur;
        double watts = coins.stream().filter(CoinEstimate::available).mapToDouble(CoinEstimate::watts).sum();
        boolean complete = !coins.isEmpty() && coins.stream().allMatch(CoinEstimate::available)
                && constants.hasFreshRates()
                && usdToEur > 0 && gridPrice > 0 && watts > 0;
        List<String> diagnostics = new ArrayList<>();
        if (!constants.hasFreshRates() || usdToEur <= 0) diagnostics.add("USD/EUR-Wechselkurs fehlt oder ist veraltet");
        if (gridPrice <= 0) diagnostics.add("Stromtarif fehlt oder ist ungültig");
        if (watts <= 0) diagnostics.add("Keine positive Leistung aus verfügbaren Miner-Prognosen");
        double centsPerKwh = watts > 0 && usdToEur > 0 ? revenue / (watts * 24 / 1000) * 100 : 0;
        return new Snapshot(coins, actualPoolCredits(site), complete, complete ? centsPerKwh : null, gridPrice > 0 ? gridPrice * 100 : null,
                complete ? revenue : null, complete ? watts * 24 / 1000 : null,
                usdToEur > 0 ? usdToEur : null, Instant.now(), diagnostics);
    }

    private List<PoolActual> actualPoolCredits(PVSiteEntity site) {
        java.time.LocalDate to = java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1);
        java.time.LocalDate from = to.minusDays(6);
        return actualPoolCredits(poolRewards.snapshot(site.getId(), from, to), from, to,
                (coin, date) -> constants.getCoinPriceUsd(MiningCoin.from(coin).symbol(), date));
    }

    static List<PoolActual> actualPoolCredits(KryptexRewardService.Snapshot snapshot,
                                              java.time.LocalDate from, java.time.LocalDate to,
                                              java.util.function.BiFunction<String, java.time.LocalDate, Double> priceUsd) {
        return snapshot.activeRoutes().stream().map(KryptexRewardService.Route::coin).distinct().sorted().map(coin -> {
            List<KryptexRewardService.Route> routes = snapshot.activeRoutes().stream()
                    .filter(route -> coin.equals(route.coin())).toList();
            var rows = snapshot.rows().stream().filter(row -> coin.equals(row.getCoin())
                    && !row.getDate().isBefore(from) && !row.getDate().isAfter(to)).toList();
            long days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
            long expectedPoints = routes.size() * days;
            long uniquePoints = rows.stream().map(row -> row.getPayoutAddress() + "\n" + row.getDate()).distinct().count();
            java.math.BigDecimal amount = rows.stream().map(row -> row.getAmount())
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            Map<java.time.LocalDate, Double> dailyPrices = rows.stream().map(MiningCoinDailyReward::getDate).distinct()
                    .collect(java.util.stream.Collectors.toMap(date -> date, date -> priceUsd.apply(coin, date)));
            boolean valued = dailyPrices.values().stream().allMatch(price -> price > 0);
            java.math.BigDecimal usd = valued ? rows.stream()
                    .map(row -> row.getAmount().multiply(java.math.BigDecimal.valueOf(dailyPrices.get(row.getDate()))))
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add) : null;
            MiningCoin definition = MiningCoin.from(coin);
            return new PoolActual(coin, definition.symbol(), from, to, amount,
                    amount.divide(java.math.BigDecimal.valueOf(7), 18, java.math.RoundingMode.HALF_UP),
                    usd, routes.size(), (int) uniquePoints,
                    expectedPoints > 0 && uniquePoints == expectedPoints && valued, "KRYPTEX_REWARD_CHART");
        }).toList();
    }

    public record CoinEstimate(String coin, String ticker, boolean available, String reason, double coinsPerDay,
                               double usdPerDay, double watts, double hashrateHps, String updatedAt,
                               List<String> sources, List<String> diagnostics,
                               double grossCoinsPerDay, double grossUsdPerDay,
                               Double knownFeePercent, String estimateKind) {
        public static CoinEstimate unavailable(String coin, String ticker, String reason) {
            return new CoinEstimate(coin, ticker, false, reason, 0, 0, 0, 0, "", List.of(), List.of(reason),
                    0, 0, null, "UNAVAILABLE");
        }
    }
    public record PoolActual(String coin, String ticker, java.time.LocalDate from, java.time.LocalDate to,
                             java.math.BigDecimal amount, java.math.BigDecimal averagePerDay,
                             java.math.BigDecimal usdValue, int routes, int dataPoints,
                             boolean complete, String source) { }
    public record Snapshot(List<CoinEstimate> coins, List<PoolActual> actualPoolCredits, boolean complete, Double centsPerKwh,
                           Double gridCentsPerKwh, Double eurPerDay, Double kwhPerDay,
                           Double eurPerUsd, Instant updatedAt, List<String> diagnostics) {}
    public record CurrencySnapshot(List<CoinEstimate> coins, List<PoolActual> actualPoolCredits,
                                   boolean complete, Double centsPerKwh, Double gridCentsPerKwh,
                                   Double eurPerDay, Double kwhPerDay, Double eurPerUsd,
                                   Instant updatedAt, List<String> diagnostics,
                                   String currencyCode, String currencySymbol,
                                   Double currencyPerKwh, Double gridCurrencyPerKwh,
                                   Double currencyPerDay, Double currencyPerUsd) {}
}
