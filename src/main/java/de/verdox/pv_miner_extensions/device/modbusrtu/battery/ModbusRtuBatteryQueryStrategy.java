package de.verdox.pv_miner_extensions.device.modbusrtu.battery;

import de.verdox.pv_miner.pvsite.battery.BatteryDataDTO;
import de.verdox.pv_miner_extensions.device.modbus.ModbusQueryStrategy;
import de.verdox.solarminer.formula.VariableProvider;
import de.verdox.solarminer.modbus.ModbusRegisterClient;
import de.verdox.solarminer.modbustcp.ModbusConfig;
import java.util.Map;

public class ModbusRtuBatteryQueryStrategy extends ModbusQueryStrategy<BatteryDataDTO, ModbusRtuBattery> {
    @Override
    protected BatteryDataDTO createResult(ModbusConfig.ConfigSection c, ModbusRegisterClient client,
                                          Map<String, Double> values, VariableProvider provider, int offset) throws Exception {
        double soc = hasEntry(c, "state_of_charge") ? evaluateEntry("state_of_charge", c, client, values, provider, offset)
                : evaluateEntry("battery_soc", c, client, values, provider, offset);
        double power = hasEntry(c, "current_power") ? evaluateEntry("current_power", c, client, values, provider, offset)
                : evaluateEntry("battery_power", c, client, values, provider, offset) * 1000;
        return new BatteryDataDTO(soc, (int) power,
                (int) evaluateEntry("current_max_charge_power", c, client, values, provider, offset),
                (int) evaluateEntry("current_max_discharge_power", c, client, values, provider, offset),
                evaluateEntry("state_of_health", c, client, values, provider, offset),
                evaluateEntry("temperature", c, client, values, provider, offset));
    }
}
