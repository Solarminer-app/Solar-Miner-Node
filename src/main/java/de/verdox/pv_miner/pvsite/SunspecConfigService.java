package de.verdox.pv_miner.pvsite;

import de.verdox.solarminer.modbustcp.ModbusConfig;
import de.verdox.solarminer.modbustcp.ModbusParameterType;
import de.verdox.solarminer.modbustcp.ModbusConfigCreatorTemplate;
import de.verdox.solarminer.modbustcp.ModbusReadOperationType;
import de.verdox.solarminer.modbustcp.TCPModbusClient;
import org.springframework.stereotype.Service;

import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SunspecConfigService {

    public static final int THREE_PHASE_INVERTER = 103;
    public static final int ONE_PHASE_INVERTER = 101;
    public static final int THREE_PHASE_SMART_METER = 203;
    public static final int BATTERY = 802;

    public ModbusConfig createConfigFromSunSpec(Map<Integer, Integer> blockAddresses, TCPModbusClient client) {
        Map<String, ModbusConfig.ConfigSection> sections = new LinkedHashMap<>();

        int inverterBlockId = blockAddresses.containsKey(THREE_PHASE_INVERTER) ? THREE_PHASE_INVERTER : (blockAddresses.containsKey(ONE_PHASE_INVERTER) ? ONE_PHASE_INVERTER : -1);

        if (inverterBlockId != -1) {
            int blockStart = blockAddresses.get(inverterBlockId);
            int wattOffset = 14;
            int scaleFactorOffset = 15;

            float liveScaleFactor = readLiveScaleFactor(client, blockStart + scaleFactorOffset);
            Map<String, ModbusConfig.Entry<?>> entries = new LinkedHashMap<>();
            entries.put("current_ac_power", new ModbusConfig.Entry<>(
                    blockStart + wattOffset, 1, liveScaleFactor, "x",
                    ModbusParameterType.INT16, ModbusReadOperationType.READ_HOLDING_REGISTER, ByteOrder.BIG_ENDIAN
            ));
            entries.put("current_dc_power", entry(blockStart + 31, 1, readLiveScaleFactor(client, blockStart + 32), ModbusParameterType.INT16));
            entries.put("current_dc_voltage", entry(blockStart + 29, 1, readLiveScaleFactor(client, blockStart + 30), ModbusParameterType.UINT16));
            entries.put("current_ac_voltage", entry(blockStart + 10, 1, readLiveScaleFactor(client, blockStart + 13), ModbusParameterType.UINT16));
            entries.put("grid_frequency", entry(blockStart + 16, 1, readLiveScaleFactor(client, blockStart + 17), ModbusParameterType.UINT16));
            entries.put("total_energy_yield", entry(blockStart + 24, 2, readLiveScaleFactor(client, blockStart + 26), ModbusParameterType.UINT32));
            entries.put("internal_temperature", entry(blockStart + 33, 1, readLiveScaleFactor(client, blockStart + 37), ModbusParameterType.INT16));
            entries.put("status_code", entry(blockStart + 38, 1, 1.0f, ModbusParameterType.UINT16));
            sections.put("sunspec_inverter_" + inverterBlockId, new ModbusConfig.ConfigSection(
                    ModbusConfigCreatorTemplate.INVERTER.id(), "SunSpec Inverter (Model " + inverterBlockId + ")", entries
            ));
        }

        if (blockAddresses.containsKey(THREE_PHASE_SMART_METER)) {
            int meterStart = blockAddresses.get(THREE_PHASE_SMART_METER);
            int meterWattOffset = 18;
            int meterScaleFactorOffset = 22;

            float liveScaleFactor = readLiveScaleFactor(client, meterStart + meterScaleFactorOffset);
            Map<String, ModbusConfig.Entry<?>> entries = new LinkedHashMap<>();
            entries.put("total_active_power", new ModbusConfig.Entry<>(
                    meterStart + meterWattOffset, 1, liveScaleFactor, "x",
                    ModbusParameterType.INT16, ModbusReadOperationType.READ_HOLDING_REGISTER, ByteOrder.BIG_ENDIAN
            ));
            float voltageScale = readLiveScaleFactor(client, meterStart + 15);
            entries.put("power_l1", entry(meterStart + 19, 1, liveScaleFactor, ModbusParameterType.INT16));
            entries.put("power_l2", entry(meterStart + 20, 1, liveScaleFactor, ModbusParameterType.INT16));
            entries.put("power_l3", entry(meterStart + 21, 1, liveScaleFactor, ModbusParameterType.INT16));
            entries.put("voltage_l1", entry(meterStart + 8, 1, voltageScale, ModbusParameterType.INT16));
            entries.put("voltage_l2", entry(meterStart + 9, 1, voltageScale, ModbusParameterType.INT16));
            entries.put("voltage_l3", entry(meterStart + 10, 1, voltageScale, ModbusParameterType.INT16));
            float energyScale = readLiveScaleFactor(client, meterStart + 54);
            entries.put("total_exported", entry(meterStart + 38, 2, energyScale, ModbusParameterType.UINT32));
            entries.put("total_imported", entry(meterStart + 46, 2, energyScale, ModbusParameterType.UINT32));
            sections.put("sunspec_smart_meter_" + THREE_PHASE_SMART_METER, new ModbusConfig.ConfigSection(
                    ModbusConfigCreatorTemplate.SMART_METER.id(), "SunSpec Smart Meter (Model " + THREE_PHASE_SMART_METER + ")", entries
            ));
        }
        if (blockAddresses.containsKey(BATTERY)) {
            int battStart = blockAddresses.get(BATTERY);
            int socOffset = 11;
            int socScaleFactorOffset = 56;

            float liveScaleFactor = readLiveScaleFactor(client, battStart + socScaleFactorOffset);
            Map<String, ModbusConfig.Entry<?>> entries = new LinkedHashMap<>();
            entries.put("state_of_charge", new ModbusConfig.Entry<>(
                    battStart + socOffset, 1, liveScaleFactor, "x",
                    ModbusParameterType.UINT16, ModbusReadOperationType.READ_HOLDING_REGISTER, ByteOrder.BIG_ENDIAN
            ));
            float maxPowerScale = readLiveScaleFactor(client, battStart + 54);
            entries.put("current_max_charge_power", entry(battStart + 4, 1, maxPowerScale, ModbusParameterType.UINT16));
            entries.put("current_max_discharge_power", entry(battStart + 5, 1, maxPowerScale, ModbusParameterType.UINT16));
            entries.put("current_power", entry(battStart + 47, 1, readLiveScaleFactor(client, battStart + 63), ModbusParameterType.INT16));
            entries.put("state_of_health", entry(battStart + 13, 1, readLiveScaleFactor(client, battStart + 58), ModbusParameterType.UINT16));
            sections.put("sunspec_battery_" + BATTERY, new ModbusConfig.ConfigSection(
                    ModbusConfigCreatorTemplate.BATTERY.id(), "SunSpec Battery (Model " + BATTERY + ")", entries
            ));
        }

        return new ModbusConfig(null, sections, 0);
    }

    private float readLiveScaleFactor(TCPModbusClient client, int address) {
        try {
            ModbusConfig.Entry<Short> scaleEntry = new ModbusConfig.Entry<>(
                    address, 1, 1.0f, "", ModbusParameterType.INT16, ModbusReadOperationType.READ_HOLDING_REGISTER, ByteOrder.BIG_ENDIAN
            );
            Number rawScale = (Number) client.read(0, scaleEntry);
            return (float) Math.pow(10, rawScale.intValue());
        } catch (Exception e) {
            return 1.0f;
        }
    }

    private ModbusConfig.Entry<?> entry(int address, int size, float scaleFactor, ModbusParameterType<?> type) {
        return new ModbusConfig.Entry<>(address, size, scaleFactor, "x", type,
                ModbusReadOperationType.READ_HOLDING_REGISTER, ByteOrder.BIG_ENDIAN);
    }

}
