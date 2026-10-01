package de.verdox.pv_miner.dashboard;

import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.globalconstants.GlobalConstantsService;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.miningpool.MiningCoin;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import org.springframework.stereotype.Service;
import java.time.Instant;
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
    private final Map<UUID, Snapshot> cache = new ConcurrentHashMap<>();

    public MiningEarningsService(EntityQueryService queries, GlobalConstantsService constants, List<MiningEarningsSource> sources) {
        this.queries = queries;
        this.constants = constants;
        this.sources = sources;
    }

    public synchronized Snapshot snapshot(PVSiteEntity site) {
        Snapshot previous = cache.get(site.getId());
        if (previous != null && previous.updatedAt().plusSeconds(30).isAfter(Instant.now())) return previous;
        Snapshot fresh = calculate(site);
        cache.put(site.getId(), fresh);
        return fresh;
    }

    private Snapshot calculate(PVSiteEntity site) {
        Map<MiningCoin, List<CoinEstimate>> perCoin = new LinkedHashMap<>();
        List<CoinEstimate> unavailable = new ArrayList<>();
        for (var miner : site.getMiners()) {
            MinerStats stats = queries.getLastResult(miner, MinerStats.DEFAULT);
            boolean hasObservedRate = stats.terahashPerSecond() > 0 || (stats.workers() != null && stats.workers().stream().anyMatch(worker -> worker.terahashPerSecond() > 0));
            if (!hasObservedRate && stats.miningStatus() != MinerStats.MinerStatus.MINING) continue;
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
            coins.add(new CoinEstimate(coin.key(), coin.symbol(), available,
                    available ? "" : entries.stream().filter(entry -> !entry.available()).findFirst().map(CoinEstimate::reason).orElse("Daten fehlen"),
                    entries.stream().mapToDouble(CoinEstimate::coinsPerDay).sum(),
                    entries.stream().mapToDouble(CoinEstimate::usdPerDay).sum(),
                    entries.stream().mapToDouble(CoinEstimate::watts).sum(),
                    entries.stream().mapToDouble(CoinEstimate::hashrateHps).sum(),
                    entries.stream().map(CoinEstimate::updatedAt).filter(value -> !value.isBlank()).findFirst().orElse(""), sources, diagnostics));
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
        return new Snapshot(coins, complete, complete ? centsPerKwh : null, gridPrice > 0 ? gridPrice * 100 : null,
                complete ? revenue : null, complete ? watts * 24 / 1000 : null,
                usdToEur > 0 ? usdToEur : null, Instant.now(), diagnostics);
    }

    public record CoinEstimate(String coin, String ticker, boolean available, String reason, double coinsPerDay,
                               double usdPerDay, double watts, double hashrateHps, String updatedAt,
                               List<String> sources, List<String> diagnostics) {
        public static CoinEstimate unavailable(String coin, String ticker, String reason) {
            return new CoinEstimate(coin, ticker, false, reason, 0, 0, 0, 0, "", List.of(), List.of(reason));
        }
    }
    public record Snapshot(List<CoinEstimate> coins, boolean complete, Double centsPerKwh,
                           Double gridCentsPerKwh, Double eurPerDay, Double kwhPerDay,
                           Double eurPerUsd, Instant updatedAt, List<String> diagnostics) {}
}
