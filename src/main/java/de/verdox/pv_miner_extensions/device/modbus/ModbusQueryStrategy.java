package de.verdox.pv_miner_extensions.device.modbus;

import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.influx.QueryResult;
import de.verdox.solarminer.formula.FormulaEngine;
import de.verdox.solarminer.formula.VariableProvider;
import de.verdox.solarminer.modbustcp.ModbusConfig;
import de.verdox.solarminer.modbustcp.TCPModbusClient;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public abstract class ModbusQueryStrategy<RESULT extends QueryResult, MODBUS_ENTITY extends ModbusEntity<RESULT>> implements EntityQueryService.Strategy<MODBUS_ENTITY, RESULT> {
    @Override
    public RESULT query(EntityQueryService entityQueryService, MODBUS_ENTITY entity) throws Throwable {
        ModbusConfig modbusConfig = SpringContextHelper.getBean(ModbusConfigStorage.class).loadConfig(entity.getModbusConfigName());

        ModbusConfig.ConfigSection section = modbusConfig.getSection(entity.getSectionKey());
        if (section == null) {
            return null;
        }

        ModbusClientManager clientManager = SpringContextHelper.getBean(ModbusClientManager.class);
        TCPModbusClient client = clientManager.getClient(entity.getIpAddress(), entity.getPort(), entity.getSlaveId());

        Map<String, Double> calculatedValues = new HashMap<>();
        VariableProvider variableProvider = new VariableProvider() {
            @Override
            public double getValueFor(String variableName) {
                try {
                    return evaluateEntry(variableName, section, client, calculatedValues, this, modbusConfig.getAddressOffset());
                } catch (Exception e) {
                    return 0.0;
                }
            }
        };
        return createResult(section, client, calculatedValues, variableProvider, modbusConfig.getAddressOffset());
    }

    protected abstract RESULT createResult(ModbusConfig.ConfigSection section, TCPModbusClient client, Map<String, Double> calculatedValues, VariableProvider provider, int offset) throws Exception;

    protected static double evaluateEntry(String id, ModbusConfig.ConfigSection section, TCPModbusClient client, Map<String, Double> calculatedValues, VariableProvider provider, int offset) throws Exception {
        if (calculatedValues.containsKey(id)) {
            return calculatedValues.get(id);
        }

        ModbusConfig.Entry<?> entry;
        try {
            entry = section.getEntryForId(id);
        }
        catch (NoSuchElementException e) {
            return 0;
        }
        Object rawObj = client.read(offset, entry);
        double rawValue = 0.0;
        if (rawObj instanceof Number n) {
            rawValue = n.doubleValue();
        } else if (rawObj instanceof String s) {
            try {
                rawValue = Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {}
        }
        double evaluatedValue = FormulaEngine.evaluate(rawValue, entry.formula(), provider);
        double finalValue = evaluatedValue * entry.scaleFactor();
        calculatedValues.put(id, finalValue);
        return finalValue;
    }

    @Override
    public void ping(MODBUS_ENTITY entity) throws Throwable {
        ModbusClientManager clientManager = SpringContextHelper.getBean(ModbusClientManager.class);
        clientManager.getClient(entity.getIpAddress(), entity.getPort(), entity.getSlaveId());
    }
}