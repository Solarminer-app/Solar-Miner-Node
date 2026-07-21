package de.verdox.pv_miner_extensions.device.rest;

import de.verdox.pv_miner.entity.QueryEntity;
import de.verdox.pv_miner.influx.QueryResult;

public interface RestEntity<RESULT extends QueryResult> extends QueryEntity<RESULT> {
    String getHostName();
    int getPort();
    String getApiToken();
    String getRestConfigName();
    void setRestConfigName(String name);
    String getSectionKey();
    void setSectionKey(String sectionKey);
}
