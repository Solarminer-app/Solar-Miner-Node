package de.verdox.pv_miner.miningcontroller.dsl;

import com.influxdb.query.FluxRecord;
import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.influx.InfluxService;
import de.verdox.pv_miner.influx.InfluxUtil;
import de.verdox.pv_miner.miner.MinerEntityController;
import de.verdox.pv_miner.pvsite.panels.PVPanels;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteInfluxStrategy;
import de.verdox.vserializer.SerializableField;
import de.verdox.vserializer.generic.Serializer;
import de.verdox.vserializer.generic.SerializerBuilder;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ControllerDSL {
    private static final Logger LOGGER = Logger.getLogger(ControllerDSL.class.getName());

    public enum ActionTargetType {
        SINGLE_MINER,
        ALL_MINERS,
        CLUSTER_DYNAMIC
    }

    public enum MinerDistributionStrategy {
        SEQUENTIAL,
        EQUAL_DISTRIBUTION,
        EFFICIENCY_FIRST,
    }

    @Getter
    public enum Comparator {
        EQUAL("=") {
            @Override
            public boolean compare(double valueFromPVSite, double ruleValue) {
                return valueFromPVSite == ruleValue;
            }
        },
        LESS_OR_EQUAL("<=") {
            @Override
            public boolean compare(double valueFromPVSite, double ruleValue) {
                return valueFromPVSite <= ruleValue;
            }
        },
        GREATER_OR_EQUAL(">=") {
            @Override
            public boolean compare(double valueFromPVSite, double ruleValue) {
                return valueFromPVSite >= ruleValue;
            }
        },
        LESS("<") {
            @Override
            public boolean compare(double valueFromPVSite, double ruleValue) {
                return valueFromPVSite < ruleValue;
            }
        },
        GREATER(">") {
            @Override
            public boolean compare(double valueFromPVSite, double ruleValue) {
                return valueFromPVSite > ruleValue;
            }
        };

        private final String symbol;

        Comparator(String symbol) {
            this.symbol = symbol;
        }

        public abstract boolean compare(double valueFromPVSite, double ruleValue);
    }

    public record ValueAdjustment(InfluxUtil.AggregateOperation valueFunction, int timeValue, TimeUnit timeUnit) {
        public static final Serializer<ValueAdjustment> SERIALIZER = SerializerBuilder.create("value_adjustment", ValueAdjustment.class)
                .constructor(
                        new SerializableField<>("valueFunction", Serializer.Enum.create("function", InfluxUtil.AggregateOperation.class), ValueAdjustment::valueFunction),
                        new SerializableField<>("timeValue", Serializer.Primitive.INTEGER, ValueAdjustment::timeValue),
                        new SerializableField<>("timeUnit", Serializer.Enum.create("timeUnit", TimeUnit.class), ValueAdjustment::timeUnit),
                        ValueAdjustment::new
                )
                .build();
    }

    @Getter
    public enum PVSiteVariableType {
        BATTERY_SOC("Battery SoC in % [0;100]") {
            @Override
            public double getValueFromPVSite(PVSiteEntity pvSiteEntity, ValueAdjustment valueAdjustment) {
                return queryInflux(pvSiteEntity, valueAdjustment, PVSiteInfluxStrategy.BATTERY_STATE_OF_CHARGE);
            }
        },
        IMPORT_POWER_KW("Total import power used from the grid") {
            @Override
            public double getValueFromPVSite(PVSiteEntity pvSiteEntity, ValueAdjustment valueAdjustment) {
                return queryInflux(pvSiteEntity, valueAdjustment, PVSiteInfluxStrategy.FEED_IN_POWER_IN_KW);
            }
        },
        CURRENT_HOUR_OF_DAY("Current time as hour of day [0.0 ; 24.0)") {
            @Override
            public double getValueFromPVSite(PVSiteEntity pvSiteEntity, ValueAdjustment valueAdjustment) {
                java.time.LocalTime now = java.time.LocalTime.now(pvSiteEntity.getZoneId());
                return now.getHour() + (now.getMinute() / 60.0);
            }
        },
        SUNRISE_HOUR("Time of sunrise as decimal hour [0.0 ; 24.0)") {
            @Override
            public double getValueFromPVSite(PVSiteEntity pvSiteEntity, ValueAdjustment valueAdjustment) {
                return calculateSunEvent(pvSiteEntity, true);
            }
        },
        SUNSET_HOUR("Time of sunset as decimal hour [0.0 ; 24.0)") {
            @Override
            public double getValueFromPVSite(PVSiteEntity pvSiteEntity, ValueAdjustment valueAdjustment) {
                return calculateSunEvent(pvSiteEntity, false);
            }
        },
        IS_DAYLIGHT("1.0 if sun is up, 0.0 if sun is down") {
            @Override
            public double getValueFromPVSite(PVSiteEntity pvSiteEntity, ValueAdjustment valueAdjustment) {
                double current = CURRENT_HOUR_OF_DAY.getValueFromPVSite(pvSiteEntity, valueAdjustment);
                double sunrise = SUNRISE_HOUR.getValueFromPVSite(pvSiteEntity, valueAdjustment);
                double sunset = SUNSET_HOUR.getValueFromPVSite(pvSiteEntity, valueAdjustment);

                if (sunrise == 0.0 && sunset == 24.0) return 1.0;
                if (sunrise == 24.0 && sunset == 0.0) return 0.0;

                return (current >= sunrise && current <= sunset) ? 1.0 : 0.0;
            }
        },
        BATTERY_POWER_KW("Total battery power [positive = charging]") {
            @Override
            public double getValueFromPVSite(PVSiteEntity pvSiteEntity, ValueAdjustment valueAdjustment) {
                return queryInflux(pvSiteEntity, valueAdjustment, PVSiteInfluxStrategy.BATTERY_CHARGE_POWER);
            }
        },
        LOADS_KW("Total power used in kw") {
            @Override
            public double getValueFromPVSite(PVSiteEntity pvSiteEntity, ValueAdjustment valueAdjustment) {
                return queryInflux(pvSiteEntity, valueAdjustment, PVSiteInfluxStrategy.LOADS_POWER_IN_KW);
            }
        },
        MINER_POWER_KW("Total power used by all miners in kw") {
            @Override
            public double getValueFromPVSite(PVSiteEntity pvSiteEntity, ValueAdjustment valueAdjustment) {
                return queryInflux(pvSiteEntity, valueAdjustment, PVSiteInfluxStrategy.MINER_POWER_IN_KW);
            }
        },
        PV_PRODUCTION("PV Production in kW") {
            @Override
            public double getValueFromPVSite(PVSiteEntity pvSiteEntity, ValueAdjustment valueAdjustment) {
                return queryInflux(pvSiteEntity, valueAdjustment, PVSiteInfluxStrategy.PV_POWER_IN_KW);
            }
        },
        POTENTIAL_PV_SURPLUS("Potential pv surplus in kW minus all miner power.") {
            @Override
            public double getValueFromPVSite(PVSiteEntity pvSiteEntity, ValueAdjustment valueAdjustment) {
                double pvProduction = PV_PRODUCTION.getValueFromPVSite(pvSiteEntity, valueAdjustment);
                double minerPower = MINER_POWER_KW.getValueFromPVSite(pvSiteEntity, valueAdjustment);
                double loadsPowerKW = LOADS_KW.getValueFromPVSite(pvSiteEntity, valueAdjustment);
                return Math.max(0, pvProduction - (loadsPowerKW - minerPower));
            }
        },
        PV_SURPLUS("PV Surplus in kW") {
            @Override
            public double getValueFromPVSite(PVSiteEntity pvSiteEntity, ValueAdjustment valueAdjustment) {
                double pvProduction = PV_PRODUCTION.getValueFromPVSite(pvSiteEntity, valueAdjustment);
                double loadsPower = queryInflux(pvSiteEntity, valueAdjustment, PVSiteInfluxStrategy.LOADS_POWER_IN_KW);
                return Math.max(0, pvProduction - loadsPower);
            }
        },
        PV_SURPLUS_RATIO("PV Surplus Ratio [0.0 ; 1.0]") {
            @Override
            public double getValueFromPVSite(PVSiteEntity pvSiteEntity, ValueAdjustment valueAdjustment) {
                double surplusKw = PV_SURPLUS.getValueFromPVSite(pvSiteEntity, valueAdjustment);
                double capacityKwp = Math.max(1.0, pvSiteEntity.getKwp());
                return surplusKw / capacityKwp;
            }
        },
        POTENTIAL_PV_SURPLUS_RATIO("PV Surplus Ratio [0.0 ; 1.0]") {
            @Override
            public double getValueFromPVSite(PVSiteEntity pvSiteEntity, ValueAdjustment valueAdjustment) {
                double surplusKw = POTENTIAL_PV_SURPLUS.getValueFromPVSite(pvSiteEntity, valueAdjustment);
                double capacityKwp = Math.max(1.0, pvSiteEntity.getKwp());
                return surplusKw / capacityKwp;
            }
        };

        private final String display;

        PVSiteVariableType(String display) {
            this.display = display;
        }

        public abstract double getValueFromPVSite(PVSiteEntity pvSiteEntity, ValueAdjustment valueAdjustment);

        protected double queryInflux(PVSiteEntity pvSiteEntity, ValueAdjustment valueAdjustment, String field) {
            Instant start = Instant.now().minus(Duration.of(valueAdjustment.timeValue(), valueAdjustment.timeUnit().toChronoUnit()));
            Instant end = Instant.now();
            List<FluxRecord> any = SpringContextHelper.getBean(InfluxService.class).queryDataFromApi(pvSiteEntity, start, end, influxQueryBuilder -> {
                influxQueryBuilder
                        .setAggregation(valueAdjustment.valueFunction(), Duration.of(valueAdjustment.timeValue(), valueAdjustment.timeUnit().toChronoUnit()))
                        .addField(field);
            });
            if (any.isEmpty()) {
                throw new NoSuchElementException("No single data point could be queried from influx");
            }
            return ((Number) any.getFirst().getValue()).doubleValue();
        }
    }

    public interface Condition {
        boolean evaluate(PVSiteEntity pvSiteEntity);

        Serializer<Condition> SERIALIZER = Serializers.CONDITION_SERIALIZER;

        enum LogicalOperator {AND, OR, NOT}

        record LogicalCondition(LogicalOperator operator, List<Condition> subConditions) implements Condition {
            public static final Serializer<LogicalCondition> SERIALIZER = Serializers.LOGICAL_CONDITION_SERIALIZER;

            @Override
            public boolean evaluate(PVSiteEntity pvSiteEntity) {
                if (subConditions.isEmpty()) return true;
                return switch (operator) {
                    case AND -> subConditions.stream().allMatch(c -> c.evaluate(pvSiteEntity));
                    case OR -> subConditions.stream().anyMatch(c -> c.evaluate(pvSiteEntity));
                    case NOT -> !subConditions.getFirst().evaluate(pvSiteEntity);
                };
            }
        }

        record Predicate(PVSiteVariableType pvSiteVariableType, ValueAdjustment scope, Comparator comparator,
                         double value) implements Condition {
            public static final Serializer<Predicate> SERIALIZER = Serializers.PREDICATE_SERIALIZER;

            @Override
            public boolean evaluate(PVSiteEntity pvSiteEntity) {
                double valueFromPVSite = pvSiteVariableType.getValueFromPVSite(pvSiteEntity, scope);
                return comparator.compare(valueFromPVSite, value);
            }
        }

        class Serializers {
            private static final Serializer.Types<Condition> CONDITION_TYPES = Serializer.Types.create("condition", Condition.class);
            public static final Serializer<Condition> CONDITION_SERIALIZER = CONDITION_TYPES;

            public static final Serializer<Predicate> PREDICATE_SERIALIZER = SerializerBuilder.create("predicate", Predicate.class)
                    .constructor(
                            new SerializableField<>("variable", Serializer.Enum.create("variable", PVSiteVariableType.class), Predicate::pvSiteVariableType),
                            new SerializableField<>("scope", ValueAdjustment.SERIALIZER, Predicate::scope),
                            new SerializableField<>("comparator", Serializer.Enum.create("comparator", Comparator.class), Predicate::comparator),
                            new SerializableField<>("value", Serializer.Primitive.DOUBLE, Predicate::value),
                            Predicate::new
                    ).build();

            public static final Serializer<LogicalCondition> LOGICAL_CONDITION_SERIALIZER = SerializerBuilder.create("logical_condition", LogicalCondition.class)
                    .constructor(
                            new SerializableField<>("operator", Serializer.Enum.create("operator", LogicalOperator.class), LogicalCondition::operator),
                            new SerializableField<>("subConditions", Serializer.Collection.create(CONDITION_SERIALIZER, ArrayList::new), LogicalCondition::subConditions),
                            LogicalCondition::new
                    ).build();

            static {
                CONDITION_TYPES.type("logical", LOGICAL_CONDITION_SERIALIZER);
                CONDITION_TYPES.type("predicate", PREDICATE_SERIALIZER);
            }
        }
    }

    public sealed interface ValueExpression permits ValueExpression.ClusterCapacityPercentage, ValueExpression.Constant, ValueExpression.DynamicVariable {
        double evaluate(PVSiteEntity pvSiteEntity, double clusterCapacityWatts);

        Serializer<ValueExpression> SERIALIZER = Serializers.EXPRESSION_SERIALIZER;

        record Constant(double valueWatts) implements ValueExpression {
            public static final Serializer<Constant> SERIALIZER = Serializers.CONSTANT_SERIALIZER;

            @Override
            public double evaluate(PVSiteEntity pvSiteEntity, double clusterCapacityWatts) {
                return valueWatts;
            }
        }

        record DynamicVariable(PVSiteVariableType variable, ValueAdjustment adjustment, double multiplier,
                               double offset) implements ValueExpression {
            public static final Serializer<DynamicVariable> SERIALIZER = Serializers.DYNAMIC_VARIABLE_SERIALIZER;

            @Override
            public double evaluate(PVSiteEntity pvSiteEntity, double clusterCapacityWatts) {
                double baseValue = variable.getValueFromPVSite(pvSiteEntity, adjustment);
                return (baseValue * multiplier) + offset;
            }
        }

        record ClusterCapacityPercentage(double percentage) implements ValueExpression {
            public static final Serializer<ClusterCapacityPercentage> SERIALIZER = Serializers.CAPACITY_PERCENTAGE_SERIALIZER;

            @Override
            public double evaluate(PVSiteEntity pvSiteEntity, double clusterCapacityWatts) {
                return clusterCapacityWatts * percentage;
            }
        }

        class Serializers {
            private static final Serializer.Types<ValueExpression> EXPRESSION_TYPES = Serializer.Types.create("value_expression", ValueExpression.class);
            public static final Serializer<ValueExpression> EXPRESSION_SERIALIZER = EXPRESSION_TYPES;

            public static final Serializer<Constant> CONSTANT_SERIALIZER = SerializerBuilder.create("constant", Constant.class)
                    .constructor(
                            new SerializableField<>("valueWatts", Serializer.Primitive.DOUBLE, Constant::valueWatts),
                            Constant::new
                    ).build();

            public static final Serializer<DynamicVariable> DYNAMIC_VARIABLE_SERIALIZER = SerializerBuilder.create("dynamic_variable", DynamicVariable.class)
                    .constructor(
                            new SerializableField<>("variable", Serializer.Enum.create("variable", PVSiteVariableType.class), DynamicVariable::variable),
                            new SerializableField<>("adjustment", ValueAdjustment.SERIALIZER, DynamicVariable::adjustment),
                            new SerializableField<>("multiplier", Serializer.Primitive.DOUBLE, DynamicVariable::multiplier),
                            new SerializableField<>("offset", Serializer.Primitive.DOUBLE, DynamicVariable::offset),
                            DynamicVariable::new
                    ).build();

            public static final Serializer<ClusterCapacityPercentage> CAPACITY_PERCENTAGE_SERIALIZER = SerializerBuilder.create("capacity_percentage", ClusterCapacityPercentage.class)
                    .constructor(
                            new SerializableField<>("percentage", Serializer.Primitive.DOUBLE, ClusterCapacityPercentage::percentage),
                            ClusterCapacityPercentage::new
                    ).build();

            static {
                EXPRESSION_TYPES.type("constant", CONSTANT_SERIALIZER);
                EXPRESSION_TYPES.type("dynamic_variable", DYNAMIC_VARIABLE_SERIALIZER);
                EXPRESSION_TYPES.type("capacity_percentage", CAPACITY_PERCENTAGE_SERIALIZER);
            }
        }
    }

    public record ControllerAction(
            ControllerActionType<?> controllerActionType,
            ActionTargetType targetType,
            MinerDistributionStrategy strategy,
            ValueExpression valueExpression,
            int stepSizeWatts
    ) {
        public static final Serializer<ControllerAction> SERIALIZER = SerializerBuilder.create("controller_action", ControllerAction.class)
                .constructor(
                        new SerializableField<>("actionType", ControllerActionType.SERIALIZER, ControllerAction::controllerActionType),
                        new SerializableField<>("targetType", Serializer.Enum.create("target_type", ActionTargetType.class), ControllerAction::targetType),
                        new SerializableField<>("strategy", Serializer.Enum.create("strategy", MinerDistributionStrategy.class), ControllerAction::strategy),
                        new SerializableField<>("valueExpression", ValueExpression.SERIALIZER, ControllerAction::valueExpression),
                        new SerializableField<>("stepSizeWatts", Serializer.Primitive.INTEGER, ControllerAction::stepSizeWatts),
                        ControllerAction::new
                ).build();
    }

    public record OperatingMode(
            String modeName,
            ControllerDSL.Condition startCondition,
            ControllerDSL.Condition stopCondition,
            List<ControllerDSL.ControllerAction> actions,
            Duration minRunTime,
            Duration minIdleTime,
            Duration powerChangeLockTime
    ) {
        public static final Serializer<OperatingMode> SERIALIZER = SerializerBuilder.create("operating_mode", OperatingMode.class)
                .constructor(
                        new SerializableField<>("modeName", Serializer.Primitive.STRING, OperatingMode::modeName),
                        new SerializableField<>("startCondition", ControllerDSL.Condition.SERIALIZER, OperatingMode::startCondition),
                        new SerializableField<>("stopCondition", ControllerDSL.Condition.SERIALIZER, OperatingMode::stopCondition),
                        new SerializableField<>("actions", Serializer.Collection.create(ControllerDSL.ControllerAction.SERIALIZER, ArrayList::new), OperatingMode::actions),
                        new SerializableField<>("minRunTimeMinutes", Serializer.Primitive.LONG, mode -> mode.minRunTime().toMinutes()),
                        new SerializableField<>("minIdleTimeMinutes", Serializer.Primitive.LONG, mode -> mode.minIdleTime().toMinutes()),
                        new SerializableField<>("powerChangeLockTimeMinutes", Serializer.Primitive.LONG, mode -> mode.powerChangeLockTime() != null ? mode.powerChangeLockTime().toMinutes() : 8L),
                        (modeName, start, stop, actions, runMins, idleMins, powerMins) -> new OperatingMode(
                                modeName,
                                start,
                                stop,
                                actions,
                                Duration.ofMinutes(runMins),
                                Duration.ofMinutes(idleMins),
                                Duration.ofMinutes(powerMins)
                        )
                ).build();
    }

    public static class ControllerActionType<T> implements BiFunction<MinerEntityController, String, Boolean> {
        private static final Map<String, ControllerActionType<?>> actions = new HashMap<>();
        private static final Map<ControllerActionType<?>, String> actionsToIDMapping = new HashMap<>();

        public static final Serializer<ControllerActionType> SERIALIZER = SerializerBuilder.createObjectToPrimitiveSerializer("controller_action", ControllerActionType.class, Serializer.Primitive.STRING, actionsToIDMapping::get, actions::get);

        public static final ControllerActionType<Long> SET_POWER_TARGET = register("power", MinerEntityController::setPowerTarget, Long::parseLong);
        public static final ControllerActionType<Void> PAUSE = register("pause", MinerEntityController::pauseMining);
        public static final ControllerActionType<Void> RESUME = register("resume", miner -> {
            var result = miner.resumeMining();
            if (!result) {
                result = miner.startMining();
            }
            return result;
        });

        private static ControllerActionType<Void> register(String name, Function<MinerEntityController, Boolean> action) {
            return register(name, new ControllerActionType<>(name, (miner, unused) -> action.apply(miner), s -> null));
        }

        private static <T> ControllerActionType<T> register(String name, BiFunction<MinerEntityController, T, Boolean> action, Function<String, T> toString) {
            return register(name, new ControllerActionType<>(name, action, toString));
        }

        private static <T> ControllerActionType<T> register(String name, ControllerActionType<T> action) {
            actions.put(name, action);
            actionsToIDMapping.put(action, name);
            return action;
        }

        @Getter
        private final String name;
        private final BiFunction<MinerEntityController, T, Boolean> controllerLogic;
        private final Function<String, T> stringToValueParser;

        private ControllerActionType(String name, BiFunction<MinerEntityController, T, Boolean> controllerLogic, Function<String, T> stringToValueParser) {
            this.name = name;
            this.controllerLogic = controllerLogic;
            this.stringToValueParser = stringToValueParser;
        }

        @Override
        public Boolean apply(MinerEntityController miner, String s) {
            try {
                if (s != null && stringToValueParser != null) {
                    T parsedValue = stringToValueParser.apply(s);
                    if (parsedValue != null) {
                        return controllerLogic.apply(miner, parsedValue);
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Fehler beim Anwenden der ControllerAction '" + name + "' mit Wert: " + s, e);
            }
            return controllerLogic.apply(miner, null);
        }
    }

    private static double calculateSunEvent(PVSiteEntity pvSiteEntity, boolean isSunrise) {
        Set<PVPanels> panels = pvSiteEntity.getPvPanels();

        if (panels == null || panels.isEmpty()) {
            return isSunrise ? 6.0 : 18.0;
        }

        double latSum = 0.0;
        double lonSum = 0.0;
        for (PVPanels panel : panels) {
            latSum += panel.getLatitudeDeg();
            lonSum += panel.getLongitudeDeg();
        }
        double centroidLat = latSum / panels.size();
        double centroidLon = lonSum / panels.size();

        java.time.ZoneId zoneId = java.time.ZoneId.systemDefault();
        if (pvSiteEntity.getTimezoneId() != null && !pvSiteEntity.getTimezoneId().isBlank()) {
            zoneId = java.time.ZoneId.of(pvSiteEntity.getTimezoneId());
        }

        org.shredzone.commons.suncalc.SunTimes times = org.shredzone.commons.suncalc.SunTimes.compute()
                .on(java.time.ZonedDateTime.now(zoneId))
                .at(centroidLat, centroidLon)
                .execute();

        if (times.isAlwaysUp()) {
            return isSunrise ? 0.0 : 24.0;
        } else if (times.isAlwaysDown()) {
            return isSunrise ? 24.0 : 0.0;
        }

        java.time.ZonedDateTime eventTime = isSunrise ? times.getRise() : times.getSet();

        if (eventTime == null) {
            return isSunrise ? 6.0 : 18.0;
        }
        return eventTime.getHour()
                + (eventTime.getMinute() / 60.0)
                + (eventTime.getSecond() / 3600.0);
    }
}