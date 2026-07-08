package de.verdox.pv_miner_extensions.device.modbus.battery;

import de.verdox.pv_miner.pvsite.battery.BatteryDataDTO;
import de.verdox.pv_miner_extensions.device.modbus.ModbusQueryStrategy;
import de.verdox.solarminer.formula.VariableProvider;
import de.verdox.solarminer.modbustcp.ModbusConfig;
import de.verdox.solarminer.modbustcp.TCPModbusClient;

import java.util.Map;

public class ModbusBatteryQueryStrategy extends ModbusQueryStrategy<BatteryDataDTO, ModbusBattery> {
    @Override
    protected BatteryDataDTO createResult(ModbusConfig.ConfigSection section, TCPModbusClient client, Map<String, Double> calculatedValues, VariableProvider provider, int offset) throws Exception {

        double stateOfChargePct = evaluateEntry("state_of_charge", section, client, calculatedValues, provider, offset);
        double currentPowerW = evaluateEntry("current_power", section, client, calculatedValues, provider, offset);
        double currentMaxChargePowerW = evaluateEntry("current_max_charge_power", section, client, calculatedValues, provider, offset);
        double currentMaxDischargePowerW = evaluateEntry("current_max_discharge_power", section, client, calculatedValues, provider, offset);
        double stateOfHealthPct = evaluateEntry("state_of_health", section, client, calculatedValues, provider, offset);
        double temperatureC = evaluateEntry("temperature", section, client, calculatedValues, provider, offset);

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
