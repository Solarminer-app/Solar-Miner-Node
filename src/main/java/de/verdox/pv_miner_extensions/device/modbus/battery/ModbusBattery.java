package de.verdox.pv_miner_extensions.device.modbus.battery;

import de.verdox.pv_miner.pvsite.battery.BatteryDataDTO;
import de.verdox.pv_miner.pvsite.battery.BatteryEntity;
import de.verdox.pv_miner_extensions.device.modbus.ModbusEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
public class ModbusBattery extends BatteryEntity implements ModbusEntity<BatteryDataDTO> {
    private String ipAddress;
    private int port;
    private int slaveId;
    private String modbusConfigName;
    private String sectionKey;
}
