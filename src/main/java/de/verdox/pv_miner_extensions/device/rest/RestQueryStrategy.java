package de.verdox.pv_miner_extensions.device.rest;

import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.influx.QueryResult;
import de.verdox.solarminer.formula.FormulaEngine;
import de.verdox.solarminer.formula.VariableProvider;
import de.verdox.solarminer.rest.RestConfigCreatorTemplate;
import de.verdox.solarminer.rest.RestPVClient;
import de.verdox.solarminer.rest.RestPVConfig;
import java.util.HashMap;
import java.util.Map;

public abstract class RestQueryStrategy<RESULT extends QueryResult, REST_ENTITY extends RestEntity<RESULT>> implements EntityQueryService.Strategy<REST_ENTITY, RESULT> {
    @Override
    public RESULT query(EntityQueryService entityQueryService, REST_ENTITY entity) throws Throwable {
        RestPVConfig restConfig = SpringContextHelper.getBean(RestConfigStorage.class).loadConfig(entity.getRestConfigName());
        RestPVConfig.ConfigSection section = restConfig.getSection(entity.getSectionKey());

        if (section == null) {
            throw new IllegalStateException("");
        }

        try (var client = new RestPVClient(entity.getHostName() + ":" + entity.getPort(), entity.getApiToken())) {
            Map<String, Double> calculatedValues = new HashMap<>();
            VariableProvider variableProvider = new VariableProvider() {
                @Override
                public double getValueFor(String variableName) {
                    try { return evaluateEntry(variableName, section, client, calculatedValues, this); } catch (Exception e) { return 0.0; }
                }
            };
            return createResult(section, client, calculatedValues, variableProvider);
        }
    }

    protected abstract RESULT createResult(RestPVConfig.ConfigSection section, RestPVClient client, Map<String, Double> calculatedValues, VariableProvider provider) throws Exception;

    protected static double evaluateEntry(String id, RestPVConfig.ConfigSection section, RestPVClient client, Map<String, Double> calculatedValues, VariableProvider provider) throws Exception {
        if (calculatedValues.containsKey(id)) return calculatedValues.get(id);
        RestPVConfig.Entry<?> entry = section.getEntryForId(id);
        double rawValue = client.read(entry);
        double evaluatedValue = FormulaEngine.evaluate(rawValue, entry.formula(), provider);
        double finalValue = evaluatedValue * entry.scaleFactor();
        calculatedValues.put(id, finalValue);
        return finalValue;
    }

    @Override
    public void ping(REST_ENTITY entity) throws Throwable {
        try (var client = new RestPVClient(entity.getHostName() + ":" + entity.getPort(), entity.getApiToken())) {}
    }
}