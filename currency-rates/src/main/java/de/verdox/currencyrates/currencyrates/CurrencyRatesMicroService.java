package de.verdox.currencyrates.currencyrates;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints(CurrencyRatesMicroService.GraalVMHints.class)
public class CurrencyRatesMicroService {
    public static void main(String[] args) {
        SpringApplication.run(CurrencyRatesMicroService.class, args);
    }

    public static class GraalVMHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.reflection().registerType(
                    TypeReference.of("org.hibernate.internal.log.ConnectionInfoLogger"),
                    MemberCategory.INVOKE_PUBLIC_METHODS
            );

            hints.reflection().registerType(
                    TypeReference.of("org.jboss.logging.Logger"),
                    MemberCategory.INVOKE_PUBLIC_METHODS
            );
        }
    }
}
