package de.verdox.solarminer.rest;


import de.verdox.solarminer.RequiredField;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record RestConfigCreatorTemplate(String id, String name, List<RequiredField> requiredFields) {

    public static final RestConfigCreatorTemplate HOME_ASSISTANT_PV = new RestConfigCreatorTemplate(
            "ha_pvsite",
            "PV Site Inverter (4 Values)",
            List.of(
                    new RequiredField("pv_power", "kw"),
                    new RequiredField("grid_power", "kw"),
                    new RequiredField("battery_power", "kw"),
                    new RequiredField("battery_soc", "%")
            ));

    public RestPVConfig createTemplateConfig() {
        Map<String, RestPVConfig.Entry<?>> templateValues = new HashMap<>();
        for (RequiredField requiredField : requiredFields) {
            String defaultExtension = "/api/states/sensor." + requiredField.field();
            templateValues.put(requiredField.field(), new RestPVConfig.Entry<>(
                    defaultExtension,
                    RestHttpMethod.GET,
                    "state",
                    1.0f,
                    "",
                    RestParameterType.FLOAT
            ));
        }
        return new RestPVConfig(templateValues);
    }
}
