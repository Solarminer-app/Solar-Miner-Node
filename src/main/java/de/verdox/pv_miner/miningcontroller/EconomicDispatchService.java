package de.verdox.pv_miner.miningcontroller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.pv_miner.dashboard.MiningEarningsSource;
import de.verdox.pv_miner.entity.EntityControllerService;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.globalconstants.GlobalConstantsService;
import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.MiningOS;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.miningcontroller.dsl.ControllerDSL;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Evaluates each measured worker against feed-in and grid tariffs. Unknown data fails closed. */
@Service
public class EconomicDispatchService {
    private final MinerApiClient api;
    private final EntityControllerService controllers;
    private final EntityQueryService queries;
    private final GlobalConstantsService constants;
    private final List<MiningEarningsSource> earningsSources;
    private final MinerEfficiencyLearningService efficiencies;
    private final ObjectMapper json;
    private final Map<UUID, Boolean> lastMinerDecision = new ConcurrentHashMap<>();
    private final Map<UUID, TimedStats> lastRunningStats = new ConcurrentHashMap<>();

    public EconomicDispatchService(MinerApiClient api, EntityControllerService controllers, EntityQueryService queries,
                                   GlobalConstantsService constants, List<MiningEarningsSource> earningsSources,
                                   MinerEfficiencyLearningService efficiencies, ObjectMapper json) {
        this.api = api;
        this.controllers = controllers;
        this.queries = queries;
        this.constants = constants;
        this.earningsSources = earningsSources;
        this.efficiencies = efficiencies;
        this.json = json;
    }

    public double evaluate(PVSiteEntity site, List<MinerEntity<?>> miners, ControllerDSL.ControllerAction action,
                         double batterySoc, double potentialSurplusKw) {
        double threshold = action.valueExpression() instanceof ControllerDSL.ValueExpression.EconomicThreshold economic
                ? economic.margin() : action.valueExpression() instanceof ControllerDSL.ValueExpression.Constant constant
                ? constant.valueWatts() : Double.NaN;
        double gridBatteryFloor = action.valueExpression() instanceof ControllerDSL.ValueExpression.EconomicThreshold economic
                ? economic.gridBatteryFloorPercent() : 10.0;
        boolean percent = action.strategy() == ControllerDSL.MinerDistributionStrategy.ECONOMIC_PERCENT;
        if ((!percent && action.strategy() != ControllerDSL.MinerDistributionStrategy.ECONOMIC_ABSOLUTE)
                || !Double.isFinite(threshold) || threshold < 0 || !Double.isFinite(batterySoc)
                || batterySoc < 0 || batterySoc > 100 || !Double.isFinite(gridBatteryFloor)
                || gridBatteryFloor < 0 || gridBatteryFloor > 100) return 0;
        double reserveSoc = action.stepSizeWatts();
        if (reserveSoc < 10 || reserveSoc > 100) return 0;
        CustomCurrency eur = CustomCurrency.getInstance("EUR");
        double usdToEur = constants.getExchangeRate(CustomCurrency.getInstance("USD"), eur);
        double grid = constants.convert(site.getCurrentElectricityPrice(), eur).getRawMoneyAmount();
        double feedIn = constants.convert(site.getCurrentFeedInTariff(), eur).getRawMoneyAmount();
        boolean tariffConfigured = site.getFeedInTariffHistory() != null
                && site.getFeedInTariffHistory().stream().anyMatch(price ->
                !price.getValidFrom().isAfter(java.time.LocalDate.now(site.getZoneId())));
        if (!constants.hasFreshRates() || !(usdToEur > 0) || !(grid > 0)
                || !tariffConfigured || feedIn < 0) {
            pauseUnknown(miners);
            return 0;
        }
        double requiredGrid = percent ? grid * (1 + threshold / 100.0) : grid + threshold / 100.0;
        double pvBudgetWatts = Math.max(0, potentialSurplusKw * 1000);
        double selectedWatts = 0;

        for (MinerEntity<?> miner : miners) {
            DispatchResult result;
            if (miner.getOS() == MiningOS.AGENT) {
                result = dispatchAgent(miner, batterySoc, reserveSoc, gridBatteryFloor, pvBudgetWatts, feedIn, requiredGrid, usdToEur);
            } else {
                result = dispatchWholeMiner(miner, batterySoc, reserveSoc, gridBatteryFloor, pvBudgetWatts, feedIn, requiredGrid, usdToEur);
            }
            pvBudgetWatts -= result.pvWatts();
            selectedWatts += result.selectedWatts();
        }
        return selectedWatts;
    }

    public void pauseUnknown(List<MinerEntity<?>> miners) {
        for (MinerEntity<?> miner : miners) {
            if (miner.getOS() == MiningOS.AGENT) {
                var plan = json.createObjectNode();
                plan.put("planId", UUID.randomUUID().toString());
                plan.put("validForSeconds", 90);
                plan.putNull("targetWatts");
                plan.putArray("assignments");
                plan.putArray("activeWorkerIds");
                api.applyAgentEconomicPlan(miner.getDetails(), plan);
            } else if (controllers.getController(miner).pauseMining()) {
                lastMinerDecision.put(miner.getId(), false);
            }
        }
    }

    DispatchResult dispatchAgent(MinerEntity<?> miner, double soc, double reserveSoc, double gridBatteryFloor, double pvBudgetWatts,
                               double feedIn, double grid, double usdToEur) {
        JsonNode capabilities = api.getAgentEconomicCapabilities(miner.getDetails());
        if (capabilities == null || capabilities.path("protocolVersion").asInt() != 1
                || !capabilities.path("workers").isArray()) return DispatchResult.NONE;
        List<WorkerChoice> choices = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode worker : capabilities.path("workers")) {
            String id = worker.path("workerId").asText("");
            boolean localPowerMode = !"MANUAL".equals(worker.path("powerMode").asText("MANUAL"));
            String selectedCoin = worker.path("selectedCoin").asText("");
            if (id.isBlank() || !seen.add(id) || !worker.path("externalControlEnabled").asBoolean()
                    || !"AUTO".equals(worker.path("coinPolicy").asText())) continue;
            WorkerChoice best = null;
            for (JsonNode coin : worker.path("coins")) {
                JsonNode profile = coin.path("profile");
                double watts = profile.path("watts").asDouble();
                double usdPerDay = coin.path("usdPerDay").asDouble();
                String coinId = coin.path("coin").asText("");
                if (localPowerMode && !coinId.equals(selectedCoin)) continue;
                if (!coin.path("configured").asBoolean() || !coin.path("feeRouteReady").asBoolean()
                        || !(watts > 0) || !(usdPerDay > 0) || coinId.isBlank()
                        || !fresh(coin.path("marketUpdatedAt"), Duration.ofHours(2))
                        || !fresh(profile.path("measuredAt"), Duration.ofDays(30))) continue;
                double eurPerKwh = usdPerDay * usdToEur / (watts * 24 / 1000);
                if (!Double.isFinite(eurPerKwh) || eurPerKwh <= feedIn && eurPerKwh <= grid) continue;
                WorkerChoice candidate = new WorkerChoice(id, coinId, watts, eurPerKwh, localPowerMode);
                if (best == null || candidate.eurPerKwh() > best.eurPerKwh()) best = candidate;
            }
            if (best != null) choices.add(best);
        }
        choices.sort(Comparator.comparingDouble(WorkerChoice::eurPerKwh).reversed());
        List<WorkerChoice> selected = new ArrayList<>();
        double pvRemaining = pvBudgetWatts;
        for (WorkerChoice choice : choices) {
            EnergyTier tier = energyTier(choice.eurPerKwh(), choice.watts(), soc, reserveSoc, gridBatteryFloor,
                    pvRemaining, feedIn, grid);
            if (tier == EnergyTier.GRID) selected.add(choice);
            else if (tier == EnergyTier.PV) {
                selected.add(choice);
                if (soc < reserveSoc) pvRemaining -= choice.watts();
            }
        }
        var plan = json.createObjectNode();
        plan.put("planId", UUID.randomUUID().toString());
        plan.put("validForSeconds", 90);
        plan.putNull("targetWatts");
        var assignments = plan.putArray("assignments");
        var activeIds = plan.putArray("activeWorkerIds");
        for (WorkerChoice choice : selected) {
            if (!choice.localPowerMode()) assignments.addObject().put("workerId", choice.id()).put("coin", choice.coin());
            activeIds.add(choice.id());
        }
        JsonNode applied = api.applyAgentEconomicPlan(miner.getDetails(), plan);
        return applied != null && applied.path("accepted").asBoolean()
                ? new DispatchResult(Math.max(0, pvBudgetWatts - pvRemaining),
                    selected.stream().mapToDouble(WorkerChoice::watts).sum()) : DispatchResult.NONE;
    }

    private DispatchResult dispatchWholeMiner(MinerEntity<?> miner, double soc, double reserveSoc, double gridBatteryFloor, double pvBudgetWatts,
                                    double feedIn, double grid, double usdToEur) {
        MinerStats stats = queries.getLastResult(miner, MinerStats.DEFAULT);
        boolean observedRunning = stats.miningStatus() == MinerStats.MinerStatus.MINING;
        boolean freshTelemetry = queries.getLastSuccessfulQueryAt(miner)
                .map(time -> !time.isBefore(Instant.now().minus(Duration.ofMinutes(5)))).orElse(false);
        if (observedRunning && freshTelemetry && stats.terahashPerSecond() > 0 && stats.miningPowerWatts() > 0)
            lastRunningStats.put(miner.getId(), new TimedStats(stats, Instant.now()));
        MinerStats estimateStats = observedRunning
                ? (freshTelemetry ? stats : MinerStats.DEFAULT)
                : priorOrEstimatedStats(miner, stats);
        MiningEarningsSource source = earningsSources.stream().filter(value -> value.supports(miner)).findFirst().orElse(null);
        boolean run = false;
        double pvWatts = 0;
        double selectedWatts = 0;
        if (source != null) {
            try {
                var forecasts = source.forecast(miner, estimateStats);
                if (!forecasts.isEmpty() && forecasts.stream().allMatch(value -> value.available())) {
                    double watts = forecasts.stream().mapToDouble(value -> value.watts()).sum();
                    double usdPerDay = forecasts.stream().mapToDouble(value -> value.usdPerDay()).sum();
                    if (watts > 0 && usdPerDay > 0) {
                        double eurPerKwh = usdPerDay * usdToEur / (watts * 24 / 1000);
                        EnergyTier tier = energyTier(eurPerKwh, watts, soc, reserveSoc, gridBatteryFloor, pvBudgetWatts, feedIn, grid);
                        run = tier != EnergyTier.OFF;
                        if (run) selectedWatts = watts;
                        if (tier == EnergyTier.PV && soc < reserveSoc) pvWatts = watts;
                    }
                }
            } catch (RuntimeException ignored) { /* Unknown economics pauses a miner controlled by this rule. */ }
        }
        Boolean previous = lastMinerDecision.get(miner.getId());
        if (previous != null && previous == run && observedRunning == run)
            return new DispatchResult(pvWatts, selectedWatts);
        var controller = controllers.getController(miner);
        boolean applied;
        if (run) {
            applied = controller.resumeMining() || controller.startMining();
        } else applied = controller.pauseMining();
        if (applied) lastMinerDecision.put(miner.getId(), run);
        return applied ? new DispatchResult(pvWatts, selectedWatts) : DispatchResult.NONE;
    }

    MinerStats priorOrEstimatedStats(MinerEntity<?> miner, MinerStats observed) {
        TimedStats prior = lastRunningStats.get(miner.getId());
        if (prior != null && !prior.at().isBefore(Instant.now().minus(Duration.ofDays(30)))) return prior.stats();
        long watts = miner.getMaxPowerTarget();
        var efficiency = efficiencies.estimate(miner, watts);
        Double joulesPerTh = efficiency.efficiencyJTh();
        if (joulesPerTh == null || !Double.isFinite(joulesPerTh) || joulesPerTh <= 0 || watts <= 0)
            return observed;
        if (efficiency.source() == MinerEfficiencyLearningService.EfficiencySource.LEARNED
                && efficiencies.getProfiles(miner.getId()).stream().noneMatch(profile ->
                java.util.Objects.equals(profile.getPowerTargetBucketWatts(), efficiency.powerTargetBucketWatts())
                && profile.getLastObservedAt() != null
                && !profile.getLastObservedAt().isBefore(Instant.now().minus(Duration.ofDays(30))))) return observed;
        return new MinerStats(observed.minerIdentity(), observed.name(), MinerStats.MinerStatus.MINING,
                watts, observed.minPowerTarget(), observed.defaultPowerTarget(), observed.maxPowerTarget(),
                watts, watts / joulesPerTh, observed.temperatureCelsius(), observed.pools(), observed.workers());
    }

    private static boolean fresh(JsonNode timestamp, Duration age) {
        try {
            return !Instant.parse(timestamp.asText()).isBefore(Instant.now().minus(age));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static EnergyTier energyTier(double eurPerKwh, double watts, double soc, double reserveSoc, double gridBatteryFloor,
                                 double pvSurplusWatts, double feedIn, double requiredGrid) {
        if (!Double.isFinite(eurPerKwh) || !(watts > 0)) return EnergyTier.OFF;
        if (soc < gridBatteryFloor) return eurPerKwh > feedIn && watts <= pvSurplusWatts ? EnergyTier.PV : EnergyTier.OFF;
        if (eurPerKwh > requiredGrid) return EnergyTier.GRID;
        if (eurPerKwh > feedIn && (soc >= reserveSoc || watts <= pvSurplusWatts)) return EnergyTier.PV;
        return EnergyTier.OFF;
    }

    enum EnergyTier { OFF, PV, GRID }

    record DispatchResult(double pvWatts, double selectedWatts) {
        private static final DispatchResult NONE = new DispatchResult(0, 0);
    }

    private record WorkerChoice(String id, String coin, double watts, double eurPerKwh, boolean localPowerMode) { }
    private record TimedStats(MinerStats stats, Instant at) { }
}
