package de.verdox.pv_miner_extensions.inverter.modbustcp;

import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.pvsite.PVSiteDataDTO;
import de.verdox.pv_miner.pvsite.PVSiteQueryStrategy;
import de.verdox.solarminer.formula.FormulaEngine;
import de.verdox.solarminer.formula.VariableProvider;
import de.verdox.solarminer.modbustcp.ModbusConfig;
import de.verdox.solarminer.modbustcp.ModbusConfigCreatorTemplate;
import de.verdox.solarminer.modbustcp.TCPModbusClient;

import java.util.HashMap;
import java.util.Map;

public class ModbusPVSiteQueryStrategy implements PVSiteQueryStrategy<ModbusPVSite> {

    @Override
    public PVSiteDataDTO query(EntityQueryService entityQueryService, ModbusPVSite entity) throws Throwable {
        ModbusConfig modbusConfig = SpringContextHelper.getBean(ModbusConfigStorage.class)
                .loadConfig(ModbusConfigCreatorTemplate.PV_SITE, entity.getModbusConfigName());

        try (var client = new TCPModbusClient(entity.getIpAddress(), entity.getPort(), entity.getSlaveId())) {
            Map<String, Double> calculatedValues = new HashMap<>();

            VariableProvider variableProvider = new VariableProvider() {
                @Override
                public double getValueFor(String variableName) {
                    try {
                        return evaluateEntry(variableName, modbusConfig, client, calculatedValues, this);
                    } catch (Exception e) {
                        return 0.0;
                    }
                }
            };

            double pvPower = evaluateEntry("pv_power", modbusConfig, client, calculatedValues, variableProvider);
            double gridPower = evaluateEntry("grid_power", modbusConfig, client, calculatedValues, variableProvider);
            double batteryPower = evaluateEntry("battery_power", modbusConfig, client, calculatedValues, variableProvider);
            float batterySoC = (float) evaluateEntry("battery_soc", modbusConfig, client, calculatedValues, variableProvider);
            entity.getMiners().forEach(minerEntity -> {
                entityQueryService.getLastResult(minerEntity, MinerStats.DEFAULT).approximatedPowerUsageWatts();
            });
            double approximatedPowerDraw = approximateMiningPowerDrawKw(entityQueryService, entity);

            return createData(builder -> builder
                    .pvPower(pvPower)
                    .gridPower(gridPower)
                    .batteryPower(batteryPower)
                    .batterySoC(batterySoC)
                    .totalMinerPowerKw(approximatedPowerDraw)
            );
        }
    }

    private double evaluateEntry(String id, ModbusConfig config, TCPModbusClient client, Map<String, Double> calculatedValues, VariableProvider provider) throws Exception {
        if (calculatedValues.containsKey(id)) {
            return calculatedValues.get(id);
        }

        ModbusConfig.Entry<?> entry = config.getEntryForId(id);
        Object rawObj = client.read(entry);
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
    public void ping(ModbusPVSite entity) throws Throwable {
        try (var client = new TCPModbusClient(entity.getIpAddress(), entity.getPort(), entity.getSlaveId())) {

        }
    }
}