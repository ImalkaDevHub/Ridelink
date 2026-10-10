package com.ridelink.fare_payment_service.controller;

import com.ridelink.fare_payment_service.dto.FareEstimateResponse;
import com.ridelink.fare_payment_service.dto.PaymentResponse;
import com.ridelink.fare_payment_service.security.JwtUtil;
import com.ridelink.fare_payment_service.security.SecurityConfig;
import com.ridelink.fare_payment_service.service.FareService;
import com.ridelink.fare_payment_service.service.PaymentService;
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

import java.math.BigDecimal;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Web-layer security: the real SecurityConfig and JwtAuthFilter run against
// signed test tokens; the services are mocked.
@WebMvcTest(FareController.class)
@Import({SecurityConfig.class, JwtUtil.class})
class FareControllerSecurityTest {

    private static final String PAY_BODY = "{\"rideId\":\"ride-1\",\"method\":\"CASH\"}";
    private static final String FINAL_BODY =
            "{\"rideId\":\"ride-1\",\"distanceKm\":5,\"durationMin\":12,\"vehicleType\":\"SEDAN\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FareService fareService;

    @MockitoBean
    private PaymentService paymentService;

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

    private PaymentResponse paid() {
        return new PaymentResponse("pay-1", "ride-1", new BigDecimal("500.00"), "PAID", "CASH",
                null, null, "CASH", null, "LKR", "p-1", "p-1", "RL-1", null);
    }

    // --- 401 --------------------------------------------------------------

    @Test
    void noTokenIsRejectedWithJson401() throws Exception {
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content(PAY_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").exists());
        verifyNoInteractions(paymentService);
    }

    @Test
    void finalFareAndReceiptsRequireAToken() throws Exception {
        mockMvc.perform(post("/api/fares/final").contentType(MediaType.APPLICATION_JSON).content(FINAL_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/payments/pay-1/receipt")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/payments/pay-1")).andExpect(status().isUnauthorized());
    }

    @Test
    void estimateStaysPublicForTheExistingFrontendCall() throws Exception {
        when(fareService.estimate(any())).thenReturn(new FareEstimateResponse(
                "SLIIT", "Kaduwela", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                new BigDecimal("500.00"), "SEDAN", BigDecimal.ONE, BigDecimal.ONE, true, BigDecimal.ONE, "LKR",
                "PROVIDED", false, "..."));

        mockMvc.perform(post("/api/fares/estimate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pickupLocation\":\"SLIIT\",\"destinationLocation\":\"Kaduwela\",\"distanceKm\":1,\"vehicleType\":\"SEDAN\"}"))
                .andExpect(status().isOk());
    }

    // --- 403 --------------------------------------------------------------

    @Test
    void driverCannotPay() throws Exception {
        mockMvc.perform(post("/api/payments").header("Authorization", token("drv-1", "DRIVER"))
                        .contentType(MediaType.APPLICATION_JSON).content(PAY_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(paymentService);
    }

    @Test
    void adminCannotPay() throws Exception {
        mockMvc.perform(post("/api/payments").header("Authorization", token("admin-1", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(PAY_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void passengerCannotSetTheFinalFare() throws Exception {
        mockMvc.perform(post("/api/fares/final").header("Authorization", token("p-1", "PASSENGER"))
                        .contentType(MediaType.APPLICATION_JSON).content(FINAL_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(fareService);
    }

    @Test
    void passengerCannotUseTheLegacyFinalize() throws Exception {
        mockMvc.perform(post("/api/payments/finalize").header("Authorization", token("p-1", "PASSENGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rideId\":\"r\",\"passengerId\":\"p-1\",\"distanceKm\":1,\"durationMin\":1,\"vehicleType\":\"CAR\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void receiptForSomeoneElseIsJson403() throws Exception {
        when(paymentService.getReceipt("pay-1", "p-2", false))
                .thenThrow(new PaymentService.NotPayerException("Only the payer can view this receipt."));

        mockMvc.perform(get("/api/payments/pay-1/receipt").header("Authorization", token("p-2", "PASSENGER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // --- allowed ----------------------------------------------------------

    @Test
    void passengerPaysAndTheirIdIsPassedFromTheToken() throws Exception {
        when(paymentService.pay(any(), eq("p-1"), eq(false))).thenReturn(paid());

        mockMvc.perform(post("/api/payments").header("Authorization", token("p-1", "PASSENGER"))
                        .contentType(MediaType.APPLICATION_JSON).content(PAY_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void adminReceiptRequestIsPassedAsAdmin() throws Exception {
        mockMvc.perform(get("/api/payments/pay-1/receipt").header("Authorization", token("admin-1", "ADMIN")));

        verify(paymentService).getReceipt("pay-1", "admin-1", true);
    }

    @Test
    void healthEndpointIsNotBehindAuthentication() throws Exception {
        // Actuator isn't on the classpath yet, so 404 - the point is it's not 401.
        mockMvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
    }
}
