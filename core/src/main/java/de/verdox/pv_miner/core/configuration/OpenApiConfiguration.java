package de.verdox.pv_miner.core.configuration;

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
    public OpenAPI solarMinerCoreOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SolarMiner Core API")
                        .description("Low-level miner discovery and control API. This service is intended for trusted SolarMiner nodes and product integrations, not direct Internet exposure.")
                        .version(implementationVersion())
                        .license(new License().name("AGPL-3.0")))
                .tags(List.of(
                        new Tag().name("Miner control").description("Miner detection, credentials, telemetry, power and pool targets."),
                        new Tag().name("Developer fee").description("Developer-fee distribution and referral validation.")
                ));
    }

    private String implementationVersion() {
        return Optional.ofNullable(OpenApiConfiguration.class.getPackage().getImplementationVersion())
                .orElse("development");
    }
}
