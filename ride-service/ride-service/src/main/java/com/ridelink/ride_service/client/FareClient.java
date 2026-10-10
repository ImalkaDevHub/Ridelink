package com.ridelink.ride_service.client;

import com.ridelink.ride_service.dto.FareEstimateRequest;
import com.ridelink.ride_service.dto.FareEstimateResult;
import com.ridelink.ride_service.dto.FareRecordResult;
import com.ridelink.ride_service.dto.FinalizeFareRequest;
import com.ridelink.ride_service.dto.FinalizePaymentRequest;
import com.ridelink.ride_service.dto.PaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Component
public class FareClient {

    private static final Logger log = LoggerFactory.getLogger(FareClient.class);

    @Autowired
    private RestTemplate restTemplate;

    @Value("${fare.service.base-url}")
    private String fareServiceBaseUrl;

    // POST /api/fares/estimate, forwarding the caller's token (the endpoint
    // is public today, but the token is sent so tightening it later needs
    // no change here). Best effort: returns null if Fare Service is down or
    // can't price the trip, so a ride can still be created without one.
    public BigDecimal estimateFare(FareEstimateRequest body, String authorizationHeader) {
        try {
            FareEstimateResult result = restTemplate.exchange(
                    fareServiceBaseUrl + "/api/fares/estimate",
                    HttpMethod.POST,
                    new HttpEntity<>(body, jsonHeaders(authorizationHeader)),
                    FareEstimateResult.class
            ).getBody();
            return result != null ? result.estimatedFare() : null;
        } catch (RestClientException e) {
            log.warn("Fare estimate unavailable, creating ride without one: {}", e.getMessage());
            return null;
        }
    }

    // POST /api/fares/final, forwarding the caller's (driver's) token.
    // Idempotent on the Fare side, so retrying a failed completion is safe.
    public BigDecimal finalizeFare(FinalizeFareRequest body, String authorizationHeader) {
        try {
            FareRecordResult result = restTemplate.exchange(
                    fareServiceBaseUrl + "/api/fares/final",
                    HttpMethod.POST,
                    new HttpEntity<>(body, jsonHeaders(authorizationHeader)),
                    FareRecordResult.class
            ).getBody();
            if (result == null || result.totalFare() == null) {
                throw new FareServiceUnavailableException("Fare & payment service returned no final fare");
            }
            return result.totalFare();
        } catch (HttpClientErrorException e) {
            throw new FareRejectedException("Fare & payment service rejected the final fare request ("
                    + e.getStatusCode().value() + ").");
        } catch (RestClientException e) {
            throw new FareServiceUnavailableException("Fare & payment service is unavailable");
        }
    }

    // Legacy POST /api/payments/finalize: records a simulated, already
    // collected payment. Forwards the original caller's "Bearer <token>"
    // value as-is, since fare-payment-service requires authentication on
    // this endpoint.
    public PaymentResponse finalizePayment(String rideId, String passengerId, double distanceKm, double durationMin, String vehicleType,
                                            String authorizationHeader) {
        FinalizePaymentRequest body = new FinalizePaymentRequest(rideId, passengerId, distanceKm, durationMin, vehicleType);

        try {
            return restTemplate.exchange(
                    fareServiceBaseUrl + "/api/payments/finalize",
                    HttpMethod.POST,
                    new HttpEntity<>(body, jsonHeaders(authorizationHeader)),
                    PaymentResponse.class
            ).getBody();
        } catch (HttpClientErrorException.Conflict e) {
            throw new DuplicatePaymentException("A payment already exists for ride: " + rideId);
        } catch (RestClientException e) {
            throw new PaymentServiceUnavailableException("Fare & payment service is unavailable");
        }
    }

    private HttpHeaders jsonHeaders(String authorizationHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (authorizationHeader != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }
        return headers;
    }

    public static class FareServiceUnavailableException extends RuntimeException {
        public FareServiceUnavailableException(String message) {
            super(message);
        }
    }

    public static class FareRejectedException extends RuntimeException {
        public FareRejectedException(String message) {
            super(message);
        }
    }

    public static class DuplicatePaymentException extends RuntimeException {
        public DuplicatePaymentException(String message) {
            super(message);
        }
    }

    public static class PaymentServiceUnavailableException extends RuntimeException {
        public PaymentServiceUnavailableException(String message) {
            super(message);
        }
    }
}
