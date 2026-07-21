package de.verdox.pv_miner_extensions.device.modbus.inverter;

import de.verdox.pv_miner.pvsite.inverter.InverterDataDTO;
import de.verdox.pv_miner.pvsite.inverter.InverterEntity;
import de.verdox.pv_miner_extensions.device.modbus.ModbusEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
public class ModbusInverter extends InverterEntity implements ModbusEntity<InverterDataDTO> {
    private String ipAddress;
    private int port;
    private int slaveId;
    private String modbusConfigName;
    private String sectionKey;
}
