package de.verdox.pv_miner_extensions.device.mqtt;

import de.verdox.pv_miner.influx.QueryResult;
import de.verdox.pv_miner_extensions.device.message.MessageConfigStorage;
import de.verdox.pv_miner_extensions.device.message.MessageEntity;

public interface MqttEntity<RESULT extends QueryResult> extends MessageEntity<RESULT> {
    String getBrokerUri();
    String getClientId();
    String getUsername();
    String getPassword();

    @Override
    default String getMessageProtocol() { return MessageConfigStorage.MQTT; }
}
