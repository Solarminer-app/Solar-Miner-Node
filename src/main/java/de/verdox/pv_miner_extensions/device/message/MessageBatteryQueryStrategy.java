package de.verdox.pv_miner_extensions.device.message;

import de.verdox.pv_miner.pvsite.battery.BatteryDataDTO;
import de.verdox.pv_miner.pvsite.battery.BatteryEntity;
import de.verdox.solarminer.formula.VariableProvider;
import de.verdox.solarminer.rest.RestPVConfig;
import java.util.Map;

public class MessageBatteryQueryStrategy<E extends BatteryEntity & MessageEntity<BatteryDataDTO>>
        extends MessageQueryStrategy<BatteryDataDTO, E> {
    @Override protected BatteryDataDTO createResult(RestPVConfig.ConfigSection c, E entity, Map<String, Double> v,
                                                     Map<String, String> p, VariableProvider provider) throws Exception {
        double soc = hasEntry(c, "state_of_charge") ? evaluateEntry("state_of_charge", c, entity, v, p, provider)
                : evaluateEntry("battery_soc", c, entity, v, p, provider);
        double power = hasEntry(c, "current_power") ? evaluateEntry("current_power", c, entity, v, p, provider)
                : evaluateEntry("battery_power", c, entity, v, p, provider) * 1000;
        return new BatteryDataDTO(soc, (int) power,
                (int) evaluateEntry("max_charge_power", c, entity, v, p, provider),
                (int) evaluateEntry("max_discharge_power", c, entity, v, p, provider),
                evaluateEntry("state_of_health", c, entity, v, p, provider),
                evaluateEntry("temperature", c, entity, v, p, provider));
    }
}
