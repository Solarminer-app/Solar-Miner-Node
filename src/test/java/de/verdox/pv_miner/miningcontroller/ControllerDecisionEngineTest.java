package de.verdox.pv_miner.miningcontroller;

import de.verdox.pv_miner.influx.InfluxUtil;
import de.verdox.pv_miner.miningcontroller.dsl.ControllerDSL;
import de.verdox.pv_miner.miningcontroller.dsl.ControllerValueProvider;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteInfluxStrategy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ControllerDecisionEngineTest {
    private final ControllerDSL.ValueAdjustment live = new ControllerDSL.ValueAdjustment(
            InfluxUtil.AggregateOperation.LAST, 1, TimeUnit.MINUTES
    );

    @Test
    void liveControllerAndSimulatorCanShareTheSameDslDecisionPath() {
        ControllerDSL.OperatingMode emergency = mode(
                "Emergency",
                predicate(ControllerDSL.PVSiteVariableType.BATTERY_SOC, ControllerDSL.Comparator.LESS, 20),
                predicate(ControllerDSL.PVSiteVariableType.BATTERY_SOC, ControllerDSL.Comparator.GREATER_OR_EQUAL, 30),
                new ControllerDSL.ControllerAction(
                        ControllerDSL.ControllerActionType.PAUSE,
                        ControllerDSL.ActionTargetType.ALL_MINERS,
                        ControllerDSL.MinerDistributionStrategy.EQUAL_DISTRIBUTION,
                        new ControllerDSL.ValueExpression.Constant(0),
                        0
                )
        );
        ControllerDSL.OperatingMode surplus = mode(
                "Surplus",
                predicate(ControllerDSL.PVSiteVariableType.POTENTIAL_PV_SURPLUS, ControllerDSL.Comparator.GREATER_OR_EQUAL, 1),
                predicate(ControllerDSL.PVSiteVariableType.POTENTIAL_PV_SURPLUS, ControllerDSL.Comparator.LESS, 0.5),
                new ControllerDSL.ControllerAction(
                        ControllerDSL.ControllerActionType.SET_POWER_TARGET,
                        ControllerDSL.ActionTargetType.CLUSTER_DYNAMIC,
                        ControllerDSL.MinerDistributionStrategy.EFFICIENCY_FIRST,
                        new ControllerDSL.ValueExpression.DynamicVariable(
                                ControllerDSL.PVSiteVariableType.POTENTIAL_PV_SURPLUS, live, 1000, 0
                        ),
                        250
                )
        );
        LinkedHashMap<String, ControllerDSL.OperatingMode> modes = new LinkedHashMap<>();
        modes.put(emergency.modeName(), emergency);
        modes.put(surplus.modeName(), surplus);

        MinerControllerConfig config = new MinerControllerConfig(modes);
        MutableProvider provider = new MutableProvider();
        provider.values.put(PVSiteInfluxStrategy.BATTERY_STATE_OF_CHARGE, 80.0);
        provider.values.put(PVSiteInfluxStrategy.PV_POWER_IN_KW, 5.0);
        provider.values.put(PVSiteInfluxStrategy.LOADS_POWER_IN_KW, 1.0);
        provider.values.put(PVSiteInfluxStrategy.MINER_POWER_IN_KW, 0.0);
        ControllerDecisionEngine engine = new ControllerDecisionEngine();

        var surplusDecision = engine.evaluate(config, provider, mock(PVSiteEntity.class), 10_000);
        assertEquals("Surplus", surplusDecision.activeMode().modeName());
        assertEquals(4_000, surplusDecision.targetPowerWatts(), 0.001);

        provider.values.put(PVSiteInfluxStrategy.BATTERY_STATE_OF_CHARGE, 10.0);
        var emergencyDecision = engine.evaluate(config, provider, mock(PVSiteEntity.class), 10_000);
        assertEquals("Emergency", emergencyDecision.activeMode().modeName());
        assertEquals(0, emergencyDecision.targetPowerWatts(), 0.001);
        assertTrue(emergencyDecision.modeChanged());

        provider.values.put(PVSiteInfluxStrategy.BATTERY_STATE_OF_CHARGE, 40.0);
        var recoveredDecision = engine.evaluate(config, provider, mock(PVSiteEntity.class), 10_000);
        assertEquals("Surplus", recoveredDecision.activeMode().modeName());
    }

    private ControllerDSL.Condition predicate(
            ControllerDSL.PVSiteVariableType variable,
            ControllerDSL.Comparator comparator,
            double threshold
    ) {
        return new ControllerDSL.Condition.Predicate(variable, live, comparator, threshold);
    }

    private ControllerDSL.OperatingMode mode(
            String name,
            ControllerDSL.Condition start,
            ControllerDSL.Condition stop,
            ControllerDSL.ControllerAction action
    ) {
        return new ControllerDSL.OperatingMode(
                name, start, stop, java.util.List.of(action), Duration.ZERO, Duration.ZERO, Duration.ZERO
        );
    }

    private static final class MutableProvider implements ControllerValueProvider {
        private final Map<String, Double> values = new java.util.HashMap<>();

        @Override
        public double getValue(String valueId, PVSiteEntity site, ControllerDSL.ValueAdjustment adjustment) {
            return values.getOrDefault(valueId, 0.0);
        }
    }
}
