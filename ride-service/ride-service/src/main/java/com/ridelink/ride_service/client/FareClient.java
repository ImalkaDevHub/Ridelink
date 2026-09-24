package com.ridelink.ride_service.client;

import com.ridelink.ride_service.dto.FinalizePaymentRequest;
import com.ridelink.ride_service.dto.PaymentResponse;
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

@Component
public class FareClient {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${fare.service.base-url}")
    private String fareServiceBaseUrl;

    // Forwards the original caller's "Bearer <token>" value as-is, since
    // fare-payment-service requires authentication on this endpoint.
    public PaymentResponse finalizePayment(String rideId, String passengerId, double distanceKm, double durationMin,
                                            String authorizationHeader) {
        FinalizePaymentRequest body = new FinalizePaymentRequest(rideId, passengerId, distanceKm, durationMin);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (authorizationHeader != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }

        try {
            return restTemplate.exchange(
                    fareServiceBaseUrl + "/api/payments/finalize",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    PaymentResponse.class
            ).getBody();
        } catch (HttpClientErrorException.Conflict e) {
            throw new DuplicatePaymentException("A payment already exists for ride: " + rideId);
        } catch (RestClientException e) {
            throw new PaymentServiceUnavailableException("Fare & payment service is unavailable");
        }
    }

    public PaymentResponse getPaymentStatus(String rideId, String authorizationHeader) {
        HttpHeaders headers = new HttpHeaders();
        if (authorizationHeader != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }
        try {
            return restTemplate.exchange(
                    fareServiceBaseUrl + "/api/payments/" + rideId + "/status",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    PaymentResponse.class
            ).getBody();
        } catch (HttpClientErrorException.NotFound e) {
            return null; // Payment does not exist
        } catch (RestClientException e) {
            throw new PaymentServiceUnavailableException("Fare & payment service is unavailable");
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
