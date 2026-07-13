package de.verdox.pv_miner_extensions.device.rest.battery;

import de.verdox.pv_miner.pvsite.battery.BatteryDataDTO;
import de.verdox.pv_miner_extensions.device.rest.RestQueryStrategy;
import de.verdox.solarminer.formula.VariableProvider;
import de.verdox.solarminer.rest.RestPVClient;
import de.verdox.solarminer.rest.RestPVConfig;

import java.util.Map;

public class RestBatteryQueryStrategy extends RestQueryStrategy<BatteryDataDTO, RestBattery> {

    @Override
    protected BatteryDataDTO createResult(RestPVConfig.ConfigSection config, RestPVClient client, Map<String, Double> calculatedValues, VariableProvider provider) throws Exception {

        double stateOfChargePct = evaluateEntry("state_of_charge", config, client, calculatedValues, provider);
        double currentPowerW = evaluateEntry("current_power", config, client, calculatedValues, provider);
        double currentMaxChargePowerW = evaluateEntry("current_max_charge_power", config, client, calculatedValues, provider);
        double currentMaxDischargePowerW = evaluateEntry("current_max_discharge_power", config, client, calculatedValues, provider);
        double stateOfHealthPct = evaluateEntry("state_of_health", config, client, calculatedValues, provider);
        double temperatureC = evaluateEntry("temperature", config, client, calculatedValues, provider);

        return new BatteryDataDTO(
                stateOfChargePct,
                (int) currentPowerW,
                (int) currentMaxChargePowerW,
                (int) currentMaxDischargePowerW,
                stateOfHealthPct,
                temperatureC
        );
    }
}