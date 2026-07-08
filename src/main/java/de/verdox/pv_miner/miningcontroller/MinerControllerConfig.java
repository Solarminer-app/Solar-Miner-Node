package de.verdox.pv_miner.miningcontroller;

import de.verdox.pv_miner.configuration.SimpleConfig;
import de.verdox.pv_miner.miningcontroller.dsl.ControllerDSL;
import de.verdox.vserializer.generic.Serializer;

import java.util.Map;

public class MinerControllerConfig extends SimpleConfig<ControllerDSL.OperatingMode> {
    public static final Serializer<MinerControllerConfig> SERIALIZER = SERIALIZER(
            "miner_controller_config",
            MinerControllerConfig.class,
            ControllerDSL.OperatingMode.SERIALIZER,
            MinerControllerConfig::new
    );

    public MinerControllerConfig(Map<String, ControllerDSL.OperatingMode> configEntries) {
        super(configEntries);
    }
}
