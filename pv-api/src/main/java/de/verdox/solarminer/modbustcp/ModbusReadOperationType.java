package de.verdox.solarminer.modbustcp;
import com.intelligt.modbus.jlibmodbus.msg.request.ReadHoldingRegistersRequest;
import com.intelligt.modbus.jlibmodbus.msg.request.ReadInputRegistersRequest;
import com.intelligt.modbus.jlibmodbus.msg.response.ReadHoldingRegistersResponse;
import com.intelligt.modbus.jlibmodbus.msg.response.ReadInputRegistersResponse;
import de.verdox.vserializer.generic.Serializer;

public enum ModbusReadOperationType {
    READ_HOLDING_REGISTER("0x03 (Read Holding Registers)", (master, slave, entry, offset) -> {
        ReadHoldingRegistersRequest request = new ReadHoldingRegistersRequest();

        request.setServerAddress(slave);
        request.setStartAddress(entry.startAddress()+offset);
        request.setQuantity(entry.size());

        ReadHoldingRegistersResponse response = (ReadHoldingRegistersResponse) master.processRequest(request);

        return response.getBytes();
    }),

    READ_INPUT_REGISTER("0x04 (Read Input Registers)", (master, slave, entry, offset) -> {
        ReadInputRegistersRequest request = new ReadInputRegistersRequest();


        request.setServerAddress(slave);
        request.setStartAddress(entry.startAddress()+offset);
        request.setQuantity(entry.size());

        ReadInputRegistersResponse response = (ReadInputRegistersResponse) master.processRequest(request);
        return response.getBytes();
    });

    public static final Serializer<ModbusReadOperationType> SERIALIZER = Serializer.Enum.create("modbus_read_operation", ModbusReadOperationType.class);

    private final String id;
    private final TCPModbusClient.ModbusReadOperation modbusReadOperation;

    ModbusReadOperationType(String id, TCPModbusClient.ModbusReadOperation modbusReadOperation) {
        this.id = id;
        this.modbusReadOperation = modbusReadOperation;
    }

    public String getId() {
        return id;
    }

    public TCPModbusClient.ModbusReadOperation getModbusReadOperation() {
        return modbusReadOperation;
    }
}
