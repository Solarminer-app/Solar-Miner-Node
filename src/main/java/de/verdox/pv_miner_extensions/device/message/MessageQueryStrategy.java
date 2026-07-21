package de.verdox.pv_miner_extensions.device.message;

import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.influx.QueryResult;
import de.verdox.pv_miner.pvconfig.DeviceProfileConfigurationException;
import de.verdox.pv_miner_extensions.device.mqtt.MqttEntity;
import de.verdox.pv_miner_extensions.device.mqtt.MqttMessageService;
import de.verdox.pv_miner_extensions.device.websocket.WebSocketEntity;
import de.verdox.pv_miner_extensions.device.websocket.WebSocketMessageService;
import de.verdox.solarminer.formula.FormulaEngine;
import de.verdox.solarminer.formula.VariableProvider;
import de.verdox.solarminer.rest.RestHttpMethod;
import de.verdox.solarminer.rest.RestPVClient;
import de.verdox.solarminer.rest.RestPVConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public abstract class MessageQueryStrategy<RESULT extends QueryResult, ENTITY extends MessageEntity<RESULT>>
        implements EntityQueryService.Strategy<ENTITY, RESULT> {
    @Override
    public RESULT query(EntityQueryService queryService, ENTITY entity) throws Throwable {
        RestPVConfig config;
        try {
            config = SpringContextHelper.getBean(MessageConfigStorage.class)
                    .loadConfig(entity.getMessageProtocol(), entity.getMessageConfigName());
        } catch (NoSuchElementException exception) {
            throw new DeviceProfileConfigurationException("Config " + entity.getMessageConfigName() + " was not found");
        }
        return querySection(config, entity.getSectionKey(), entity);
    }

    /** Executes the regular message profile DSL without a persisted entity. */
    public final RESULT querySection(RestPVConfig config, String sectionKey, ENTITY entity) throws Exception {
        return querySection(config, sectionKey, entity, new HashMap<>());
    }

    /** Executes a section while reusing payloads already fetched for sibling sections. */
    public final RESULT querySection(RestPVConfig config, String sectionKey, ENTITY entity,
                                     Map<String, String> sharedPayloads) throws Exception {
        RestPVConfig.ConfigSection section = config.getSection(sectionKey);
        if (section == null) throw new DeviceProfileConfigurationException("No section " + sectionKey + " found in config");
        Map<String, Double> values = new HashMap<>();
        VariableProvider provider = new VariableProvider() {
            @Override public double getValueFor(String variable) {
                try { return evaluateEntry(variable, section, entity, values, sharedPayloads, this); }
                catch (Exception ignored) { return 0; }
            }
        };
        return createResult(section, entity, values, sharedPayloads, provider);
    }

    protected abstract RESULT createResult(RestPVConfig.ConfigSection section, ENTITY entity,
                                           Map<String, Double> values, Map<String, String> payloads,
                                           VariableProvider provider) throws Exception;

    protected double evaluateEntry(String id, RestPVConfig.ConfigSection section, ENTITY entity,
                                   Map<String, Double> values, Map<String, String> payloads,
                                   VariableProvider provider) throws Exception {
        if (values.containsKey(id)) return values.get(id);
        RestPVConfig.Entry<?> entry;
        try { entry = section.getEntryForId(id); }
        catch (NoSuchElementException ignored) { return 0; }
        String sourceKey = entity instanceof MqttEntity<?> ? entry.urlExtension() : "websocket";
        String payload = payloads.get(sourceKey);
        if (payload == null) {
            if (entity instanceof MqttEntity<?> mqtt) {
                payload = SpringContextHelper.getBean(MqttMessageService.class).read(mqtt, entry.urlExtension());
            } else if (entity instanceof WebSocketEntity<?> websocket) {
                payload = SpringContextHelper.getBean(WebSocketMessageService.class).read(websocket);
            } else throw new IllegalArgumentException("Unsupported message transport");
            payloads.put(sourceKey, payload);
        }
        RestPVConfig.Entry<?> rawEntry = new RestPVConfig.Entry<>(entry.urlExtension(), RestHttpMethod.GET,
                entry.responseType(), entry.dataPath(), 1, "x", entry.restParameterType());
        double raw = RestPVClient.parsePayload(payload, rawEntry, sourceKey);
        double result = FormulaEngine.evaluate(raw, entry.formula(), provider) * entry.scaleFactor();
        values.put(id, result);
        return result;
    }

    protected boolean hasEntry(RestPVConfig.ConfigSection section, String id) { return section.getEntries().containsKey(id); }

    @Override
    public void ping(ENTITY entity) throws Throwable {
        if (entity instanceof MqttEntity<?> mqtt) {
            SpringContextHelper.getBean(MqttMessageService.class).ping(mqtt.getBrokerUri(), mqtt.getClientId(), mqtt.getUsername(), mqtt.getPassword());
        } else if (entity instanceof WebSocketEntity<?> websocket) {
            SpringContextHelper.getBean(WebSocketMessageService.class).ping(websocket.getUrl(), websocket.getApiToken());
        }
    }
}
