package de.verdox.currencyrates.currencyrates;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
public class CurrencyRatesMicroService {
    public static void main(String[] args) {
        SpringApplication.run(CurrencyRatesMicroService.class, args);
    }
}
