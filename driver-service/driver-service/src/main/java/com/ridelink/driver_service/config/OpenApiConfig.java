package com.ridelink.driver_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Powers the Swagger UI page springdoc-openapi exposes for this service.
// Registers one bearer-JWT scheme and applies it to every endpoint - all
// /api/drivers/** endpoints require a token.
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI driverServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("RideLink - Driver Service API")
                        .description("Driver profile records, availability lookup for ride assignment, "
                                + "and availability toggling.")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
