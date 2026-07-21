package de.verdox.pv_miner_extensions.device.modbusrtu.smartmeter;

import de.verdox.pv_miner.pvsite.smartmeter.SmartMeterDataDTO;
import de.verdox.pv_miner_extensions.device.modbus.ModbusQueryStrategy;
import de.verdox.solarminer.formula.VariableProvider;
import de.verdox.solarminer.modbus.ModbusRegisterClient;
import de.verdox.solarminer.modbustcp.ModbusConfig;
import java.util.Map;

public class ModbusRtuSmartMeterQueryStrategy extends ModbusQueryStrategy<SmartMeterDataDTO, ModbusRtuSmartMeter> {
    @Override
    protected SmartMeterDataDTO createResult(ModbusConfig.ConfigSection c, ModbusRegisterClient client,
                                             Map<String, Double> values, VariableProvider provider, int offset) throws Exception {
        double total = hasEntry(c, "total_active_power") ? evaluateEntry("total_active_power", c, client, values, provider, offset)
                : evaluateEntry("grid_power", c, client, values, provider, offset) * 1000;
        return new SmartMeterDataDTO((int) total,
                (long) evaluateEntry("total_imported", c, client, values, provider, offset),
                (long) evaluateEntry("total_exported", c, client, values, provider, offset),
                (int) evaluateEntry("power_l1", c, client, values, provider, offset),
                (int) evaluateEntry("power_l2", c, client, values, provider, offset),
                (int) evaluateEntry("power_l3", c, client, values, provider, offset),
                evaluateEntry("voltage_l1", c, client, values, provider, offset),
                evaluateEntry("voltage_l2", c, client, values, provider, offset),
                evaluateEntry("voltage_l3", c, client, values, provider, offset));
    }
}
