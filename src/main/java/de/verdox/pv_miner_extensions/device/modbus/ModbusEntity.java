package de.verdox.pv_miner_extensions.device.modbus;

import de.verdox.pv_miner.entity.QueryEntity;
import de.verdox.pv_miner.influx.QueryResult;

public interface ModbusEntity<RESULT extends QueryResult> extends ModbusProfileEntity<RESULT> {
    String getIpAddress();
    int getPort();
    int getSlaveId();
}
