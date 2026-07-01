package de.verdox.pv_miner_extensions.inverter.smartfox;

import java.util.HashMap;
import java.util.Map;

@Deprecated
public enum SmartFoxValuesDataType {

    INVERTER_1_PV_POWER("wr1PowerValue", "kW"),
    INVERTER_2_PV_POWER("wr2PowerValue", "kW"),
    INVERTER_3_PV_POWER("wr3PowerValue", "kW"),
    INVERTER_4_PV_POWER("wr4PowerValue", "kW"),
    INVERTER_5_PV_POWER("wr5PowerValue", "kW"),

    TO_GRID("toGridValue", "kW"),
    BATTERY_SOC("batterySoc", "kWh"),
    BATTERY_1_POWER("battery1Power", "kW"),

    POWER_PRODUCTION("hidProduction", "kW"),
    
    BATTERY_1_TEMPERATURE("battery1Temperature", "°C"),
    BATTERY_2_TEMPERATURE("battery2Temperature", "°C"),

    ;
    private static final Map<String, SmartFoxValuesDataType> cloudDataType = new HashMap<>();

    private final String variableName;
    private final String unit;

    SmartFoxValuesDataType(String variableName, String unit) {
        this.variableName = variableName;
        this.unit = unit;
    }

    public String getVariableName() {
        return variableName;
    }

    public String getUnit() {
        return unit;
    }

    static {
        for (SmartFoxValuesDataType value : values()) {
            cloudDataType.put(value.variableName, value);
        }
    }

    public static SmartFoxValuesDataType byVariableName(String variableName) {
        return cloudDataType.get(variableName);
    }
}
