package de.verdox.pv_miner_extensions.device.modbus;

import de.verdox.pv_miner.pvsite.battery.BatteryDataDTO;
import de.verdox.pv_miner_extensions.device.modbus.battery.ModbusBatteryQueryStrategy;
import de.verdox.solarminer.modbus.ModbusRegisterClient;
import de.verdox.solarminer.modbustcp.ModbusConfig;
import de.verdox.solarminer.modbustcp.ModbusParameterType;
import de.verdox.solarminer.modbustcp.ModbusReadOperationType;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModbusQueryStrategyPreviewTest {
    @Test
    void executesTheProductionDslAgainstAnExistingClient() throws Exception {
        Map<String, ModbusConfig.Entry<?>> entries = new LinkedHashMap<>();
        entries.put("state_of_charge", entry(1));
        entries.put("current_power", entry(2));
        entries.put("current_max_charge_power", entry(3));
        entries.put("current_max_discharge_power", entry(4));
        entries.put("state_of_health", entry(5));
        entries.put("temperature", entry(6));
        ModbusConfig config = new ModbusConfig(null, Map.of(
                "battery_1", new ModbusConfig.ConfigSection("battery", "Battery 1", entries)
        ), -1);
        ModbusRegisterClient client = new ModbusRegisterClient() {
            @Override
            public Object read(int addressOffset, ModbusConfig.Entry<?> configEntry) {
                assertEquals(-1, addressOffset);
                return configEntry.startAddress() * 10;
            }

            @Override
            public boolean verifyFingerprint(int addressOffset, ModbusConfig.Fingerprint fingerprint) {
                return true;
            }

            @Override
            public void close() {
            }
        };

        BatteryDataDTO result = new ModbusBatteryQueryStrategy().querySection(config, "battery_1", client);

        assertEquals(10, result.stateOfChargePct());
        assertEquals(20, result.currentPowerW());
        assertEquals(30, result.currentMaxChargePowerW());
        assertEquals(40, result.currentMaxDischargePowerW());
        assertEquals(50, result.stateOfHealthPct());
        assertEquals(60, result.temperatureC());
    }

    private ModbusConfig.Entry<Integer> entry(int address) {
        return new ModbusConfig.Entry<>(address, 1, 1, "x", ModbusParameterType.INT32,
                ModbusReadOperationType.READ_HOLDING_REGISTER, ByteOrder.BIG_ENDIAN);
    }
}
