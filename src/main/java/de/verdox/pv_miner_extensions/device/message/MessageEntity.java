package de.verdox.pv_miner_extensions.device.message;

import de.verdox.pv_miner.entity.QueryEntity;
import de.verdox.pv_miner.influx.QueryResult;

public interface MessageEntity<RESULT extends QueryResult> extends QueryEntity<RESULT> {
    String getMessageConfigName();
    void setMessageConfigName(String name);
    String getSectionKey();
    void setSectionKey(String sectionKey);
    String getMessageProtocol();
}
