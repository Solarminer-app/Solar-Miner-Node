package de.verdox.pv_miner_extensions.device.rest.inverter;

import de.verdox.pv_miner.pvsite.inverter.InverterDataDTO;
import de.verdox.pv_miner_extensions.device.rest.RestQueryStrategy;
import de.verdox.solarminer.formula.VariableProvider;
import de.verdox.solarminer.rest.RestConfigCreatorTemplate;
import de.verdox.solarminer.rest.RestPVClient;
import de.verdox.solarminer.rest.RestPVConfig;

import java.util.Map;

public class RestInverterQueryStrategy extends RestQueryStrategy<InverterDataDTO, RestInverter> {
    @Override
    protected InverterDataDTO createResult(RestPVConfig.ConfigSection config, RestPVClient client, Map<String, Double> calculatedValues, VariableProvider provider) throws Exception {
        double currentDcPowerW = evaluateEntry("current_dc_power", config, client, calculatedValues, provider);
        double currentDcVoltageV = evaluateEntry("current_dc_voltage", config, client, calculatedValues, provider);
        double currentAcPowerW = evaluateEntry("current_ac_power", config, client, calculatedValues, provider);
        double currentAcVoltageV = evaluateEntry("current_ac_voltage", config, client, calculatedValues, provider);
        double gridFrequencyHz = evaluateEntry("grid_frequency", config, client, calculatedValues, provider);
        double totalEnergyYieldWh = evaluateEntry("total_energy_yield", config, client, calculatedValues, provider);
        double internalTemperatureC = evaluateEntry("internal_temperature", config, client, calculatedValues, provider);
        double statusCode = evaluateEntry("status_code", config, client, calculatedValues, provider);

        return new InverterDataDTO(
                (int) currentDcPowerW, currentDcVoltageV, (int) currentAcPowerW, currentAcVoltageV,
                gridFrequencyHz, (long) totalEnergyYieldWh, internalTemperatureC, (int) statusCode
        );
    }
}