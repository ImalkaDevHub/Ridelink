package com.ridelink.driver_service.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// No PasswordEncoder bean here - this service never handles passwords, it
// only validates JWTs issued by Account Service.
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    // Allows the Next.js frontend (a different origin - localhost:3000 -
    // from every backend service's own port) to call this API directly
    // from the browser. Without this, the browser blocks the response
    // before JavaScript ever sees it, even though curl/Postman work fine.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Without this, an uncaught exception (malformed JSON, a 404 for
                        // an unmapped path, ...) triggers Spring Boot's internal forward
                        // to /error, which /this/ security chain would otherwise treat as
                        // just another unauthenticated request and reject with 401 -
                        // masking the real status code and error body entirely.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // ride-service's DriverClient calls this with no Authorization
                        // header today to find a driver to assign - kept open so that
                        // existing interservice call keeps working. Follow-up: give
                        // ride-service a service-level credential instead of leaving
                        // this permanently anonymous.
                        .requestMatchers(HttpMethod.GET, "/api/drivers/available").permitAll()
                        // Dual-purpose endpoint: ride-service's DriverClient also PATCHes
                        // this with no token to reserve a driver on ride assignment, while
                        // a driver toggling their own availability should require the
                        // DRIVER role. Left open at the URL level so the anonymous
                        // interservice call keeps working; the role check is enforced by
                        // @PreAuthorize("isAnonymous() or hasAuthority('DRIVER')") on
                        // DriverController.setAvailability instead, which still rejects a
                        // PASSENGER-role token.
                        .requestMatchers(HttpMethod.PATCH, "/api/drivers/*/availability").permitAll()
                        // Swagger UI and its raw OpenAPI document - the brief names
                        // Swagger UI as an official demo interface, so it can't itself
                        // require a token to load.
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJsonError(response, objectMapper, HttpStatus.UNAUTHORIZED,
                                        "UNAUTHORIZED", "A valid access token is required."))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJsonError(response, objectMapper, HttpStatus.FORBIDDEN,
                                        "FORBIDDEN", "You do not have permission to perform this action.")))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeJsonError(HttpServletResponse response, ObjectMapper objectMapper,
                                 HttpStatus status, String code, String message) throws IOException {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);

        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
