package de.verdox.pv_miner_extensions.modbus;

import de.verdox.pv_miner.pvsite.PVSiteEntity;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
public class ModbusPVSite extends PVSiteEntity {

    private String ipAddress;
    private int port;
    private int slaveId;
    private String modbusConfigName;

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getSlaveId() {
        return slaveId;
    }

    public void setSlaveId(int slaveId) {
        this.slaveId = slaveId;
    }

    public String getModbusConfigName() {
        return modbusConfigName;
    }

    public void setModbusConfigName(String modbusConfigName) {
        this.modbusConfigName = modbusConfigName;
    }
}
