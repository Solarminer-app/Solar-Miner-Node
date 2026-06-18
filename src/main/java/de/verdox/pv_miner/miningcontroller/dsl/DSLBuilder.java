package de.verdox.pv_miner.miningcontroller.dsl;

import de.verdox.pv_miner.influx.InfluxUtil;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import de.verdox.pv_miner.miningcontroller.dsl.ControllerDSL.*;

/**
 * A fluent builder to construct immutable OperatingMode instances.
 */
public class DSLBuilder {
    private final String modeName;
    private Condition startCondition;
    private Condition stopCondition;
    private final List<ControllerAction> actions = new ArrayList<>();
    private Duration minRunTime = Duration.ofMinutes(15);
    private Duration minIdleTime = Duration.ofMinutes(5);

    private DSLBuilder(String modeName) {
        this.modeName = modeName;
    }

    public static DSLBuilder create(String modeName) {
        return new DSLBuilder(modeName);
    }

    public DSLBuilder startWhen(Condition condition) {
        this.startCondition = condition;
        return this;
    }

    public DSLBuilder stopWhen(Condition condition) {
        this.stopCondition = condition;
        return this;
    }

    public DSLBuilder execute(ControllerAction action) {
        this.actions.add(action);
        return this;
    }

    public DSLBuilder withHardwareLocks(Duration minRunTime, Duration minIdleTime) {
        this.minRunTime = minRunTime;
        this.minIdleTime = minIdleTime;
        return this;
    }

    public OperatingMode build() {
        if (startCondition == null || stopCondition == null || actions.isEmpty()) {
            throw new IllegalStateException("OperatingMode '" + modeName + "' is incomplete! Conditions and at least one Action are required.");
        }
        return new OperatingMode(modeName, startCondition, stopCondition, actions, minRunTime, minIdleTime);
    }


    /**
     * Factory class to create Conditions fluently and readably.
     */
    public class Conditions {
        public static ControllerDSL.Condition allOf(Condition... conditions) {
            return new Condition.LogicalCondition(Condition.LogicalOperator.AND, Arrays.asList(conditions));
        }

        public static Condition anyOf(Condition... conditions) {
            return new Condition.LogicalCondition(Condition.LogicalOperator.OR, Arrays.asList(conditions));
        }

        public static Condition greaterThan(PVSiteVariableType variable, ValueAdjustment adjustment, double value) {
            return new Condition.Predicate(variable, adjustment, Comparator.GREATER, value);
        }

        public static Condition lessThan(PVSiteVariableType variable, ValueAdjustment adjustment, double value) {
            return new Condition.Predicate(variable, adjustment, Comparator.LESS, value);
        }

        public static Condition greaterOrEqual(PVSiteVariableType variable, ValueAdjustment adjustment, double value) {
            return new Condition.Predicate(variable, adjustment, Comparator.GREATER_OR_EQUAL, value);
        }

        // Convenience for immediate/live values (e.g. for Battery SoC)
        public static ValueAdjustment live() {
            return new ValueAdjustment(InfluxUtil.AggregateOperation.LAST, 1, TimeUnit.MINUTES);
        }

        public static ValueAdjustment median(int minutes) {
            return new ValueAdjustment(InfluxUtil.AggregateOperation.MEDIAN, minutes, TimeUnit.MINUTES);
        }
    }
}
