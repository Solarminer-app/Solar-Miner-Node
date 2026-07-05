package de.verdox.pv_miner.core.graal;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(BraiinsNativeHints.class)
public class NativeConfiguration {
}
