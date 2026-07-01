package de.verdox.pv_miner_extensions.inverter.rest;

import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.formula.FormulaEngine;
import de.verdox.pv_miner.formula.VariableProvider;
import de.verdox.pv_miner.pvsite.PVSiteDataDTO;
import de.verdox.pv_miner.pvsite.PVSiteQueryStrategy;
import de.verdox.pv_miner_extensions.inverter.rest.config.RestConfigCreatorTemplate;
import de.verdox.pv_miner_extensions.inverter.rest.config.RestConfigStorage;
import de.verdox.pv_miner_extensions.inverter.rest.config.RestPVConfig;

import java.util.HashMap;
import java.util.Map;

public class RestPVSiteQueryStrategy implements PVSiteQueryStrategy<RestPVSite> {

    @Override
    public PVSiteDataDTO query(EntityQueryService entityQueryService, RestPVSite entity) throws Throwable {
        RestPVConfig restConfig = SpringContextHelper.getBean(RestConfigStorage.class)
                .loadConfig(RestConfigCreatorTemplate.HOME_ASSISTANT_PV, entity.getRestPVConfigName());

        try (var client = new RestPVClient(entity.getHostName() + ":" + entity.getPort(), entity.getApiToken())) {
            Map<String, Double> calculatedValues = new HashMap<>();

            var variableProvider = (VariableProvider) (variableName) -> {
                if (calculatedValues.containsKey(variableName)) {
                    return calculatedValues.get(variableName);
                }
                RestPVConfig.Entry<?> targetEntry = restConfig.getEntryForId(variableName);
                return client.read(targetEntry);
            };

            double pvPower = evaluateEntry("pv_power", restConfig, client, calculatedValues, variableProvider);
            double gridPower = evaluateEntry("grid_power", restConfig, client, calculatedValues, variableProvider);
            double batteryPower = evaluateEntry("battery_power", restConfig, client, calculatedValues, variableProvider);
            float batterySoC = (float) evaluateEntry("battery_soc", restConfig, client, calculatedValues, variableProvider);

            return createData(builder -> builder
                    .pvPower(pvPower)
                    .gridPower(gridPower)
                    .batteryPower(batteryPower)
                    .batterySoC(batterySoC)
                    .totalMinerPowerKw(approximateMiningPowerDrawKw(entityQueryService, entity))
            );
        }
    }

    @Override
    public void ping(RestPVSite entity) throws Throwable {
        try (var client = new RestPVClient(entity.getHostName() + ":" + entity.getPort(), entity.getApiToken())) {

        }
    }

    private double evaluateEntry(String id, RestPVConfig config, RestPVClient client, Map<String, Double> calculatedValues, VariableProvider provider) throws Exception {
        if (calculatedValues.containsKey(id)) {
            return calculatedValues.get(id);
        }
        RestPVConfig.Entry<?> entry = config.getEntryForId(id);
        double raw = client.read(entry);
        double finalValue = FormulaEngine.evaluate(raw, entry.formula(), provider);

        calculatedValues.put(id, finalValue);
        return finalValue;
    }
}