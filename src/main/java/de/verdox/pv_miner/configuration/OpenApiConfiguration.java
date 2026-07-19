package de.verdox.pv_miner.configuration;

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
    public OpenAPI solarMinerNodeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SolarMiner Node API")
                        .description("Public integration API of the local SolarMiner node. It is used by the bundled React frontend and can also be consumed by trusted third-party clients.")
                        .version(implementationVersion())
                        .license(new License().name("AGPL-3.0")))
                .tags(List.of(
                        new Tag().name("Sites").description("PV-site discovery and initial navigation data."),
                        new Tag().name("Dashboard").description("Live values, overview data and historical charts."),
                        new Tag().name("PV site details").description("PV devices, panel groups, tariffs and site preferences."),
                        new Tag().name("Mining").description("Miner, pool and cluster management for a PV site."),
                        new Tag().name("Miner analytics").description("Detailed telemetry and statistics for one miner."),
                        new Tag().name("Cluster configurations").description("Controller DSL configurations and simulations."),
                        new Tag().name("PV profiles").description("REST and Modbus/TCP device-profile editing and testing."),
                        new Tag().name("Setup").description("Capabilities, discovery, validation and initial site creation."),
                        new Tag().name("Finance").description("Financial dashboard data, sales and report exports."),
                        new Tag().name("Lightning wallet").description("Local Lightning wallet status and payment operations.")
                ));
    }

    private String implementationVersion() {
        return Optional.ofNullable(OpenApiConfiguration.class.getPackage().getImplementationVersion())
                .orElse("development");
    }
}
