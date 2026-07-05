package de.verdox.pv_miner.core.graal;

import de.verdox.pv_miner.core.miner.braiins.graphql.BraiinsQuery;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class BraiinsNativeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.resources().registerPattern("graphql/braiins/*.graphql");
        hints.reflection().registerType(BraiinsQuery.class);
    }
}
