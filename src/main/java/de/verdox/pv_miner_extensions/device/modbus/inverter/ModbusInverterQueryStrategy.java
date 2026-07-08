package de.verdox.pv_miner_extensions.device.modbus.inverter;

import de.verdox.pv_miner.pvsite.inverter.InverterDataDTO;
import de.verdox.pv_miner_extensions.device.modbus.ModbusQueryStrategy;
import de.verdox.solarminer.formula.VariableProvider;
import de.verdox.solarminer.modbustcp.ModbusConfig;
import de.verdox.solarminer.modbustcp.ModbusConfigCreatorTemplate;
import de.verdox.solarminer.modbustcp.TCPModbusClient;

import java.util.Map;

public class ModbusInverterQueryStrategy extends ModbusQueryStrategy<InverterDataDTO, ModbusInverter> {

    @Override
    protected InverterDataDTO createResult(ModbusConfig.ConfigSection modbusConfig, TCPModbusClient client, Map<String, Double> calculatedValues, VariableProvider provider) throws Exception {

        double currentDcPowerW = evaluateEntry("current_dc_power", modbusConfig, client, calculatedValues, provider);
        double currentDcVoltageV = evaluateEntry("current_dc_voltage", modbusConfig, client, calculatedValues, provider);
        double currentAcPowerW = evaluateEntry("current_ac_power", modbusConfig, client, calculatedValues, provider);
        double currentAcVoltageV = evaluateEntry("current_ac_voltage", modbusConfig, client, calculatedValues, provider);
        double gridFrequencyHz = evaluateEntry("grid_frequency", modbusConfig, client, calculatedValues, provider);
        double totalEnergyYieldWh = evaluateEntry("total_energy_yield", modbusConfig, client, calculatedValues, provider);
        double internalTemperatureC = evaluateEntry("internal_temperature", modbusConfig, client, calculatedValues, provider);
        double statusCode = evaluateEntry("status_code", modbusConfig, client, calculatedValues, provider);

        return new InverterDataDTO(
                (int) currentDcPowerW,
                currentDcVoltageV,
                (int) currentAcPowerW,
                currentAcVoltageV,
                gridFrequencyHz,
                (long) totalEnergyYieldWh,
                internalTemperatureC,
                (int) statusCode
        );
    }
}
