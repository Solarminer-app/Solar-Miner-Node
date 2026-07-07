package de.verdox.pv_miner.miningcontroller.dsl;

import de.verdox.pv_miner.miningcontroller.MinerControllerConfig;
import de.verdox.pv_miner.miningcontroller.dsl.ControllerDSL.*;
import de.verdox.pv_miner.miningcontroller.dsl.DSLBuilder.*;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class DefaultCluster {

    static {
        ValueAdjustment median30Min = Conditions.median(30);
        ValueAdjustment median5Min = Conditions.median(5);
        ValueAdjustment liveData = Conditions.live();

        ValueExpression safeSurplusTarget = new ValueExpression.DynamicVariable(
                PVSiteVariableType.POTENTIAL_PV_SURPLUS,
                median5Min,
                950.0,
                0.0
        );

        OperatingMode surplusTrackingMode = DSLBuilder.create("Mining with PV-Surplus")
                .startWhen(Conditions.allOf(
                        Conditions.greaterThan(PVSiteVariableType.POTENTIAL_PV_SURPLUS, median30Min, 0.15),
                        Conditions.greaterOrEqual(PVSiteVariableType.BATTERY_SOC, liveData, 95.0)
                ))
                .stopWhen(Conditions.lessThan(PVSiteVariableType.POTENTIAL_PV_SURPLUS, median30Min, 0.02))
                .execute(new ControllerAction(
                        ControllerActionType.RESUME,
                        ActionTargetType.ALL_MINERS,
                        MinerDistributionStrategy.EFFICIENCY_FIRST,
                        new ValueExpression.Constant(1.0),
                        0
                ))
                .execute(new ControllerAction(
                        ControllerActionType.SET_POWER_TARGET,
                        ActionTargetType.CLUSTER_DYNAMIC,
                        MinerDistributionStrategy.EFFICIENCY_FIRST,
                        safeSurplusTarget,
                        250
                ))
                .withHardwareLocks(
                        Duration.ofMinutes(30),
                        Duration.ofMinutes(15),
                        Duration.ofMinutes(5)
                )
                .build();

        OperatingMode emergencyStopMode = DSLBuilder.create("Mining Cluster Full stop")
                .startWhen(Conditions.lessThan(PVSiteVariableType.BATTERY_SOC, liveData, 90.0))
                .stopWhen(Conditions.greaterOrEqual(PVSiteVariableType.BATTERY_SOC, liveData, 92.0))
                .execute(new ControllerAction(
                        ControllerActionType.PAUSE,
                        ActionTargetType.ALL_MINERS,
                        MinerDistributionStrategy.EQUAL_DISTRIBUTION,
                        new ValueExpression.Constant(0.0),
                        0
                ))
                .withHardwareLocks(Duration.ZERO, Duration.ZERO, Duration.ZERO)
                .build();

        Map<String, OperatingMode> defaultModes = new LinkedHashMap<>();
        defaultModes.put(emergencyStopMode.modeName(), emergencyStopMode);
        defaultModes.put(surplusTrackingMode.modeName(), surplusTrackingMode);

        DEFAULT = new MinerControllerConfig(defaultModes);
    }

    public static MinerControllerConfig DEFAULT;
}
