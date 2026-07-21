package de.verdox.currencyrates.currencyrates.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Optional;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI currencyRatesOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SolarMiner Currency Rates API")
                        .description("Historical exchange-rate and Bitcoin-network data used by SolarMiner financial calculations.")
                        .version(implementationVersion())
                        .license(new License().name("AGPL-3.0")))
                .tags(List.of(
                        new Tag().name("Market data").description("Historical currency and Bitcoin-network values.")
                ));
    }

    private String implementationVersion() {
        return Optional.ofNullable(OpenApiConfiguration.class.getPackage().getImplementationVersion())
                .orElse("development");
    }
}
