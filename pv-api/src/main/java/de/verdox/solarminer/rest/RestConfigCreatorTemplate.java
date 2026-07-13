package de.verdox.solarminer.rest;


import de.verdox.solarminer.RequiredField;
import de.verdox.solarminer.modbustcp.ModbusConfigCreatorTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record RestConfigCreatorTemplate(String id, String name, List<RequiredField> requiredFields) {

    public static List<RestConfigCreatorTemplate> getAll() {
        return List.of(INVERTER, BATTERY, SMART_METER);
    }


    public static final RestConfigCreatorTemplate HOME_ASSISTANT_PV = new RestConfigCreatorTemplate(
            "ha_pvsite",
            "PV Site Inverter (4 Values)",
            List.of(
                    new RequiredField("pv_power", "kw"),
                    new RequiredField("grid_power", "kw"),
                    new RequiredField("battery_power", "kw"),
                    new RequiredField("battery_soc", "%")
            ));

    public static final RestConfigCreatorTemplate INVERTER = new RestConfigCreatorTemplate(
            "rest_inverter", "REST Solar Inverter",
            List.of(
                    new RequiredField("current_dc_power", "w"),
                    new RequiredField("current_dc_voltage", "v"),
                    new RequiredField("current_ac_power", "w"),
                    new RequiredField("current_ac_voltage", "v"),
                    new RequiredField("grid_frequency", "hz"),
                    new RequiredField("total_energy_yield", "wh"),
                    new RequiredField("internal_temperature", "c"),
                    new RequiredField("status_code", "")
            ));

    public static final RestConfigCreatorTemplate BATTERY = new RestConfigCreatorTemplate(
            "rest_battery", "REST Battery Storage",
            List.of(
                    new RequiredField("state_of_charge", "%"),
                    new RequiredField("current_power", "w"),
                    new RequiredField("max_charge_power", "w"),
                    new RequiredField("max_discharge_power", "w"),
                    new RequiredField("state_of_health", "%"),
                    new RequiredField("temperature", "c")
            ));

    public static final RestConfigCreatorTemplate SMART_METER = new RestConfigCreatorTemplate(
            "rest_smart_meter", "REST Grid Smart Meter",
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
            ));
}
