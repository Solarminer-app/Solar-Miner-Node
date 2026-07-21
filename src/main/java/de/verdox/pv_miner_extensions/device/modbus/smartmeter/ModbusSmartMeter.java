package de.verdox.pv_miner_extensions.device.modbus.smartmeter;

import de.verdox.pv_miner.pvsite.smartmeter.SmartMeterDataDTO;
import de.verdox.pv_miner.pvsite.smartmeter.SmartMeterEntity;
import de.verdox.pv_miner_extensions.device.modbus.ModbusEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
public class ModbusSmartMeter extends SmartMeterEntity implements ModbusEntity<SmartMeterDataDTO> {
    private String ipAddress;
    private int port;
    private int slaveId;
    private String modbusConfigName;
    private String sectionKey;
}
