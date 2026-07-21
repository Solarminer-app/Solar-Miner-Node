package de.verdox.solarminer.modbusrtu;

import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;
import com.intelligt.modbus.jlibmodbus.master.ModbusMasterRTU;
import com.intelligt.modbus.jlibmodbus.serial.SerialParameters;
import com.intelligt.modbus.jlibmodbus.serial.SerialPort;
import com.intelligt.modbus.jlibmodbus.serial.SerialPortFactoryJSerialComm;
import com.intelligt.modbus.jlibmodbus.serial.SerialUtils;
import de.verdox.solarminer.modbus.ModbusRegisterClient;
import de.verdox.solarminer.modbustcp.ModbusConfig;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A short-lived Modbus RTU connection backed by jSerialComm.
 *
 * <p>Only one master may use a serial adapter at a time. SolarMiner can poll an inverter, battery
 * and smart meter from the same physical device concurrently, therefore access is serialized per
 * port for the complete connection lifetime.</p>
 */
public final class RTUModbusClient implements ModbusRegisterClient {
    private static final Object SERIAL_FACTORY_LOCK = new Object();
    private static final ConcurrentHashMap<String, ReentrantLock> PORT_LOCKS = new ConcurrentHashMap<>();
    private final int slaveId;
    private final ModbusMaster master;
    private final ReentrantLock portLock;
    private boolean closed;

    public RTUModbusClient(String serialPort, int baudRate, int dataBits, int stopBits,
                           String parity, int slaveId) throws Exception {
        this.slaveId = slaveId;
        String normalizedPort = serialPort.trim();
        this.portLock = PORT_LOCKS.computeIfAbsent(normalizedPort, ignored -> new ReentrantLock(true));
        boolean acquired = false;
        try {
            acquired = portLock.tryLock(10, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IOException("Serial port is busy: " + normalizedPort);
            }
            synchronized (SERIAL_FACTORY_LOCK) {
                SerialUtils.setSerialPortFactory(new SerialPortFactoryJSerialComm());
            }
            SerialParameters parameters = new SerialParameters(
                    normalizedPort,
                    SerialPort.BaudRate.getBaudRate(baudRate),
                    dataBits,
                    stopBits,
                    SerialPort.Parity.valueOf(parity.trim().toUpperCase())
            );
            ModbusMaster connectedMaster = new ModbusMasterRTU(parameters);
            connectedMaster.setResponseTimeout(5000);
            connectedMaster.connect();
            master = connectedMaster;
        } catch (Exception exception) {
            if (acquired) portLock.unlock();
            throw exception;
        }
    }

    @Override
    public synchronized Object read(int addressOffset, ModbusConfig.Entry<?> entry) throws Exception {
        byte[] registers = entry.readOperationType().getModbusReadOperation()
                .readRegisters(master, slaveId, entry, addressOffset);
        if (registers == null || registers.length == 0) return null;
        try {
            ByteBuffer buffer = ByteBuffer.wrap(registers).order(entry.byteOrder());
            return entry.modbusParameterType().parser().apply(buffer);
        } catch (java.nio.BufferUnderflowException exception) {
            return null;
        }
    }

    @Override
    public boolean verifyFingerprint(int addressOffset, ModbusConfig.Fingerprint fingerprint) {
        if (fingerprint == null) return false;
        try {
            Object value = read(addressOffset, fingerprint.toDummyEntry());
            if (value == null) return false;
            String actual = value instanceof Number number && number.doubleValue() == number.longValue()
                    ? Long.toString(number.longValue()) : value.toString().trim();
            return actual.equals(fingerprint.expectedValue().trim());
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) return;
        try {
            if (master.isConnected()) master.disconnect();
        } catch (Exception exception) {
            throw new IOException("Could not close Modbus RTU connection", exception);
        } finally {
            closed = true;
            portLock.unlock();
        }
    }
}
