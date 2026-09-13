package com.ridelink.ride_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Powers the Swagger UI page springdoc-openapi exposes for this service.
// Registers one bearer-JWT scheme and applies it as the default for every
// endpoint - unlike the other 3 services, every endpoint here genuinely
// requires a token, so no controller method needs to override it.
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI rideServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("RideLink - Ride Service API")
                        .description("Ride requests, ride lifecycle management, and the interservice calls "
                                + "to Driver Service (assignment) and Fare & Payment Service (completion).")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
