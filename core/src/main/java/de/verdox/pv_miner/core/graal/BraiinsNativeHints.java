package de.verdox.pv_miner.core.graal;

import de.verdox.pv_miner.core.miner.braiins.graphql.BraiinsQuery;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class BraiinsNativeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.resources().registerPattern("graphql/braiins/*.graphql");
        hints.resources().registerPattern("graphql/braiins/auth/*.graphql");
        hints.resources().registerPattern("graphql/braiins/miner/*.graphql");
        hints.resources().registerPattern("graphql/braiins/power/*.graphql");
        hints.resources().registerPattern("graphql/braiins/bos/*.graphql");
        hints.resources().registerPattern("graphql/braiins/pool/*.graphql");
        hints.reflection().registerType(BraiinsQuery.class);
    }
}
