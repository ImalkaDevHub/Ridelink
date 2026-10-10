package com.ridelink.ride_service.controller;

import com.ridelink.ride_service.model.Ride;
import com.ridelink.ride_service.model.RideStatus;
import com.ridelink.ride_service.security.JwtUtil;
import com.ridelink.ride_service.security.SecurityConfig;
import com.ridelink.ride_service.service.RideService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Web-layer security: the real SecurityConfig and JwtAuthFilter run against
// signed test tokens; RideService is mocked (its own participant checks are
// covered by RideServiceTest).
@WebMvcTest(RideController.class)
@Import({SecurityConfig.class, JwtUtil.class})
class RideControllerSecurityTest {

    private static final String RIDE_BODY = """
            {"passengerName":"Pat","pickup":"SLIIT","destination":"Kaduwela","vehicleType":"Car"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RideService rideService;

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

    private Ride ride(RideStatus status) {
        Ride ride = new Ride();
        ride.setId("ride-1");
        ride.setStatus(status);
        return ride;
    }

    // --- 401 --------------------------------------------------------------

    @Test
    void noTokenIsRejectedWithJson401() throws Exception {
        mockMvc.perform(get("/api/rides/ride-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").exists());
        verifyNoInteractions(rideService);
    }

    @Test
    void everyRideEndpointRequiresAToken() throws Exception {
        mockMvc.perform(post("/api/rides").contentType(MediaType.APPLICATION_JSON).content(RIDE_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/rides")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/rides/ride-1/accept")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/rides/ride-1/cancel")).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String expired = Jwts.builder().subject("p-1").claim("accountId", "p-1").claim("role", "PASSENGER")
                .expiration(new Date(System.currentTimeMillis() - 1_000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()), Jwts.SIG.HS256).compact();

        mockMvc.perform(get("/api/rides/ride-1").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    // --- 403 --------------------------------------------------------------

    @Test
    void driverCannotRequestARide() throws Exception {
        mockMvc.perform(post("/api/rides").header("Authorization", token("drv-1", "DRIVER"))
                        .contentType(MediaType.APPLICATION_JSON).content(RIDE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(rideService);
    }

    @Test
    void adminCannotRequestARide() throws Exception {
        mockMvc.perform(post("/api/rides").header("Authorization", token("admin-1", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(RIDE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void passengerCannotAcceptStartOrCompleteARide() throws Exception {
        String passenger = token("p-1", "PASSENGER");
        mockMvc.perform(patch("/api/rides/ride-1/accept").header("Authorization", passenger))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/rides/ride-1/start").header("Authorization", passenger))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/rides/ride-1/complete").header("Authorization", passenger)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"distanceKm\":5,\"durationMin\":12}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(rideService);
    }

    @Test
    void adminCannotAcceptARide() throws Exception {
        mockMvc.perform(patch("/api/rides/ride-1/accept").header("Authorization", token("admin-1", "ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonParticipantGetsJson403FromTheServiceCheck() throws Exception {
        when(rideService.getRide("ride-1"))
                .thenThrow(new RideService.NotRideParticipantException("Only the ride's passenger or driver can view it."));

        mockMvc.perform(get("/api/rides/ride-1").header("Authorization", token("p-2", "PASSENGER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // --- allowed ----------------------------------------------------------

    @Test
    void passengerRequestsARide() throws Exception {
        when(rideService.requestRide(any())).thenReturn(ride(RideStatus.ASSIGNED));

        mockMvc.perform(post("/api/rides").header("Authorization", token("p-1", "PASSENGER"))
                        .contentType(MediaType.APPLICATION_JSON).content(RIDE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
    }

    @Test
    void driverAcceptsAndCompletes() throws Exception {
        when(rideService.acceptRide("ride-1")).thenReturn(ride(RideStatus.ACCEPTED));
        when(rideService.completeRide(eq("ride-1"), anyDouble(), anyDouble())).thenReturn(ride(RideStatus.COMPLETED));
        String driver = token("drv-1", "DRIVER");

        mockMvc.perform(patch("/api/rides/ride-1/accept").header("Authorization", driver))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/rides/ride-1/complete").header("Authorization", driver)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"distanceKm\":5,\"durationMin\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void healthEndpointIsNotBehindAuthentication() throws Exception {
        // Actuator isn't on the classpath yet, so 404 - the point is it's not 401.
        mockMvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
    }
}
