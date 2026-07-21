package de.verdox.pv_miner.setup;

import de.verdox.pv_miner.dto.SetupRequests;
import de.verdox.pv_miner.influx.QueryResult;
import de.verdox.pv_miner.pvconfig.DeviceProfileConfigurationException;
import de.verdox.pv_miner.pvsite.battery.BatteryDataDTO;
import de.verdox.pv_miner.pvsite.inverter.InverterDataDTO;
import de.verdox.pv_miner.pvsite.smartmeter.SmartMeterDataDTO;
import de.verdox.pv_miner_extensions.device.message.MessageBatteryQueryStrategy;
import de.verdox.pv_miner_extensions.device.message.MessageInverterQueryStrategy;
import de.verdox.pv_miner_extensions.device.message.MessageSmartMeterQueryStrategy;
import de.verdox.pv_miner_extensions.device.message.MessageConfigStorage;
import de.verdox.pv_miner_extensions.device.modbus.ModbusConfigStorage;
import de.verdox.pv_miner_extensions.device.modbus.battery.ModbusBatteryQueryStrategy;
import de.verdox.pv_miner_extensions.device.modbus.inverter.ModbusInverterQueryStrategy;
import de.verdox.pv_miner_extensions.device.modbus.smartmeter.ModbusSmartMeterQueryStrategy;
import de.verdox.pv_miner_extensions.device.mqtt.battery.MqttBattery;
import de.verdox.pv_miner_extensions.device.mqtt.inverter.MqttInverter;
import de.verdox.pv_miner_extensions.device.mqtt.smartmeter.MqttSmartMeter;
import de.verdox.pv_miner_extensions.device.rest.RestConfigStorage;
import de.verdox.pv_miner_extensions.device.rest.battery.RestBatteryQueryStrategy;
import de.verdox.pv_miner_extensions.device.rest.inverter.RestInverterQueryStrategy;
import de.verdox.pv_miner_extensions.device.rest.smartmeter.RestSmartMeterQueryStrategy;
import de.verdox.pv_miner_extensions.device.websocket.battery.WebSocketBattery;
import de.verdox.pv_miner_extensions.device.websocket.inverter.WebSocketInverter;
import de.verdox.pv_miner_extensions.device.websocket.smartmeter.WebSocketSmartMeter;
import de.verdox.solarminer.modbus.ModbusRegisterClient;
import de.verdox.solarminer.modbusrtu.RTUModbusClient;
import de.verdox.solarminer.modbustcp.ModbusConfig;
import de.verdox.solarminer.modbustcp.ModbusConfigCreatorTemplate;
import de.verdox.solarminer.modbustcp.TCPModbusClient;
import de.verdox.solarminer.rest.RestConfigCreatorTemplate;
import de.verdox.solarminer.rest.RestPVClient;
import de.verdox.solarminer.rest.RestPVConfig;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PvDevicePreviewService {
    private static final String AVAILABLE = "AVAILABLE";
    private static final String INCONCLUSIVE = "INCONCLUSIVE";
    private static final String NO_RESPONSE = "NO_RESPONSE";
    private static final String ERROR = "ERROR";

    private final ModbusConfigStorage modbusConfigStorage;
    private final RestConfigStorage restConfigStorage;
    private final MessageConfigStorage messageConfigStorage;

    public PvDevicePreviewService(ModbusConfigStorage modbusConfigStorage,
                                  RestConfigStorage restConfigStorage,
                                  MessageConfigStorage messageConfigStorage) {
        this.modbusConfigStorage = modbusConfigStorage;
        this.restConfigStorage = restConfigStorage;
        this.messageConfigStorage = messageConfigStorage;
    }

    /**
     * Queries temporary devices only. No entity is persisted and no result is written to InfluxDB.
     * The method is synchronized so repeated browser polling cannot overlap against a serial device.
     */
    public synchronized SetupRequests.PvDevicePreviewDto preview(SetupRequests.PvDevicePreviewRequest request) {
        if (request == null) throw badRequest("Preview request is required");
        String providerId = required(request.providerId(), "Provider is required");
        return switch (providerId) {
            case SetupService.MODBUS, SetupService.MODBUS_RTU -> previewModbus(providerId, request.values());
            case SetupService.REST -> previewRest(request.values());
            case SetupService.MQTT, SetupService.WEBSOCKET -> previewMessages(providerId, request.values());
            default -> throw badRequest("Unsupported PV source");
        };
    }

    private SetupRequests.PvDevicePreviewDto previewModbus(String providerId, Map<String, String> values) {
        String profile = required(values.get("profile"), "Device profile is required");
        ModbusConfig config;
        try {
            config = modbusConfigStorage.loadConfig(profile);
        } catch (Exception exception) {
            throw badRequest("Unknown Modbus device profile");
        }
        try (ModbusRegisterClient client = SetupService.MODBUS.equals(providerId)
                ? new TCPModbusClient(required(values.get("host"), "Host is required"), integer(values, "port", 1, 65535), integer(values, "slaveId", 1, 247))
                : new RTUModbusClient(required(values.get("serialPort"), "Serial port is required"),
                integer(values, "baudRate", 300, 1_000_000), integer(values, "dataBits", 7, 8),
                integer(values, "stopBits", 1, 2), required(values.get("parity"), "Parity is required").toUpperCase(Locale.ROOT),
                integer(values, "slaveId", 1, 247))) {
            List<SetupRequests.PvDeviceSectionPreviewDto> previews = new ArrayList<>();
            for (Map.Entry<String, ModbusConfig.ConfigSection> entry : config.getSections().entrySet()) {
                previews.add(previewModbusSection(config, entry.getKey(), entry.getValue(), client));
            }
            return new SetupRequests.PvDevicePreviewDto(previews);
        } catch (Exception exception) {
            return unavailableModbus(config, exception);
        }
    }

    private SetupRequests.PvDeviceSectionPreviewDto previewModbusSection(ModbusConfig config, String key,
                                                                          ModbusConfig.ConfigSection section,
                                                                          ModbusRegisterClient client) {
        try {
            QueryResult result = switch (section.getTemplateId()) {
                case "battery" -> new ModbusBatteryQueryStrategy().querySection(config, key, client);
                case "inverter" -> new ModbusInverterQueryStrategy().querySection(config, key, client);
                case "smart_meter" -> new ModbusSmartMeterQueryStrategy().querySection(config, key, client);
                default -> throw new DeviceProfileConfigurationException("Unsupported section type " + section.getTemplateId());
            };
            return success(key, deviceType(section.getTemplateId()), section.getName(), result);
        } catch (Exception exception) {
            return failure(key, deviceType(section.getTemplateId()), section.getName(), exception);
        }
    }

    private SetupRequests.PvDevicePreviewDto unavailableModbus(ModbusConfig config, Exception exception) {
        return new SetupRequests.PvDevicePreviewDto(config.getSections().entrySet().stream()
                .map(entry -> failure(entry.getKey(), deviceType(entry.getValue().getTemplateId()),
                        entry.getValue().getName(), exception))
                .toList());
    }

    private SetupRequests.PvDevicePreviewDto previewRest(Map<String, String> values) {
        String profile = required(values.get("profile"), "Device profile is required");
        RestPVConfig config;
        try {
            config = restConfigStorage.loadConfig(profile);
        } catch (Exception exception) {
            throw badRequest("Unknown REST device profile");
        }
        String host = required(values.get("host"), "Host is required");
        if (!host.startsWith("http://") && !host.startsWith("https://")) host = "http://" + host;
        try (RestPVClient client = new RestPVClient(host + ":" + integer(values, "port", 1, 65535),
                values.getOrDefault("apiToken", ""), config.getAuthenticationType())) {
            List<SetupRequests.PvDeviceSectionPreviewDto> previews = new ArrayList<>();
            for (Map.Entry<String, RestPVConfig.ConfigSection> entry : config.getSections().entrySet()) {
                previews.add(previewRestSection(config, entry.getKey(), entry.getValue(), client));
            }
            return new SetupRequests.PvDevicePreviewDto(previews);
        } catch (Exception exception) {
            return new SetupRequests.PvDevicePreviewDto(config.getSections().entrySet().stream()
                    .map(entry -> failure(entry.getKey(), deviceType(entry.getValue().getTemplateId()), entry.getValue().getName(), exception))
                    .toList());
        }
    }

    private SetupRequests.PvDeviceSectionPreviewDto previewRestSection(RestPVConfig config, String key,
                                                                        RestPVConfig.ConfigSection section,
                                                                        RestPVClient client) {
        try {
            QueryResult result = switch (section.getTemplateId()) {
                case "battery" -> new RestBatteryQueryStrategy().querySection(config, key, client);
                case "inverter" -> new RestInverterQueryStrategy().querySection(config, key, client);
                case "smart_meter", "home_assistant_pv" -> new RestSmartMeterQueryStrategy().querySection(config, key, client);
                default -> throw new DeviceProfileConfigurationException("Unsupported section type " + section.getTemplateId());
            };
            return success(key, deviceType(section.getTemplateId()), section.getName(), result);
        } catch (Exception exception) {
            return failure(key, deviceType(section.getTemplateId()), section.getName(), exception);
        }
    }

    private SetupRequests.PvDevicePreviewDto previewMessages(String providerId, Map<String, String> values) {
        String protocol = SetupService.MQTT.equals(providerId) ? MessageConfigStorage.MQTT : MessageConfigStorage.WEBSOCKET;
        String profile = required(values.get("profile"), "Device profile is required");
        RestPVConfig config;
        try {
            config = messageConfigStorage.loadConfig(protocol, profile);
        } catch (Exception exception) {
            throw badRequest("Unknown " + protocol + " device profile");
        }
        List<SetupRequests.PvDeviceSectionPreviewDto> previews = new ArrayList<>();
        Map<String, String> sharedPayloads = new HashMap<>();
        for (Map.Entry<String, RestPVConfig.ConfigSection> entry : config.getSections().entrySet()) {
            previews.add(previewMessageSection(providerId, values, config, entry.getKey(), entry.getValue(), sharedPayloads));
        }
        return new SetupRequests.PvDevicePreviewDto(previews);
    }

    private SetupRequests.PvDeviceSectionPreviewDto previewMessageSection(String providerId, Map<String, String> values,
                                                                           RestPVConfig config, String key,
                                                                           RestPVConfig.ConfigSection section,
                                                                           Map<String, String> sharedPayloads) {
        try {
            QueryResult result;
            if (SetupService.MQTT.equals(providerId)) {
                result = queryMqtt(values, config, key, section.getTemplateId(), sharedPayloads);
            } else {
                result = queryWebSocket(values, config, key, section.getTemplateId(), sharedPayloads);
            }
            return success(key, deviceType(section.getTemplateId()), section.getName(), result);
        } catch (Exception exception) {
            return failure(key, deviceType(section.getTemplateId()), section.getName(), exception);
        }
    }

    private QueryResult queryMqtt(Map<String, String> values, RestPVConfig config, String key, String templateId,
                                  Map<String, String> sharedPayloads) throws Exception {
        String broker = required(values.get("brokerUri"), "Broker URI is required");
        String clientId = values.getOrDefault("clientId", "");
        String username = values.getOrDefault("username", "");
        String password = values.getOrDefault("password", "");
        if ("battery".equals(templateId)) {
            MqttBattery entity = new MqttBattery(); configureMqtt(entity, broker, clientId, username, password);
            return new MessageBatteryQueryStrategy<MqttBattery>().querySection(config, key, entity, sharedPayloads);
        }
        if ("inverter".equals(templateId)) {
            MqttInverter entity = new MqttInverter(); configureMqtt(entity, broker, clientId, username, password);
            return new MessageInverterQueryStrategy<MqttInverter>().querySection(config, key, entity, sharedPayloads);
        }
        MqttSmartMeter entity = new MqttSmartMeter(); configureMqtt(entity, broker, clientId, username, password);
        return new MessageSmartMeterQueryStrategy<MqttSmartMeter>().querySection(config, key, entity, sharedPayloads);
    }

    private QueryResult queryWebSocket(Map<String, String> values, RestPVConfig config, String key, String templateId,
                                       Map<String, String> sharedPayloads) throws Exception {
        String url = required(values.get("url"), "WebSocket URL is required");
        String token = values.getOrDefault("apiToken", "");
        if ("battery".equals(templateId)) {
            WebSocketBattery entity = new WebSocketBattery(); configureWebSocket(entity, url, token);
            return new MessageBatteryQueryStrategy<WebSocketBattery>().querySection(config, key, entity, sharedPayloads);
        }
        if ("inverter".equals(templateId)) {
            WebSocketInverter entity = new WebSocketInverter(); configureWebSocket(entity, url, token);
            return new MessageInverterQueryStrategy<WebSocketInverter>().querySection(config, key, entity, sharedPayloads);
        }
        WebSocketSmartMeter entity = new WebSocketSmartMeter(); configureWebSocket(entity, url, token);
        return new MessageSmartMeterQueryStrategy<WebSocketSmartMeter>().querySection(config, key, entity, sharedPayloads);
    }

    private void configureMqtt(de.verdox.pv_miner_extensions.device.mqtt.MqttEntity<?> entity, String broker,
                               String clientId, String username, String password) {
        if (entity instanceof MqttBattery value) { value.setBrokerUri(broker); value.setClientId(clientId); value.setUsername(username); value.setPassword(password); }
        else if (entity instanceof MqttInverter value) { value.setBrokerUri(broker); value.setClientId(clientId); value.setUsername(username); value.setPassword(password); }
        else if (entity instanceof MqttSmartMeter value) { value.setBrokerUri(broker); value.setClientId(clientId); value.setUsername(username); value.setPassword(password); }
    }

    private void configureWebSocket(de.verdox.pv_miner_extensions.device.websocket.WebSocketEntity<?> entity,
                                    String url, String token) {
        if (entity instanceof WebSocketBattery value) { value.setUrl(url); value.setApiToken(token); }
        else if (entity instanceof WebSocketInverter value) { value.setUrl(url); value.setApiToken(token); }
        else if (entity instanceof WebSocketSmartMeter value) { value.setUrl(url); value.setApiToken(token); }
    }

    private SetupRequests.PvDeviceSectionPreviewDto success(String key, String type, String name, QueryResult result) {
        List<SetupRequests.PvDevicePreviewValueDto> values = values(result);
        boolean meaningful = values.stream().anyMatch(value -> Double.isFinite(value.value()) && Math.abs(value.value()) > 0.0001);
        return new SetupRequests.PvDeviceSectionPreviewDto(key, type, name,
                meaningful ? AVAILABLE : INCONCLUSIVE, values, "");
    }

    private SetupRequests.PvDeviceSectionPreviewDto failure(String key, String type, String name, Exception exception) {
        String status = exception instanceof DeviceProfileConfigurationException ? ERROR : NO_RESPONSE;
        return new SetupRequests.PvDeviceSectionPreviewDto(key, type, name, status, List.of(), safeMessage(exception));
    }

    private List<SetupRequests.PvDevicePreviewValueDto> values(QueryResult result) {
        if (result instanceof BatteryDataDTO value) return List.of(
                metric("state_of_charge", value.stateOfChargePct(), "%"),
                metric("current_power", value.currentPowerW(), "W"),
                metric("temperature", value.temperatureC(), "°C"),
                metric("state_of_health", value.stateOfHealthPct(), "%"));
        if (result instanceof InverterDataDTO value) return List.of(
                metric("current_ac_power", value.currentAcPowerW(), "W"),
                metric("current_dc_power", value.currentDcPowerW(), "W"),
                metric("current_ac_voltage", value.currentAcVoltageV(), "V"),
                metric("total_energy_yield", value.totalEnergyYieldWh(), "Wh"));
        if (result instanceof SmartMeterDataDTO value) return List.of(
                metric("total_active_power", value.totalActivePowerW(), "W"),
                metric("voltage_l1", value.voltageL1V(), "V"),
                metric("total_imported", value.totalImportedWh(), "Wh"),
                metric("total_exported", value.totalExportedWh(), "Wh"));
        return List.of();
    }

    private SetupRequests.PvDevicePreviewValueDto metric(String key, double value, String unit) {
        return new SetupRequests.PvDevicePreviewValueDto(key, value, unit);
    }

    private String deviceType(String templateId) {
        if (ModbusConfigCreatorTemplate.INVERTER.id().equals(templateId) || RestConfigCreatorTemplate.INVERTER.id().equals(templateId)) return "INVERTER";
        if (ModbusConfigCreatorTemplate.BATTERY.id().equals(templateId) || RestConfigCreatorTemplate.BATTERY.id().equals(templateId)) return "BATTERY";
        return "SMART_METER";
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
        return message.length() > 180 ? message.substring(0, 180) : message;
    }

    private int integer(Map<String, String> values, String key, int minimum, int maximum) {
        try {
            int value = Integer.parseInt(required(values.get(key), key + " is required"));
            if (value < minimum || value > maximum) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw badRequest(key + " must be between " + minimum + " and " + maximum);
        }
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) throw badRequest(message);
        return value.trim();
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
