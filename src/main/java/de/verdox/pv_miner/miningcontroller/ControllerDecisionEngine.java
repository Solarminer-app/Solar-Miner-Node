package de.verdox.pv_miner.miningcontroller;

import de.verdox.pv_miner.miningcontroller.dsl.ControllerDSL;
import de.verdox.pv_miner.miningcontroller.dsl.ControllerValueProvider;
import de.verdox.pv_miner.pvsite.PVSiteEntity;

import java.util.List;
import java.util.Map;

/**
 * Stateful decision core shared by the live cluster controller and dry-run simulations.
 */
public final class ControllerDecisionEngine {
    private ControllerDSL.OperatingMode activeMode;

    public Decision evaluate(
            MinerControllerConfig config,
            ControllerValueProvider valueProvider,
            PVSiteEntity site,
            double clusterCapacityWatts
    ) {
        ControllerDSL.OperatingMode previousMode = activeMode;
        ControllerDSL.OperatingMode nextMode = selectMode(config.getConfigEntries(), valueProvider, site);
        activeMode = nextMode;

        if (nextMode == null) {
            return new Decision(previousMode, null, previousMode != null, List.of(), null, 0);
        }

        ControllerDSL.ControllerAction powerAction = nextMode.actions().stream()
                .filter(action -> action.controllerActionType() == ControllerDSL.ControllerActionType.SET_POWER_TARGET)
                .findFirst()
                .orElse(null);
        double targetPowerWatts = powerAction == null
                ? 0
                : powerAction.valueExpression().evaluate(valueProvider, site, clusterCapacityWatts);

        return new Decision(
                previousMode,
                nextMode,
                previousMode != nextMode,
                nextMode.actions(),
                powerAction,
                Double.isFinite(targetPowerWatts) ? Math.max(0, targetPowerWatts) : 0
        );
    }

    public void reset() {
        activeMode = null;
    }

    private ControllerDSL.OperatingMode selectMode(
            Map<String, ControllerDSL.OperatingMode> modes,
            ControllerValueProvider valueProvider,
            PVSiteEntity site
    ) {
        for (ControllerDSL.OperatingMode mode : modes.values()) {
            if (activeMode != null && activeMode.modeName().equals(mode.modeName())) {
                if (!mode.stopCondition().evaluate(valueProvider, site)) {
                    return mode;
                }
            } else if (mode.startCondition().evaluate(valueProvider, site)) {
                return mode;
            }
        }
        return null;
    }

    public record Decision(
            ControllerDSL.OperatingMode previousMode,
            ControllerDSL.OperatingMode activeMode,
            boolean modeChanged,
            List<ControllerDSL.ControllerAction> actions,
            ControllerDSL.ControllerAction powerAction,
            double targetPowerWatts
    ) {
    }
}
