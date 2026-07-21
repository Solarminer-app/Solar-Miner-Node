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

    public static final ModbusConfigCreatorTemplate INVERTER = new ModbusConfigCreatorTemplate(
            "inverter",
            "Solar Inverter",
            List.of(
                    new RequiredField("current_dc_power", "w"),
                    new RequiredField("current_dc_voltage", "v"),
                    new RequiredField("current_ac_power", "w"),
                    new RequiredField("current_ac_voltage", "v"),
                    new RequiredField("grid_frequency", "hz"),
                    new RequiredField("total_energy_yield", "wh"),
                    new RequiredField("internal_temperature", "c"),
                    new RequiredField("status_code", "")
            )
    );

    public static final ModbusConfigCreatorTemplate BATTERY = new ModbusConfigCreatorTemplate(
            "battery",
            "Battery Storage",
            List.of(
                    new RequiredField("state_of_charge", "%"),
                    new RequiredField("current_power", "w"),
                    new RequiredField("current_max_charge_power", "w"),
                    new RequiredField("current_max_discharge_power", "w"),
                    new RequiredField("state_of_health", "%"),
                    new RequiredField("temperature", "c")
            )
    );

    public static final ModbusConfigCreatorTemplate SMART_METER = new ModbusConfigCreatorTemplate(
            "smart_meter",
            "Grid Smart Meter",
            List.of(
                    new RequiredField("total_active_power", "w"),
                    new RequiredField("total_imported", "wh"),
                    new RequiredField("total_exported", "wh"),
                    new RequiredField("power_l1", "w"),
                    new RequiredField("power_l2", "w"),
                    new RequiredField("power_l3", "w"),
                    new RequiredField("voltage_l1", "v"),
                    new RequiredField("voltage_l2", "v"),
                    new RequiredField("voltage_l3", "v")
            )
    );

    public static List<ModbusConfigCreatorTemplate> getAll() {
        return List.of(INVERTER, BATTERY, SMART_METER);
    }

    public static ModbusConfigCreatorTemplate byId(String id) {
        return getAll().stream().filter(modbusConfigCreatorTemplate -> modbusConfigCreatorTemplate.id().equals(id)).findAny().orElseThrow(() -> new NoSuchElementException("No modbus creator template found with id "+id));
    }
}
