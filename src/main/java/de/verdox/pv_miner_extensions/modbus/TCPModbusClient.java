package de.verdox.pv_miner_extensions.modbus;

import com.intelligt.modbus.jlibmodbus.exception.ModbusIOException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusNumberException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusProtocolException;
import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;
import com.intelligt.modbus.jlibmodbus.master.ModbusMasterTCP;
import com.intelligt.modbus.jlibmodbus.msg.request.ReadHoldingRegistersRequest;
import com.intelligt.modbus.jlibmodbus.msg.response.ReadHoldingRegistersResponse;
import com.intelligt.modbus.jlibmodbus.tcp.TcpParameters;
import de.verdox.pv_miner_extensions.modbus.config.ModbusConfig;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TCPModbusClient implements Closeable {
    private static final Logger LOGGER = Logger.getLogger(TCPModbusClient.class.getName());
    private final String ipAddress;
    private final int port;
    private final int slaveId;
    private final ModbusMaster master;

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

    public int findSunSpecBaseAddress() {
        int[] commonAddresses = {40000, 39999, 50000, 49999};

        for (int baseAddress : commonAddresses) {
            try {
                ReadHoldingRegistersRequest request = new ReadHoldingRegistersRequest();
                request.setServerAddress(slaveId);
                request.setStartAddress(baseAddress);
                request.setQuantity(2);

                ReadHoldingRegistersResponse response = (ReadHoldingRegistersResponse) master.processRequest(request);
                byte[] bytes = response.getBytes();

                if (bytes.length >= 4) {
                    if (bytes[0] == 0x53 && bytes[1] == 0x75 && bytes[2] == 0x6E && bytes[3] == 0x53) {
                        return baseAddress;
                    }
                }
            } catch (Exception ignored) {

            }
        }
        return -1;
    }

    public String readSunSpecString(int startAddress, int numRegisters) {
        try {
            ReadHoldingRegistersRequest request = new ReadHoldingRegistersRequest();
            request.setServerAddress(slaveId);
            request.setStartAddress(startAddress);
            request.setQuantity(numRegisters);

            ReadHoldingRegistersResponse response = (ReadHoldingRegistersResponse) master.processRequest(request);
            byte[] bytes = response.getBytes();

            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    public Map<Integer, Integer> scanSunSpecBlocks(int firstModelAddress) {
        Map<Integer, Integer> blockAddresses = new HashMap<>();
        int currentAddress = firstModelAddress;

        try {
            for (int i = 0; i < 50; i++) {
                ReadHoldingRegistersRequest request = new ReadHoldingRegistersRequest();
                request.setServerAddress(slaveId);
                request.setStartAddress(currentAddress);
                request.setQuantity(2);

                ReadHoldingRegistersResponse response = (ReadHoldingRegistersResponse) master.processRequest(request);
                byte[] bytes = response.getBytes();

                if (bytes.length < 4) break;

                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                buffer.order(ByteOrder.BIG_ENDIAN);
                int modelId = buffer.getShort() & 0xFFFF;
                int length = buffer.getShort() & 0xFFFF;

                if (modelId == 0xFFFF || length == 0) {
                    break;
                }

                blockAddresses.put(modelId, currentAddress);

                currentAddress += (2 + length);
            }
        } catch (Exception e) {
            LOGGER.warning("SunSpec Scan an Adresse " + currentAddress + " abgebrochen: " + e.getMessage());
        }

        return blockAddresses;
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