package de.verdox.pv_miner.pvsite;

import de.verdox.solarminer.modbustcp.ModbusConfig;
import de.verdox.solarminer.modbustcp.ModbusParameterType;
import de.verdox.solarminer.modbustcp.ModbusConfigCreatorTemplate;
import de.verdox.solarminer.modbustcp.ModbusReadOperationType;
import de.verdox.solarminer.modbustcp.TCPModbusClient;
import org.springframework.stereotype.Service;

import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

@Service
public class SunspecConfigService {

    public static final int THREE_PHASE_INVERTER = 103;
    public static final int ONE_PHASE_INVERTER = 101;
    public static final int THREE_PHASE_SMART_METER = 203;
    public static final int BATTERY = 802;

    public ModbusConfig createConfigFromSunSpec(Map<Integer, Integer> blockAddresses, TCPModbusClient client) {
        Map<String, ModbusConfig.Entry<?>> entries = new HashMap<>();

        int inverterBlockId = blockAddresses.containsKey(THREE_PHASE_INVERTER) ? THREE_PHASE_INVERTER : (blockAddresses.containsKey(ONE_PHASE_INVERTER) ? ONE_PHASE_INVERTER : -1);

        if (inverterBlockId != -1) {
            int blockStart = blockAddresses.get(inverterBlockId);
            int wattOffset = 14;
            int scaleFactorOffset = 15;

            float liveScaleFactor = readLiveScaleFactor(client, blockStart + scaleFactorOffset);
            float kwScaleFactor = liveScaleFactor * 0.001f;

            entries.put("pv_power", new ModbusConfig.Entry<>(
                    blockStart + wattOffset, 1, kwScaleFactor, "",
                    ModbusParameterType.INT16, ModbusReadOperationType.READ_HOLDING_REGISTER, ByteOrder.BIG_ENDIAN
            ));
        }

        if (blockAddresses.containsKey(THREE_PHASE_SMART_METER)) {
            int meterStart = blockAddresses.get(THREE_PHASE_SMART_METER);
            int meterWattOffset = 39;
            int meterScaleFactorOffset = 40;

            float liveScaleFactor = readLiveScaleFactor(client, meterStart + meterScaleFactorOffset);
            float kwScaleFactor = liveScaleFactor * 0.001f;

            entries.put("grid_power", new ModbusConfig.Entry<>(
                    meterStart + meterWattOffset, 1, kwScaleFactor, "",
                    ModbusParameterType.INT16, ModbusReadOperationType.READ_HOLDING_REGISTER, ByteOrder.BIG_ENDIAN
            ));
        }
        if (blockAddresses.containsKey(BATTERY)) {
            int battStart = blockAddresses.get(BATTERY);
            int socOffset = 26;
            int socScaleFactorOffset = 27;

            float liveScaleFactor = readLiveScaleFactor(client, battStart + socScaleFactorOffset);
            float kwScaleFactor = liveScaleFactor * 0.001f;

            entries.put("battery_soc", new ModbusConfig.Entry<>(
                    battStart + socOffset, 1, kwScaleFactor, "",
                    ModbusParameterType.UINT16, ModbusReadOperationType.READ_HOLDING_REGISTER, ByteOrder.BIG_ENDIAN
            ));
        }

        ensureRequiredFields(entries);

        Map<String, ModbusConfig.ConfigSection> sections = new HashMap<>();
        // FIX: Constructor jetzt mit templateId und name!
        sections.put(ModbusConfigCreatorTemplate.PV_SITE.id(), new ModbusConfig.ConfigSection(ModbusConfigCreatorTemplate.PV_SITE.id(), "SunSpec Auto-Generated", entries));

        return new ModbusConfig(null, sections);
    }

    private float readLiveScaleFactor(TCPModbusClient client, int address) {
        try {
            ModbusConfig.Entry<Short> scaleEntry = new ModbusConfig.Entry<>(
                    address, 1, 1.0f, "", ModbusParameterType.INT16, ModbusReadOperationType.READ_HOLDING_REGISTER, ByteOrder.BIG_ENDIAN
            );
            Number rawScale = (Number) client.read(scaleEntry);
            return (float) Math.pow(10, rawScale.intValue());
        } catch (Exception e) {
            return 1.0f;
        }
    }

    private void ensureRequiredFields(Map<String, ModbusConfig.Entry<?>> entries) {
        String[] required = {"pv_power", "grid_power", "battery_power", "battery_soc"};
        for (String req : required) {
            if (!entries.containsKey(req)) {
                entries.put(req, new ModbusConfig.Entry<>(
                        40000, 1, 0.0f, "x * 0", ModbusParameterType.INT16, ModbusReadOperationType.READ_HOLDING_REGISTER, ByteOrder.BIG_ENDIAN
                ));
            }
        }
    }
}