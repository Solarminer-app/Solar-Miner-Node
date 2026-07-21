package de.verdox.solarminer.modbus;

import de.verdox.solarminer.modbustcp.ModbusConfig;

public interface ModbusRegisterClient extends AutoCloseable {
    Object read(int addressOffset, ModbusConfig.Entry<?> configEntry) throws Exception;

    boolean verifyFingerprint(int addressOffset, ModbusConfig.Fingerprint fingerprint);
}
