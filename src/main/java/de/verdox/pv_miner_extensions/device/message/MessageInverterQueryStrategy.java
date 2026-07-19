package de.verdox.pv_miner_extensions.device.message;

import de.verdox.pv_miner.pvsite.inverter.InverterDataDTO;
import de.verdox.pv_miner.pvsite.inverter.InverterEntity;
import de.verdox.solarminer.formula.VariableProvider;
import de.verdox.solarminer.rest.RestPVConfig;
import java.util.Map;

public class MessageInverterQueryStrategy<E extends InverterEntity & MessageEntity<InverterDataDTO>>
        extends MessageQueryStrategy<InverterDataDTO, E> {
    @Override protected InverterDataDTO createResult(RestPVConfig.ConfigSection c, E entity, Map<String, Double> v,
                                                      Map<String, String> p, VariableProvider provider) throws Exception {
        double legacy = hasEntry(c, "pv_power") ? evaluateEntry("pv_power", c, entity, v, p, provider) * 1000 : 0;
        double dc = hasEntry(c, "current_dc_power") ? evaluateEntry("current_dc_power", c, entity, v, p, provider) : legacy;
        double ac = hasEntry(c, "current_ac_power") ? evaluateEntry("current_ac_power", c, entity, v, p, provider) : legacy;
        return new InverterDataDTO((int) dc, evaluateEntry("current_dc_voltage", c, entity, v, p, provider),
                (int) ac, evaluateEntry("current_ac_voltage", c, entity, v, p, provider),
                evaluateEntry("grid_frequency", c, entity, v, p, provider),
                (long) evaluateEntry("total_energy_yield", c, entity, v, p, provider),
                evaluateEntry("internal_temperature", c, entity, v, p, provider),
                (int) evaluateEntry("status_code", c, entity, v, p, provider));
    }
}
