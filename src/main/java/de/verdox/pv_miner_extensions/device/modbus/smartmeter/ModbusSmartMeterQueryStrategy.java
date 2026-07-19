package de.verdox.pv_miner_extensions.device.modbus.smartmeter;

import de.verdox.pv_miner.pvsite.smartmeter.SmartMeterDataDTO;
import de.verdox.pv_miner_extensions.device.modbus.ModbusQueryStrategy;
import de.verdox.solarminer.formula.VariableProvider;
import de.verdox.solarminer.modbustcp.ModbusConfig;
import de.verdox.solarminer.modbus.ModbusRegisterClient;

import java.util.Map;

public class ModbusSmartMeterQueryStrategy extends ModbusQueryStrategy<SmartMeterDataDTO, ModbusSmartMeter> {
    @Override
    protected SmartMeterDataDTO createResult(ModbusConfig.ConfigSection modbusConfig, ModbusRegisterClient client, Map<String, Double> calculatedValues, VariableProvider provider, int offset) throws Exception {

        double totalActivePowerW = hasEntry(modbusConfig, "total_active_power")
                ? evaluateEntry("total_active_power", modbusConfig, client, calculatedValues, provider, offset)
                : evaluateEntry("grid_power", modbusConfig, client, calculatedValues, provider, offset) * 1000.0;
        double totalImportedWh = evaluateEntry("total_imported", modbusConfig, client, calculatedValues, provider, offset);
        double totalExportedWh = evaluateEntry("total_exported", modbusConfig, client, calculatedValues, provider, offset);

        double powerL1W = evaluateEntry("power_l1", modbusConfig, client, calculatedValues, provider, offset);
        double powerL2W = evaluateEntry("power_l2", modbusConfig, client, calculatedValues, provider, offset);
        double powerL3W = evaluateEntry("power_l3", modbusConfig, client, calculatedValues, provider, offset);

        double voltageL1V = evaluateEntry("voltage_l1", modbusConfig, client, calculatedValues, provider, offset);
        double voltageL2V = evaluateEntry("voltage_l2", modbusConfig, client, calculatedValues, provider, offset);
        double voltageL3V = evaluateEntry("voltage_l3", modbusConfig, client, calculatedValues, provider, offset);

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
