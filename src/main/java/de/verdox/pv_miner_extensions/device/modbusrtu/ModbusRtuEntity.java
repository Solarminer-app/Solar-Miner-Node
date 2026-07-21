package de.verdox.pv_miner_extensions.device.modbusrtu;

import de.verdox.pv_miner.influx.QueryResult;
import de.verdox.pv_miner_extensions.device.modbus.ModbusProfileEntity;

public interface ModbusRtuEntity<RESULT extends QueryResult> extends ModbusProfileEntity<RESULT> {
    String getSerialPort();
    int getBaudRate();
    int getDataBits();
    int getStopBits();
    String getParity();
    int getSlaveId();
}
