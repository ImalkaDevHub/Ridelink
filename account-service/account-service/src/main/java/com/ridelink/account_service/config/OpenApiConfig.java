package com.ridelink.account_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Powers the Swagger UI page springdoc-openapi exposes for this service.
// Registers one bearer-JWT scheme and applies it as the default for every
// endpoint; the two genuinely public endpoints (register, login) override
// this with @SecurityRequirements on their controller methods so Swagger UI
// doesn't show a misleading lock icon on them.
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI accountServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("RideLink - Account Service API")
                        .description("Passenger and driver account registration, login and JWT issuance, "
                                + "role and account status management, and profile viewing/updating.")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
