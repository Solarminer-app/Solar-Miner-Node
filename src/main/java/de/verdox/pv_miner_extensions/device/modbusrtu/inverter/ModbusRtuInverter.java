package de.verdox.pv_miner_extensions.device.modbusrtu.inverter;

import de.verdox.pv_miner.pvsite.inverter.InverterDataDTO;
import de.verdox.pv_miner.pvsite.inverter.InverterEntity;
import de.verdox.pv_miner_extensions.device.modbusrtu.ModbusRtuEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity
public class ModbusRtuInverter extends InverterEntity implements ModbusRtuEntity<InverterDataDTO> {
    private String serialPort;
    private int baudRate;
    private int dataBits;
    private int stopBits;
    private String parity;
    private int slaveId;
    private String modbusConfigName;
    private String sectionKey;
}
