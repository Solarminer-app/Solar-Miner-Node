package de.verdox.pv_miner_extensions.device.modbusrtu.smartmeter;

import de.verdox.pv_miner.pvsite.smartmeter.SmartMeterDataDTO;
import de.verdox.pv_miner.pvsite.smartmeter.SmartMeterEntity;
import de.verdox.pv_miner_extensions.device.modbusrtu.ModbusRtuEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity
public class ModbusRtuSmartMeter extends SmartMeterEntity implements ModbusRtuEntity<SmartMeterDataDTO> {
    private String serialPort;
    private int baudRate;
    private int dataBits;
    private int stopBits;
    private String parity;
    private int slaveId;
    private String modbusConfigName;
    private String sectionKey;
}
