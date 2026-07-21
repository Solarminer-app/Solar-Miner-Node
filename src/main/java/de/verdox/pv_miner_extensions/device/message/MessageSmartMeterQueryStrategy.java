package de.verdox.pv_miner_extensions.device.message;

import de.verdox.pv_miner.pvsite.smartmeter.SmartMeterDataDTO;
import de.verdox.pv_miner.pvsite.smartmeter.SmartMeterEntity;
import de.verdox.solarminer.formula.VariableProvider;
import de.verdox.solarminer.rest.RestPVConfig;
import java.util.Map;

public class MessageSmartMeterQueryStrategy<E extends SmartMeterEntity & MessageEntity<SmartMeterDataDTO>>
        extends MessageQueryStrategy<SmartMeterDataDTO, E> {
    @Override protected SmartMeterDataDTO createResult(RestPVConfig.ConfigSection c, E entity, Map<String, Double> v,
                                                        Map<String, String> p, VariableProvider provider) throws Exception {
        double total = hasEntry(c, "total_active_power") ? evaluateEntry("total_active_power", c, entity, v, p, provider)
                : evaluateEntry("grid_power", c, entity, v, p, provider) * 1000;
        return new SmartMeterDataDTO((int) total,
                (long) evaluateEntry("total_imported", c, entity, v, p, provider),
                (long) evaluateEntry("total_exported", c, entity, v, p, provider),
                (int) evaluateEntry("power_l1", c, entity, v, p, provider),
                (int) evaluateEntry("power_l2", c, entity, v, p, provider),
                (int) evaluateEntry("power_l3", c, entity, v, p, provider),
                evaluateEntry("voltage_l1", c, entity, v, p, provider),
                evaluateEntry("voltage_l2", c, entity, v, p, provider),
                evaluateEntry("voltage_l3", c, entity, v, p, provider));
    }
}
