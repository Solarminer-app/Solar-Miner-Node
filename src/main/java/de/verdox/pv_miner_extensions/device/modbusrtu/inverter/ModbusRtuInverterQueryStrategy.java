package de.verdox.pv_miner_extensions.device.modbusrtu.inverter;

import de.verdox.pv_miner.pvsite.inverter.InverterDataDTO;
import de.verdox.pv_miner_extensions.device.modbus.ModbusQueryStrategy;
import de.verdox.solarminer.formula.VariableProvider;
import de.verdox.solarminer.modbus.ModbusRegisterClient;
import de.verdox.solarminer.modbustcp.ModbusConfig;
import java.util.Map;

public class ModbusRtuInverterQueryStrategy extends ModbusQueryStrategy<InverterDataDTO, ModbusRtuInverter> {
    @Override
    protected InverterDataDTO createResult(ModbusConfig.ConfigSection c, ModbusRegisterClient client,
                                           Map<String, Double> values, VariableProvider provider, int offset) throws Exception {
        double legacy = hasEntry(c, "pv_power") ? evaluateEntry("pv_power", c, client, values, provider, offset) * 1000 : 0;
        double dcPower = hasEntry(c, "current_dc_power") ? evaluateEntry("current_dc_power", c, client, values, provider, offset) : legacy;
        double acPower = hasEntry(c, "current_ac_power") ? evaluateEntry("current_ac_power", c, client, values, provider, offset) : legacy;
        return new InverterDataDTO((int) dcPower,
                evaluateEntry("current_dc_voltage", c, client, values, provider, offset), (int) acPower,
                evaluateEntry("current_ac_voltage", c, client, values, provider, offset),
                evaluateEntry("grid_frequency", c, client, values, provider, offset),
                (long) evaluateEntry("total_energy_yield", c, client, values, provider, offset),
                evaluateEntry("internal_temperature", c, client, values, provider, offset),
                (int) evaluateEntry("status_code", c, client, values, provider, offset));
    }
}
