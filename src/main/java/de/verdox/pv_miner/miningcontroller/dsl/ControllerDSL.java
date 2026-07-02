package de.verdox.pv_miner.miningcontroller.dsl;

import com.influxdb.query.FluxRecord;
import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.influx.InfluxService;
import de.verdox.pv_miner.influx.InfluxUtil;
import de.verdox.pv_miner.miner.MinerEntityController;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.vserializer.SerializableField;
import de.verdox.vserializer.generic.Serializer;
import de.verdox.vserializer.generic.SerializerBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * The core Domain Specific Language (DSL) and Control logic for the PV-Miner application.
 * It provides the necessary structures to define operating modes, conditions,
 * dynamic value expressions, and the stateful ClusterController to manage large fleets of miners.
 */
public class ControllerDSL {
    private static final Logger LOGGER = Logger.getLogger(ControllerDSL.class.getName());

    /**
     * Defines the target scope of a controller action.
     */
    public enum ActionTargetType {
        SINGLE_MINER,
        ALL_MINERS,
        CLUSTER_DYNAMIC
    }

    /**
     * Strategy used to distribute the calculated power target among miners in a cluster.
     */
    public enum MinerDistributionStrategy {
        /**
         * Fills miners one by one up to their maximum capacity.
         */
        SEQUENTIAL,
        /**
         * Distributes the available power equally among all available miners.
         */
        EQUAL_DISTRIBUTION,
        /** Prioritizes the most efficient miners first. */
        /*EFFICIENCY_FIRST,*/
    }

    /**
     * Defines mathematical comparisons for evaluating rules against PV site data.
     */
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

        public String getSymbol() {
            return symbol;
        }

        public abstract boolean compare(double valueFromPVSite, double ruleValue);
    }

    /**
     * Represents a time-based aggregation (e.g., 30-minute median) to smooth out volatile sensor data.
     */
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

    /**
     * The metrics that can be retrieved and evaluated from a PV Site.
     */
    public enum PVSiteVariableType {
        BATTERY_SOC("Battery SoC in % [0;100]") {
            @Override
            public double getValueFromPVSite(PVSiteEntity pvSiteEntity, ValueAdjustment valueAdjustment) {
                return queryInflux(pvSiteEntity, valueAdjustment, "BatteryStateOfCharge");
            }
        },
        LOADS_KW("Total power used in kw") {
            @Override
            public double getValueFromPVSite(PVSiteEntity pvSiteEntity, ValueAdjustment valueAdjustment) {
                return queryInflux(pvSiteEntity, valueAdjustment, "LoadsPowerInKw");
            }
        },
        MINER_POWER_KW("Total power used by all miners in kw") {
            @Override
            public double getValueFromPVSite(PVSiteEntity pvSiteEntity, ValueAdjustment valueAdjustment) {
                return queryInflux(pvSiteEntity, valueAdjustment, "MinerPowerInKw");
            }
        },
        PV_PRODUCTION("PV Production in kW") {
            @Override
            public double getValueFromPVSite(PVSiteEntity pvSiteEntity, ValueAdjustment valueAdjustment) {
                return queryInflux(pvSiteEntity, valueAdjustment, "PVPowerInKw");
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
                double loadsPower = queryInflux(pvSiteEntity, valueAdjustment, "LoadsPowerInKw");
                return Math.max(0, pvProduction - loadsPower);
            }
        },
        /**
         * Relative ratio to scale seamlessly regardless of the PV site's size.
         * Calculates surplus relative to the installed capacity (e.g., 0.15 = 15% of max capacity).
         */
        PV_SURPLUS_RATIO("PV Surplus Ratio [0.0 ; 1.0]") {
            @Override
            public double getValueFromPVSite(PVSiteEntity pvSiteEntity, ValueAdjustment valueAdjustment) {
                double surplusKw = PV_SURPLUS.getValueFromPVSite(pvSiteEntity, valueAdjustment);
                double capacityKwp = Math.max(1.0, pvSiteEntity.getKwp());
                return surplusKw / capacityKwp;
            }
        },

        /**
         * Relative ratio to scale seamlessly regardless of the PV site's size.
         * Calculates surplus relative to the installed capacity (e.g., 0.15 = 15% of max capacity).
         */
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

        public String getDisplay() {
            return display;
        }

        public abstract double getValueFromPVSite(PVSiteEntity pvSiteEntity, ValueAdjustment valueAdjustment);

        /**
         * Helper method to reduce boilerplate code when querying InfluxDB.
         */
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

    /**
     * A condition that evaluates to true or false based on the current state of the PV site.
     */
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

    /**
     * Dynamically calculates the target power based on runtime variables.
     * Designed to be fully serializable using the custom vserializer.
     */
    public sealed interface ValueExpression permits ValueExpression.ClusterCapacityPercentage, ValueExpression.Constant, ValueExpression.DynamicVariable {
        /**
         * Evaluates the expression to determine the target power in watts.
         *
         * @param pvSiteEntity         The current PV site to draw metrics from.
         * @param clusterCapacityWatts The maximum theoretical capacity of the entire miner cluster.
         * @return The calculated power target in watts.
         */
        double evaluate(PVSiteEntity pvSiteEntity, double clusterCapacityWatts);

        // Static reference to prevent initialization issues during class loading
        Serializer<ValueExpression> SERIALIZER = Serializers.EXPRESSION_SERIALIZER;

        /**
         * A fixed, constant value (e.g., always target 5000 Watts).
         * Useful for absolute fallbacks or simple setups.
         */
        record Constant(double valueWatts) implements ValueExpression {
            public static final Serializer<Constant> SERIALIZER = Serializers.CONSTANT_SERIALIZER;

            @Override
            public double evaluate(PVSiteEntity pvSiteEntity, double clusterCapacityWatts) {
                return valueWatts;
            }
        }

        /**
         * A dynamic calculation based on a PV metric.
         * Formula: (PV_Variable * multiplier) + offset
         * * Example for "Surplus - 5% Buffer in Watts":
         * Variable = PV_SURPLUS, Multiplier = 950 (kW to W minus 5%), Offset = 0
         */
        record DynamicVariable(PVSiteVariableType variable, ValueAdjustment adjustment, double multiplier,
                               double offset) implements ValueExpression {
            public static final Serializer<DynamicVariable> SERIALIZER = Serializers.DYNAMIC_VARIABLE_SERIALIZER;

            @Override
            public double evaluate(PVSiteEntity pvSiteEntity, double clusterCapacityWatts) {
                double baseValue = variable.getValueFromPVSite(pvSiteEntity, adjustment);
                return (baseValue * multiplier) + offset;
            }
        }

        /**
         * Calculates a target based purely on the cluster's capabilities.
         * Example: Run cluster at 50% capacity -> percentage = 0.5
         */
        record ClusterCapacityPercentage(double percentage) implements ValueExpression {
            public static final Serializer<ClusterCapacityPercentage> SERIALIZER = Serializers.CAPACITY_PERCENTAGE_SERIALIZER;

            @Override
            public double evaluate(PVSiteEntity pvSiteEntity, double clusterCapacityWatts) {
                return clusterCapacityWatts * percentage;
            }
        }

        /**
         * Inner class to handle serialization registration safely.
         */
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

    /**
     * Defines an action to be executed on a cluster or miner, utilizing dynamic value expressions.
     */
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
                        new SerializableField<>("stepSizeWatts", Serializer.Primitive.INTEGER, ControllerAction::stepSizeWatts), // NEU
                        ControllerAction::new
                ).build();
    }

    /**
     * NEW: Represents an operating state with hysteresis to prevent flapping.
     */
    public record OperatingMode(
            String modeName,
            ControllerDSL.Condition startCondition,
            ControllerDSL.Condition stopCondition,
            List<ControllerDSL.ControllerAction> actions, // <-- Jetzt eine Liste
            Duration minRunTime,
            Duration minIdleTime
    ) {
        public static final Serializer<OperatingMode> SERIALIZER = SerializerBuilder.create("operating_mode", OperatingMode.class)
                .constructor(
                        new SerializableField<>("modeName", Serializer.Primitive.STRING, OperatingMode::modeName),
                        new SerializableField<>("startCondition", ControllerDSL.Condition.SERIALIZER, OperatingMode::startCondition),
                        new SerializableField<>("stopCondition", ControllerDSL.Condition.SERIALIZER, OperatingMode::stopCondition),
                        new SerializableField<>("actions", Serializer.Collection.create(ControllerDSL.ControllerAction.SERIALIZER, ArrayList::new), OperatingMode::actions),
                        new SerializableField<>("minRunTimeMinutes", Serializer.Primitive.LONG, mode -> mode.minRunTime().toMinutes()),
                        new SerializableField<>("minIdleTimeMinutes", Serializer.Primitive.LONG, mode -> mode.minIdleTime().toMinutes()),
                        (modeName, start, stop, actions, runMins, idleMins) -> new OperatingMode(
                                modeName,
                                start,
                                stop,
                                actions,
                                Duration.ofMinutes(runMins),
                                Duration.ofMinutes(idleMins)
                        )
                ).build();
    }

    /**
     * Maps action definitions to the underlying API calls for the miners.
     */
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

        private final String name;
        private final BiFunction<MinerEntityController, T, Boolean> controllerLogic;
        private final Function<String, T> stringToValueParser;

        private ControllerActionType(String name, BiFunction<MinerEntityController, T, Boolean> controllerLogic, Function<String, T> stringToValueParser) {
            this.name = name;
            this.controllerLogic = controllerLogic;
            this.stringToValueParser = stringToValueParser;
        }

        public String getName() {
            return name;
        }

        @Override
        public Boolean apply(MinerEntityController miner, String s) {
            if (stringToValueParser.apply(s) != null) {
                return controllerLogic.apply(miner, stringToValueParser.apply(s));
            }
            return controllerLogic.apply(miner, null);
        }
    }
}