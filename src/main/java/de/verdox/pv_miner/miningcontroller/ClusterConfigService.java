package de.verdox.pv_miner.miningcontroller;

import com.influxdb.query.FluxRecord;
import de.verdox.pv_miner.dto.ClusterConfigDto;
import de.verdox.pv_miner.dto.ClusterConfigRequests.SaveClusterConfigRequest;
import de.verdox.pv_miner.dto.ClusterConfigRequests.SimulateClusterConfigRequest;
import de.verdox.pv_miner.dto.ClusterSimulationDto;
import de.verdox.pv_miner.influx.InfluxService;
import de.verdox.pv_miner.influx.InfluxUtil;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miningcontroller.dsl.ControllerDSL;
import de.verdox.pv_miner.miningcontroller.dsl.ControllerValueProvider;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteInfluxStrategy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

@Service
public class ClusterConfigService {
    private static final List<String> SIMULATION_FIELDS = List.of(
            PVSiteInfluxStrategy.PV_POWER_IN_KW,
            PVSiteInfluxStrategy.LOADS_POWER_IN_KW,
            PVSiteInfluxStrategy.MINER_POWER_IN_KW,
            PVSiteInfluxStrategy.FEED_IN_POWER_IN_KW,
            PVSiteInfluxStrategy.GRID_CONSUMPTION_POWER,
            PVSiteInfluxStrategy.BATTERY_STATE_OF_CHARGE,
            PVSiteInfluxStrategy.BATTERY_CHARGE_POWER
    );

    private final MinerControllerConfigStorage configStorage;
    private final MinerClusterService clusterService;
    private final InfluxService influxService;

    public ClusterConfigService(
            MinerControllerConfigStorage configStorage,
            MinerClusterService clusterService,
            InfluxService influxService
    ) {
        this.configStorage = configStorage;
        this.clusterService = clusterService;
        this.influxService = influxService;
    }

    public ClusterConfigDto getConfig(String name) throws IOException {
        if (name == null || name.isBlank() || "new".equalsIgnoreCase(name)) {
            return toDto("", false, de.verdox.pv_miner.miningcontroller.dsl.DefaultCluster.DEFAULT);
        }
        String validatedName = validateName(name);
        return toDto(validatedName, true, configStorage.get(validatedName));
    }

    public ClusterConfigDto save(SaveClusterConfigRequest request) throws IOException {
        String name = validateName(request.name());
        String originalName = request.originalName() == null ? "" : request.originalName().trim();
        if (!originalName.isBlank() && !originalName.equals(name)) {
            throw new IllegalArgumentException("Existing cluster configurations cannot be renamed");
        }
        MinerControllerConfig config = fromDtos(request.modes());
        clusterService.saveConfig(name, config);
        return toDto(name, true, config);
    }

    public ClusterSimulationDto simulate(PVSiteEntity site, SimulateClusterConfigRequest request) {
        MinerControllerConfig config = fromDtos(request.modes());
        int intervalMinutes = Math.max(1, Math.min(30, request.intervalMinutes() == null ? 5 : request.intervalMinutes()));
        String sourceType = normalized(request.sourceType(), "PRESET");
        if (!Set.of("PRESET", "HISTORICAL").contains(sourceType)) {
            throw new IllegalArgumentException("Unsupported simulation source");
        }
        NavigableMap<Instant, Map<String, Double>> data;
        LocalDate date;
        String sourceLabel;

        if ("HISTORICAL".equals(sourceType)) {
            date = Objects.requireNonNull(request.historicalDate(), "A historical date is required");
            if (!date.isBefore(LocalDate.now(site.getZoneId()))) {
                throw new IllegalArgumentException("Only completed historical days can be simulated");
            }
            data = loadHistoricalData(site, date, intervalMinutes);
            sourceLabel = date.toString();
            if (data.isEmpty()) {
                throw new IllegalArgumentException("No historical data is available for the selected date");
            }
        } else {
            date = request.historicalDate() == null ? LocalDate.now(site.getZoneId()).minusDays(1) : request.historicalDate();
            String preset = normalized(request.preset(), "SUNNY");
            if (!Set.of("SUNNY", "CLOUDY", "VOLATILE", "LOW_BATTERY").contains(preset)) {
                throw new IllegalArgumentException("Unsupported simulation preset");
            }
            data = createPresetData(site, date, intervalMinutes, preset);
            sourceLabel = preset;
        }

        List<MinerEntity<?>> miners = simulationMiners(site, request.clusterName());
        double capacityWatts = miners.stream().mapToDouble(ClusterUtil::getMaxPowerForMiner).sum();
        if (capacityWatts <= 0) {
            capacityWatts = Math.max(1, site.getMiners().stream().mapToDouble(MinerEntity::getMaxPowerTarget).sum());
        }

        HistoricalValueProvider valueProvider = new HistoricalValueProvider(data);
        ControllerDecisionEngine engine = new ControllerDecisionEngine();
        List<ClusterSimulationDto.SimulationPointDto> points = new ArrayList<>();
        double energyKwh = 0;
        double pvEnergyKwh = 0;
        double gridEnergyKwh = 0;
        double peakTarget = 0;
        long activeMinutes = 0;
        int modeChanges = 0;
        Map<String, Integer> modeTicks = new LinkedHashMap<>();

        for (Map.Entry<Instant, Map<String, Double>> entry : data.entrySet()) {
            valueProvider.moveTo(entry.getKey());
            ControllerDecisionEngine.Decision decision = engine.evaluate(config, valueProvider, site, capacityWatts);
            double target = Math.min(capacityWatts, decision.targetPowerWatts());
            AllocationEstimate allocation = estimateAllocation(miners, decision, target);
            double allocated = allocation.allocatedWatts();
            String mode = decision.activeMode() == null ? "Idle" : decision.activeMode().modeName();
            if (decision.modeChanged()) modeChanges++;
            modeTicks.merge(mode, 1, Integer::sum);

            Map<String, Double> values = entry.getValue();
            double pv = value(values, PVSiteInfluxStrategy.PV_POWER_IN_KW);
            double load = value(values, PVSiteInfluxStrategy.LOADS_POWER_IN_KW);
            double minerPower = value(values, PVSiteInfluxStrategy.MINER_POWER_IN_KW);
            double batterySoc = value(values, PVSiteInfluxStrategy.BATTERY_STATE_OF_CHARGE);
            double potentialSurplus = Math.max(0, pv - Math.max(0, load - minerPower));
            int activeMiners = allocation.activeMiners();
            double intervalHours = intervalMinutes / 60.0;
            double pvPoweredWatts = Math.min(allocated, potentialSurplus * 1000);

            energyKwh += allocated / 1000 * intervalHours;
            pvEnergyKwh += pvPoweredWatts / 1000 * intervalHours;
            gridEnergyKwh += Math.max(0, allocated - pvPoweredWatts) / 1000 * intervalHours;
            peakTarget = Math.max(peakTarget, target);
            if (allocated > 0) activeMinutes += intervalMinutes;

            points.add(new ClusterSimulationDto.SimulationPointDto(
                    entry.getKey(), pv, load, batterySoc, minerPower, potentialSurplus,
                    target, allocated, activeMiners, mode
            ));
        }

        String mostActiveMode = modeTicks.entrySet().stream()
                .filter(entry -> !"Idle".equals(entry.getKey()))
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Idle");

        return new ClusterSimulationDto(
                sourceType,
                sourceLabel,
                date,
                capacityWatts,
                points,
                new ClusterSimulationDto.SimulationSummaryDto(
                        energyKwh, pvEnergyKwh, gridEnergyKwh, peakTarget,
                        modeChanges, activeMinutes, mostActiveMode
                )
        );
    }

    private List<MinerEntity<?>> simulationMiners(PVSiteEntity site, String clusterName) {
        if (clusterName != null && !clusterName.isBlank() && !"new".equalsIgnoreCase(clusterName)) {
            var cluster = clusterService.getCluster(site.getId(), clusterName);
            if (cluster != null && !cluster.getAssignedMiners().isEmpty()) {
                return cluster.getAssignedMiners().stream().sorted(Comparator.comparing(MinerEntity::getId)).toList();
            }
        }
        return site.getMiners().stream().sorted(Comparator.comparing(MinerEntity::getId)).toList();
    }

    private AllocationEstimate estimateAllocation(
            List<MinerEntity<?>> miners,
            ControllerDecisionEngine.Decision decision,
            double targetWatts
    ) {
        boolean pauses = decision.actions().stream()
                .anyMatch(action -> action.controllerActionType() == ControllerDSL.ControllerActionType.PAUSE);
        if (pauses || miners.isEmpty()) return new AllocationEstimate(0, 0);

        ControllerDSL.ControllerAction powerAction = decision.powerAction();
        if (powerAction == null) {
            boolean resumes = decision.actions().stream()
                    .anyMatch(action -> action.controllerActionType() == ControllerDSL.ControllerActionType.RESUME);
            if (!resumes) return new AllocationEstimate(0, 0);
            double resumedPower = miners.stream()
                    .mapToDouble(miner -> miner.getOS().supportsDynamicPowerScaling()
                            ? ClusterUtil.getMinPowerTargetForMiner(miner)
                            : ClusterUtil.getMaxPowerForMiner(miner))
                    .sum();
            return new AllocationEstimate(resumedPower, miners.size());
        }

        if (powerAction.strategy() == ControllerDSL.MinerDistributionStrategy.EQUAL_DISTRIBUTION) {
            return estimateEqualAllocation(miners, powerAction, targetWatts);
        }

        double remaining = targetWatts;
        double allocated = 0;
        int active = 0;
        for (MinerEntity<?> miner : miners) {
            boolean scales = miner.getOS().supportsDynamicPowerScaling();
            double minimum = scales ? ClusterUtil.getMinPowerTargetForMiner(miner) : ClusterUtil.getMaxPowerForMiner(miner);
            double maximum = ClusterUtil.getMaxPowerForMiner(miner);
            int step = ClusterUtil.getEffectiveStepSize(miner, powerAction, scales);
            double minerAllocation = ClusterUtil.roundToStepDown(Math.min(remaining, maximum), step);
            if (remaining < minimum || minerAllocation < minimum) continue;
            allocated += minerAllocation;
            remaining -= minerAllocation;
            active++;
        }
        return new AllocationEstimate(allocated, active);
    }

    private AllocationEstimate estimateEqualAllocation(
            List<MinerEntity<?>> miners,
            ControllerDSL.ControllerAction action,
            double targetWatts
    ) {
        List<MinerEntity<?>> activeMiners = new ArrayList<>();
        Map<MinerEntity<?>, Double> allocations = new LinkedHashMap<>();
        double remaining = targetWatts;
        for (MinerEntity<?> miner : miners) {
            boolean scales = miner.getOS().supportsDynamicPowerScaling();
            double minimum = scales ? ClusterUtil.getMinPowerTargetForMiner(miner) : ClusterUtil.getMaxPowerForMiner(miner);
            if (remaining < minimum) continue;
            activeMiners.add(miner);
            allocations.put(miner, minimum);
            remaining -= minimum;
        }

        boolean progress = true;
        while (remaining > 0 && progress) {
            progress = false;
            List<MinerEntity<?>> adjustable = activeMiners.stream()
                    .filter(miner -> allocations.get(miner) < ClusterUtil.getMaxPowerForMiner(miner))
                    .toList();
            if (adjustable.isEmpty()) break;
            double share = remaining / adjustable.size();
            for (MinerEntity<?> miner : adjustable) {
                boolean scales = miner.getOS().supportsDynamicPowerScaling();
                double current = allocations.get(miner);
                double maximum = ClusterUtil.getMaxPowerForMiner(miner);
                int step = ClusterUtil.getEffectiveStepSize(miner, action, scales);
                double next = ClusterUtil.roundToStepDown(current + Math.min(share, maximum - current), step);
                if (next <= current) continue;
                remaining -= next - current;
                allocations.put(miner, next);
                progress = true;
            }
        }
        return new AllocationEstimate(
                allocations.values().stream().mapToDouble(Double::doubleValue).sum(),
                activeMiners.size()
        );
    }

    private record AllocationEstimate(double allocatedWatts, int activeMiners) {
    }

    private NavigableMap<Instant, Map<String, Double>> loadHistoricalData(
            PVSiteEntity site,
            LocalDate date,
            int intervalMinutes
    ) {
        ZoneId zone = site.getZoneId();
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();
        List<FluxRecord> records = influxService.queryDataFromApi(site, from, to, builder -> {
            SIMULATION_FIELDS.forEach(builder::addField);
            builder.setAggregation(InfluxUtil.AggregateOperation.MEAN, Duration.ofMinutes(intervalMinutes));
        });
        NavigableMap<Instant, Map<String, Double>> result = new TreeMap<>();
        for (FluxRecord record : records) {
            if (record.getTime() == null || record.getField() == null || !(record.getValue() instanceof Number number)) continue;
            result.computeIfAbsent(record.getTime(), ignored -> new LinkedHashMap<>())
                    .put(record.getField(), number.doubleValue());
        }
        return result;
    }

    private NavigableMap<Instant, Map<String, Double>> createPresetData(
            PVSiteEntity site,
            LocalDate date,
            int intervalMinutes,
            String preset
    ) {
        NavigableMap<Instant, Map<String, Double>> result = new TreeMap<>();
        ZoneId zone = site.getZoneId();
        double peakPv = Math.max(8, site.getKwp());
        for (int minute = 0; minute < 24 * 60; minute += intervalMinutes) {
            double hour = minute / 60.0;
            double daylight = hour < 6 || hour > 20 ? 0 : Math.sin((hour - 6) / 14 * Math.PI);
            double variability = 0.78 + 0.22 * Math.sin(minute * 0.13) * Math.sin(minute * 0.031);
            double pvFactor = switch (preset) {
                case "CLOUDY" -> 0.32 * Math.max(0.15, variability);
                case "VOLATILE" -> Math.max(0.08, 0.62 + 0.38 * Math.sin(minute * 0.19));
                case "LOW_BATTERY" -> 0.7;
                default -> 0.92;
            };
            double pv = peakPv * daylight * pvFactor;
            double householdLoad = 0.9 + (hour >= 6 && hour <= 9 ? 1.2 : 0) + (hour >= 17 && hour <= 22 ? 1.8 : 0);
            double batterySoc = switch (preset) {
                case "LOW_BATTERY" -> Math.max(20, Math.min(88, 30 + daylight * 55 - Math.max(0, hour - 18) * 5));
                case "CLOUDY" -> Math.max(45, Math.min(96, 62 + daylight * 28 - Math.max(0, hour - 18) * 3));
                default -> Math.max(55, Math.min(100, 68 + daylight * 38 - Math.max(0, hour - 19) * 4));
            };
            Map<String, Double> values = new LinkedHashMap<>();
            values.put(PVSiteInfluxStrategy.PV_POWER_IN_KW, pv);
            values.put(PVSiteInfluxStrategy.LOADS_POWER_IN_KW, householdLoad);
            values.put(PVSiteInfluxStrategy.MINER_POWER_IN_KW, 0.0);
            values.put(PVSiteInfluxStrategy.FEED_IN_POWER_IN_KW, Math.max(0, householdLoad - pv));
            values.put(PVSiteInfluxStrategy.GRID_CONSUMPTION_POWER, Math.max(0, pv - householdLoad));
            values.put(PVSiteInfluxStrategy.BATTERY_STATE_OF_CHARGE, batterySoc);
            values.put(PVSiteInfluxStrategy.BATTERY_CHARGE_POWER, Math.max(0, pv - householdLoad));
            result.put(date.atStartOfDay(zone).plusMinutes(minute).toInstant(), values);
        }
        return result;
    }

    private ClusterConfigDto toDto(String name, boolean existing, MinerControllerConfig config) {
        List<ClusterConfigDto.OperatingModeDto> modes = config.getConfigEntries().values().stream()
                .map(this::toDto)
                .toList();
        return new ClusterConfigDto(name, existing, modes, options());
    }

    private ClusterConfigDto.OperatingModeDto toDto(ControllerDSL.OperatingMode mode) {
        return new ClusterConfigDto.OperatingModeDto(
                mode.modeName(),
                toDto(mode.startCondition()),
                toDto(mode.stopCondition()),
                mode.actions().stream().map(this::toDto).toList(),
                mode.minRunTime().toMinutes(),
                mode.minIdleTime().toMinutes(),
                mode.powerChangeLockTime() == null ? 8 : mode.powerChangeLockTime().toMinutes()
        );
    }

    private ClusterConfigDto.ConditionDto toDto(ControllerDSL.Condition condition) {
        if (condition instanceof ControllerDSL.Condition.LogicalCondition logical) {
            return new ClusterConfigDto.ConditionDto(
                    "LOGICAL", logical.operator().name(), logical.subConditions().stream().map(this::toDto).toList(),
                    null, null, null, null, null, null
            );
        }
        ControllerDSL.Condition.Predicate predicate = (ControllerDSL.Condition.Predicate) condition;
        return new ClusterConfigDto.ConditionDto(
                "PREDICATE", null, List.of(), predicate.pvSiteVariableType().name(),
                predicate.scope().valueFunction().name(), predicate.scope().timeValue(), predicate.scope().timeUnit().name(),
                predicate.comparator().name(), predicate.value()
        );
    }

    private ClusterConfigDto.ControllerActionDto toDto(ControllerDSL.ControllerAction action) {
        return new ClusterConfigDto.ControllerActionDto(
                action.controllerActionType().getName(), action.targetType().name(), action.strategy().name(),
                toDto(action.valueExpression()), action.stepSizeWatts()
        );
    }

    private ClusterConfigDto.ValueExpressionDto toDto(ControllerDSL.ValueExpression expression) {
        if (expression instanceof ControllerDSL.ValueExpression.Constant constant) {
            return new ClusterConfigDto.ValueExpressionDto("CONSTANT", constant.valueWatts(), null, null, null, null, null, null, null);
        }
        if (expression instanceof ControllerDSL.ValueExpression.ClusterCapacityPercentage percentage) {
            return new ClusterConfigDto.ValueExpressionDto("CAPACITY_PERCENTAGE", null, null, null, null, null, null, null, percentage.percentage());
        }
        ControllerDSL.ValueExpression.DynamicVariable dynamic = (ControllerDSL.ValueExpression.DynamicVariable) expression;
        return new ClusterConfigDto.ValueExpressionDto(
                "DYNAMIC_VARIABLE", null, dynamic.variable().name(), dynamic.adjustment().valueFunction().name(),
                dynamic.adjustment().timeValue(), dynamic.adjustment().timeUnit().name(),
                dynamic.multiplier(), dynamic.offset(), null
        );
    }

    private MinerControllerConfig fromDtos(List<ClusterConfigDto.OperatingModeDto> modeDtos) {
        if (modeDtos == null || modeDtos.isEmpty()) throw new IllegalArgumentException("At least one operating mode is required");
        LinkedHashMap<String, ControllerDSL.OperatingMode> modes = new LinkedHashMap<>();
        for (ClusterConfigDto.OperatingModeDto dto : modeDtos) {
            String name = requireText(dto.name(), "Mode name is required");
            if (modes.containsKey(name)) throw new IllegalArgumentException("Mode names must be unique");
            List<ControllerDSL.ControllerAction> actions = dto.actions() == null ? List.of() : dto.actions().stream().map(this::fromDto).toList();
            if (actions.isEmpty()) throw new IllegalArgumentException("Every mode needs at least one action");
            modes.put(name, new ControllerDSL.OperatingMode(
                    name, fromDto(dto.startCondition()), fromDto(dto.stopCondition()), actions,
                    Duration.ofMinutes(Math.max(0, dto.minRunTimeMinutes())),
                    Duration.ofMinutes(Math.max(0, dto.minIdleTimeMinutes())),
                    Duration.ofMinutes(Math.max(0, dto.powerChangeLockTimeMinutes()))
            ));
        }
        return new MinerControllerConfig(modes);
    }

    private ControllerDSL.Condition fromDto(ClusterConfigDto.ConditionDto dto) {
        Objects.requireNonNull(dto, "Condition is required");
        if ("LOGICAL".equals(normalized(dto.type(), "PREDICATE"))) {
            List<ControllerDSL.Condition> conditions = dto.subConditions() == null ? List.of() : dto.subConditions().stream().map(this::fromDto).toList();
            return new ControllerDSL.Condition.LogicalCondition(
                    ControllerDSL.Condition.LogicalOperator.valueOf(normalized(dto.operator(), "AND")), conditions
            );
        }
        return new ControllerDSL.Condition.Predicate(
                ControllerDSL.PVSiteVariableType.valueOf(requireText(dto.variable(), "Condition variable is required")),
                adjustment(dto.aggregation(), dto.windowValue(), dto.windowUnit()),
                ControllerDSL.Comparator.valueOf(normalized(dto.comparator(), "GREATER_OR_EQUAL")),
                dto.threshold() == null ? 0 : dto.threshold()
        );
    }

    private ControllerDSL.ControllerAction fromDto(ClusterConfigDto.ControllerActionDto dto) {
        String actionType = normalized(dto.actionType(), "power").toLowerCase(Locale.ROOT);
        ControllerDSL.ControllerActionType<?> type = switch (actionType) {
            case "pause" -> ControllerDSL.ControllerActionType.PAUSE;
            case "resume" -> ControllerDSL.ControllerActionType.RESUME;
            case "power" -> ControllerDSL.ControllerActionType.SET_POWER_TARGET;
            default -> throw new IllegalArgumentException("Unsupported controller action: " + actionType);
        };
        return new ControllerDSL.ControllerAction(
                type,
                ControllerDSL.ActionTargetType.valueOf(normalized(dto.targetType(), "CLUSTER_DYNAMIC")),
                ControllerDSL.MinerDistributionStrategy.valueOf(normalized(dto.strategy(), "EFFICIENCY_FIRST")),
                fromDto(dto.valueExpression()),
                Math.max(0, dto.stepSizeWatts())
        );
    }

    private ControllerDSL.ValueExpression fromDto(ClusterConfigDto.ValueExpressionDto dto) {
        if (dto == null) return new ControllerDSL.ValueExpression.Constant(0);
        return switch (normalized(dto.type(), "CONSTANT")) {
            case "DYNAMIC_VARIABLE" -> new ControllerDSL.ValueExpression.DynamicVariable(
                    ControllerDSL.PVSiteVariableType.valueOf(requireText(dto.variable(), "Expression variable is required")),
                    adjustment(dto.aggregation(), dto.windowValue(), dto.windowUnit()),
                    dto.multiplier() == null ? 1 : dto.multiplier(),
                    dto.offset() == null ? 0 : dto.offset()
            );
            case "CAPACITY_PERCENTAGE" -> new ControllerDSL.ValueExpression.ClusterCapacityPercentage(
                    Math.max(0, Math.min(1, dto.percentage() == null ? 0 : dto.percentage()))
            );
            default -> new ControllerDSL.ValueExpression.Constant(dto.constantWatts() == null ? 0 : Math.max(0, dto.constantWatts()));
        };
    }

    private ControllerDSL.ValueAdjustment adjustment(String aggregation, Integer value, String unit) {
        return new ControllerDSL.ValueAdjustment(
                InfluxUtil.AggregateOperation.valueOf(normalized(aggregation, "LAST")),
                Math.max(1, value == null ? 1 : value),
                TimeUnit.valueOf(normalized(unit, "MINUTES"))
        );
    }

    private ClusterConfigDto.ClusterDslOptionsDto options() {
        return new ClusterConfigDto.ClusterDslOptionsDto(
                names(ControllerDSL.PVSiteVariableType.values()),
                names(ControllerDSL.Comparator.values()),
                names(ControllerDSL.Condition.LogicalOperator.values()),
                List.of("power", "pause", "resume"),
                names(ControllerDSL.ActionTargetType.values()),
                names(ControllerDSL.MinerDistributionStrategy.values()),
                names(InfluxUtil.AggregateOperation.values()),
                List.of("MINUTES", "HOURS"),
                List.of("CONSTANT", "DYNAMIC_VARIABLE", "CAPACITY_PERCENTAGE"),
                List.of(
                        new ClusterConfigDto.SimulationPresetDto("SUNNY", "cluster.simulator.preset.sunny", "cluster.simulator.preset.sunny_description"),
                        new ClusterConfigDto.SimulationPresetDto("CLOUDY", "cluster.simulator.preset.cloudy", "cluster.simulator.preset.cloudy_description"),
                        new ClusterConfigDto.SimulationPresetDto("VOLATILE", "cluster.simulator.preset.volatile", "cluster.simulator.preset.volatile_description"),
                        new ClusterConfigDto.SimulationPresetDto("LOW_BATTERY", "cluster.simulator.preset.low_battery", "cluster.simulator.preset.low_battery_description")
                )
        );
    }

    private List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    private String validateName(String name) {
        String result = requireText(name, "Cluster name is required");
        if (!result.matches("[A-Za-z0-9ÄÖÜäöüß _-]{1,80}") || "new".equalsIgnoreCase(result)) {
            throw new IllegalArgumentException("Cluster name contains unsupported characters");
        }
        return result;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private String normalized(String value, String fallback) {
        return (value == null || value.isBlank() ? fallback : value).trim().toUpperCase(Locale.ROOT);
    }

    private double value(Map<String, Double> values, String key) {
        return Math.max(0, values.getOrDefault(key, 0.0));
    }

    private static final class HistoricalValueProvider implements ControllerValueProvider {
        private final NavigableMap<Instant, Map<String, Double>> values;
        private Instant current;

        private HistoricalValueProvider(NavigableMap<Instant, Map<String, Double>> values) {
            this.values = values;
            this.current = values.firstKey();
        }

        private void moveTo(Instant timestamp) {
            this.current = timestamp;
        }

        @Override
        public double getValue(String valueId, PVSiteEntity site, ControllerDSL.ValueAdjustment adjustment) {
            Duration window = Duration.of(adjustment.timeValue(), adjustment.timeUnit().toChronoUnit());
            List<Double> samples = values.subMap(current.minus(window), true, current, true).values().stream()
                    .map(fields -> fields.get(valueId))
                    .filter(Objects::nonNull)
                    .filter(Double::isFinite)
                    .toList();
            if (samples.isEmpty()) return 0;
            return aggregate(samples, adjustment.valueFunction());
        }

        @Override
        public ZonedDateTime getCurrentTime(PVSiteEntity site) {
            return current.atZone(site.getZoneId());
        }

        private double aggregate(List<Double> values, InfluxUtil.AggregateOperation operation) {
            return switch (operation) {
                case FIRST -> values.getFirst();
                case LAST -> values.getLast();
                case MIN -> values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                case MAX -> values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                case SUM, INTEGRAL -> values.stream().mapToDouble(Double::doubleValue).sum();
                case COUNT -> values.size();
                case MEDIAN, MODE -> {
                    List<Double> sorted = values.stream().sorted().toList();
                    int middle = sorted.size() / 2;
                    yield sorted.size() % 2 == 0 ? (sorted.get(middle - 1) + sorted.get(middle)) / 2 : sorted.get(middle);
                }
                case STANDARD_DEVIATION -> {
                    double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    yield Math.sqrt(values.stream().mapToDouble(value -> Math.pow(value - mean, 2)).average().orElse(0));
                }
                case VARIANCE -> {
                    double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    yield values.stream().mapToDouble(value -> Math.pow(value - mean, 2)).average().orElse(0);
                }
                default -> values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            };
        }
    }
}
