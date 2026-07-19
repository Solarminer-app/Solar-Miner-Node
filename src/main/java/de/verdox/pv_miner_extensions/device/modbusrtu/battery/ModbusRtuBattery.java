package de.verdox.pv_miner_extensions.device.modbusrtu.battery;

import de.verdox.pv_miner.pvsite.battery.BatteryDataDTO;
import de.verdox.pv_miner.pvsite.battery.BatteryEntity;
import de.verdox.pv_miner_extensions.device.modbusrtu.ModbusRtuEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity
public class ModbusRtuBattery extends BatteryEntity implements ModbusRtuEntity<BatteryDataDTO> {
    private String serialPort;
    private int baudRate;
    private int dataBits;
    private int stopBits;
    private String parity;
    private int slaveId;
    private String modbusConfigName;
    private String sectionKey;
}
