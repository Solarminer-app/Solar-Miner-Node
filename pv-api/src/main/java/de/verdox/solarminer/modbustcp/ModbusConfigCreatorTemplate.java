package de.verdox.solarminer.modbustcp;


import de.verdox.solarminer.RequiredField;

import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

public record ModbusConfigCreatorTemplate(String id, String name, List<RequiredField> requiredFields) {
    public static final ModbusConfigCreatorTemplate PV_SITE = new ModbusConfigCreatorTemplate(
            "pvsite",
            "PV Site Inverter (4 Values)",
            List.of(
            new RequiredField("pv_power", "kw"),
            new RequiredField("grid_power", "kw"),
            new RequiredField("battery_power", "kw"),
            new RequiredField("battery_soc", "%")
    ));

    public static List<ModbusConfigCreatorTemplate> getAll() {
        return List.of(PV_SITE);
    }

    public static ModbusConfigCreatorTemplate byId(String id) {
        return getAll().stream().filter(modbusConfigCreatorTemplate -> modbusConfigCreatorTemplate.id().equals(id)).findAny().orElseThrow(() -> new NoSuchElementException("No modbus creator template found with id "+id));
    }

    public ModbusConfig createTemplateConfig() {
        Map<String, ModbusConfig.Entry<?>> templateValues = new HashMap<>();
        for (RequiredField requiredField : requiredFields) {
            templateValues.put(requiredField.field(), new ModbusConfig.Entry<>(40000, 1, 1, "", ModbusParameterType.INT32, ModbusReadOperationType.READ_HOLDING_REGISTER, ByteOrder.BIG_ENDIAN));
        }
        return new ModbusConfig(new ModbusConfig.Fingerprint(40000, 1, ModbusParameterType.INT32, ModbusReadOperationType.READ_HOLDING_REGISTER, ByteOrder.BIG_ENDIAN, ""),templateValues);
    }

}
