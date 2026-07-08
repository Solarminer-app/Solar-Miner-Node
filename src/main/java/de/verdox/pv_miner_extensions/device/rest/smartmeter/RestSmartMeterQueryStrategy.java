package de.verdox.pv_miner_extensions.device.rest.smartmeter;

import de.verdox.pv_miner.pvsite.smartmeter.SmartMeterDataDTO;
import de.verdox.pv_miner_extensions.device.rest.RestQueryStrategy;
import de.verdox.solarminer.formula.VariableProvider;
import de.verdox.solarminer.rest.RestConfigCreatorTemplate;
import de.verdox.solarminer.rest.RestPVClient;
import de.verdox.solarminer.rest.RestPVConfig;

import java.util.Map;

public class RestSmartMeterQueryStrategy extends RestQueryStrategy<SmartMeterDataDTO, RestSmartMeter> {
    @Override
    protected SmartMeterDataDTO createResult(RestPVConfig.ConfigSection config, RestPVClient client, Map<String, Double> calculatedValues, VariableProvider provider) throws Exception {

        double totalActivePowerW = evaluateEntry("total_active_power", config, client, calculatedValues, provider);
        double totalImportedWh = evaluateEntry("total_imported", config, client, calculatedValues, provider);
        double totalExportedWh = evaluateEntry("total_exported", config, client, calculatedValues, provider);

        double powerL1W = evaluateEntry("power_l1", config, client, calculatedValues, provider);
        double powerL2W = evaluateEntry("power_l2", config, client, calculatedValues, provider);
        double powerL3W = evaluateEntry("power_l3", config, client, calculatedValues, provider);

        double voltageL1V = evaluateEntry("voltage_l1", config, client, calculatedValues, provider);
        double voltageL2V = evaluateEntry("voltage_l2", config, client, calculatedValues, provider);
        double voltageL3V = evaluateEntry("voltage_l3", config, client, calculatedValues, provider);

        return new SmartMeterDataDTO(
                (int) totalActivePowerW,
                (long) totalImportedWh,
                (long) totalExportedWh,
                (int) powerL1W,
                (int) powerL2W,
                (int) powerL3W,
                voltageL1V,
                voltageL2V,
                voltageL3V
        );
    }
}