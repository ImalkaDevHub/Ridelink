package com.ridelink.driver_service.controller;

import com.ridelink.driver_service.model.Driver;
import com.ridelink.driver_service.repository.DriverRepository;
import com.ridelink.driver_service.security.DriverAccess;
import com.ridelink.driver_service.security.JwtUtil;
import com.ridelink.driver_service.security.SecurityConfig;
import com.ridelink.driver_service.service.DriverService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Web-layer security: the real SecurityConfig and JwtAuthFilter run against
// signed test tokens; DriverService and the repository are mocked, so no
// MongoDB is needed.
@WebMvcTest(DriverController.class)
@Import({SecurityConfig.class, JwtUtil.class, DriverAccess.class})
class DriverControllerSecurityTest {

    private static final String VALID_BODY = """
            {"name":"Nimal","email":"nimal@example.com","vehicleNumber":"CAB-1024",
             "vehicleType":"SEDAN","serviceArea":"Malabe"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DriverService driverService;

    @MockitoBean
    private DriverRepository driverRepository;

    @Value("${jwt.secret}")
    private String secret;

    private String token(String accountId, String role) {
        return "Bearer " + Jwts.builder()
                .subject(accountId)
                .claim("accountId", accountId)
                .claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()), Jwts.SIG.HS256)
                .compact();
    }

    private Driver ownedBy(String accountId) {
        Driver driver = new Driver();
        driver.setId("d-1");
        driver.setAccountId(accountId);
        return driver;
    }

    // --- 401 --------------------------------------------------------------

    @Test
    void noTokenIsRejectedWithJson401() throws Exception {
        mockMvc.perform(get("/api/drivers/d-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void availableDriversNowRequireAToken() throws Exception {
        mockMvc.perform(get("/api/drivers/available")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousAvailabilityToggleIsRejected() throws Exception {
        mockMvc.perform(patch("/api/drivers/d-1/availability").param("available", "true"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgedTokenIsRejected() throws Exception {
        String forged = Jwts.builder().subject("x").claim("accountId", "x").claim("role", "ADMIN")
                .signWith(Keys.hmacShaKeyFor("a-completely-different-secret-of-enough-length!".getBytes()))
                .compact();

        mockMvc.perform(get("/api/drivers/d-1").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reserveAndReleaseRequireAToken() throws Exception {
        mockMvc.perform(post("/api/drivers/reserve")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/drivers/d-1/release")).andExpect(status().isUnauthorized());
    }

    // --- 403 --------------------------------------------------------------

    @Test
    void passengerCannotCreateADriverProfile() throws Exception {
        mockMvc.perform(post("/api/drivers").header("Authorization", token("p-1", "PASSENGER"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void driverCannotToggleSomeoneElsesAvailability() throws Exception {
        when(driverRepository.findById("d-1")).thenReturn(Optional.of(ownedBy("other-driver")));

        mockMvc.perform(patch("/api/drivers/d-1/availability").param("available", "true")
                        .header("Authorization", token("drv-1", "DRIVER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void passengerCannotUpdateLocation() throws Exception {
        mockMvc.perform(patch("/api/drivers/d-1/location").header("Authorization", token("p-1", "PASSENGER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lat\":6.9,\"lng\":79.9}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyAdminsListEveryDriver() throws Exception {
        mockMvc.perform(get("/api/drivers").header("Authorization", token("drv-1", "DRIVER")))
                .andExpect(status().isForbidden());
    }

    // --- allowed ----------------------------------------------------------

    @Test
    void driverCreatesTheirOwnProfile() throws Exception {
        when(driverService.createDriver(any())).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/drivers").header("Authorization", token("drv-1", "DRIVER"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value("drv-1")); // from the token
    }

    @Test
    void driverTogglesTheirOwnAvailability() throws Exception {
        when(driverRepository.findById("d-1")).thenReturn(Optional.of(ownedBy("drv-1")));
        when(driverService.setAvailability(eq("d-1"), anyBoolean())).thenReturn(ownedBy("drv-1"));

        mockMvc.perform(patch("/api/drivers/d-1/availability").param("available", "true")
                        .header("Authorization", token("drv-1", "DRIVER")))
                .andExpect(status().isOk());
    }

    @Test
    void adminMayToggleAnyDriversAvailability() throws Exception {
        when(driverService.setAvailability(eq("d-1"), anyBoolean())).thenReturn(ownedBy("other"));

        mockMvc.perform(patch("/api/drivers/d-1/availability").param("available", "false")
                        .header("Authorization", token("admin-1", "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void anyAuthenticatedUserCanReadADriver() throws Exception {
        when(driverService.getDriver("d-1")).thenReturn(ownedBy("drv-1"));

        mockMvc.perform(get("/api/drivers/d-1").header("Authorization", token("p-1", "PASSENGER")))
                .andExpect(status().isOk());
    }

    @Test
    void healthEndpointIsNotBehindAuthentication() throws Exception {
        // Actuator endpoints aren't part of this web slice, so 404 - the point is it's not 401.
        mockMvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
    }
}
