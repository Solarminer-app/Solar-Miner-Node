package de.verdox.pv_miner_extensions.device.modbus;

import de.verdox.pv_miner.entity.QueryEntity;
import de.verdox.pv_miner.influx.QueryResult;

/** Common profile metadata shared by Modbus TCP and Modbus RTU devices. */
public interface ModbusProfileEntity<RESULT extends QueryResult> extends QueryEntity<RESULT> {
    String getModbusConfigName();
    String getSectionKey();
}
