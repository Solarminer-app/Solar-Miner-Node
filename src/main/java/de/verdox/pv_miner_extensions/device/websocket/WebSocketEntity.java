package de.verdox.pv_miner_extensions.device.websocket;

import de.verdox.pv_miner.influx.QueryResult;
import de.verdox.pv_miner_extensions.device.message.MessageConfigStorage;
import de.verdox.pv_miner_extensions.device.message.MessageEntity;

public interface WebSocketEntity<RESULT extends QueryResult> extends MessageEntity<RESULT> {
    String getUrl();
    String getApiToken();

    @Override
    default String getMessageProtocol() { return MessageConfigStorage.WEBSOCKET; }
}
