package de.verdox.solarminer.modbustcp;

import com.intelligt.modbus.jlibmodbus.exception.ModbusIOException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusNumberException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusProtocolException;
import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;
import com.intelligt.modbus.jlibmodbus.master.ModbusMasterTCP;
import com.intelligt.modbus.jlibmodbus.tcp.TcpParameters;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TCPModbusClient implements Closeable {
    private static final Logger LOGGER = Logger.getLogger(TCPModbusClient.class.getName());
    private String ipAddress;
    private int port;
    private int slaveId;
    private ModbusMaster master;

    private static final Pattern FORMULA_PATTERN = Pattern.compile("x\\s*([+\\-*/%])\\s*([0-9.]+)");

    public TCPModbusClient(String ipAddress, int port, int slaveId) throws Exception {
        this.ipAddress = ipAddress;
        this.port = port;
        this.slaveId = slaveId;

        TcpParameters tcpParameters = new TcpParameters();
        tcpParameters.setHost(InetAddress.getByName(ipAddress));
        tcpParameters.setPort(port);
        tcpParameters.setKeepAlive(true);

        master = new ModbusMasterTCP(tcpParameters);
        master.setResponseTimeout(1000);
        master.connect();
    }

    public Object read(ModbusConfig.Entry<?> configEntry) throws ModbusProtocolException, ModbusNumberException, ModbusIOException {
        byte[] registers = configEntry.readOperationType().getModbusReadOperation().readRegisters(master, slaveId, configEntry);

        Object readFromRegister = parseRegisterValues(registers, configEntry);
        if (readFromRegister instanceof Number number) {
            String formula = configEntry.formula();
            if (formula == null || formula.equalsIgnoreCase("x") || formula.isBlank()) {
                return number.doubleValue();
            }

            Matcher matcher = FORMULA_PATTERN.matcher(formula);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("The formula " + configEntry.formula() + " has invalid syntax");
            }

            String operator = matcher.group(1);
            double scalar = Double.parseDouble(matcher.group(2));

            return applyOperation(number.doubleValue(), scalar, operator);
        }

        return readFromRegister;
    }

    public boolean verifyFingerprint(ModbusConfig.Fingerprint fingerprint) {
        if (fingerprint == null) return false;

        try {
            Object result = read(fingerprint.toDummyEntry());
            String actualValue;

            if (result instanceof Number n) {
                double val = n.doubleValue();
                actualValue = (val == (long) val) ? String.valueOf((long) val) : String.valueOf(val);
            } else {
                actualValue = result.toString().trim();
            }

            return actualValue.equals(fingerprint.expectedValue().trim());
        } catch (Exception e) {
            return false;
        }
    }

    private <T> T parseRegisterValues(byte[] registers, ModbusConfig.Entry<T> configEntry) {
        if (registers == null || registers.length == 0) {
            LOGGER.warning("Modbus returned an empty array at " + configEntry.startAddress());
            return null;
        }

        ByteBuffer buffer = ByteBuffer.wrap(registers);
        buffer.order(configEntry.byteOrder());
        buffer.rewind();

        try {
            return configEntry.modbusParameterType().parser().apply(buffer);
        } catch (java.nio.BufferUnderflowException e) {
            LOGGER.warning("WARNUNG: Buffer to short! " + registers.length + " bytes but " + configEntry.modbusParameterType().identifier() + " needs more!");
            return null;
        }
    }

    private static double applyOperation(double x, double scalar, String operator) {
        return switch (operator) {
            case "+" -> x + scalar;
            case "-" -> x - scalar;
            case "*" -> x * scalar;
            case "/" -> x / scalar;
            case "%" -> x % scalar;
            default -> throw new IllegalArgumentException("Ungültiger Operator: " + operator);
        };
    }

    public void disconnect() throws ModbusIOException {
        if (master != null && master.isConnected()) {
            master.disconnect();
        }
    }

    @Override
    public void close() throws IOException {
        try {
            disconnect();
        } catch (ModbusIOException e) {
            throw new RuntimeException(e);
        }
    }

    public interface ModbusReadOperation {
        byte[] readRegisters(ModbusMaster master, int slaveId, ModbusConfig.Entry<?> configEntry) throws ModbusProtocolException, ModbusNumberException, ModbusIOException;
    }
}