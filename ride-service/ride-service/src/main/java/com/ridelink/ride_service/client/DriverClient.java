package com.ridelink.ride_service.client;

import com.ridelink.ride_service.dto.DriverResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class DriverClient {

    private static final Logger log = LoggerFactory.getLogger(DriverClient.class);

    @Autowired
    private RestTemplate restTemplate;

    @Value("${driver.service.base-url}")
    private String driverServiceBaseUrl;

    // Reserves an available driver with driver-service's atomic
    // POST /api/drivers/reserve (a single findAndModify), so two concurrent
    // ride requests can never get the same driver. Forwards the caller's
    // token, which that endpoint requires. Returns null when no driver is
    // available.
    public DriverResponse reserveDriver(String vehicleType, String authorizationHeader) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(driverServiceBaseUrl)
                .path("/api/drivers/reserve");
        if (vehicleType != null && !vehicleType.isBlank()) {
            uri.queryParam("vehicleType", vehicleType.trim());
        }

        try {
            return restTemplate.exchange(uri.encode().build().toUri(), HttpMethod.POST,
                    new HttpEntity<>(headers(authorizationHeader)), DriverResponse.class).getBody();
        } catch (HttpClientErrorException.Conflict e) {
            return null; // 409 NO_DRIVER_AVAILABLE
        } catch (HttpClientErrorException.BadRequest e) {
            throw new InvalidVehicleTypeException("vehicleType '" + vehicleType + "' is not supported by Driver Service.");
        } catch (RestClientException e) {
            throw new DriverServiceUnavailableException("Driver service is unavailable");
        }
    }

    // Marks the driver available again (ride completed or cancelled). Best
    // effort: the ride outcome must not depend on it, so failures are only
    // logged. Returns whether the release succeeded.
    public boolean releaseDriver(String driverId, String authorizationHeader) {
        try {
            restTemplate.exchange(driverServiceBaseUrl + "/api/drivers/{id}/release", HttpMethod.POST,
                    new HttpEntity<>(headers(authorizationHeader)), Void.class, driverId);
            return true;
        } catch (RestClientException e) {
            log.warn("Could not release driver {} - Driver Service call failed: {}", driverId, e.getMessage());
            return false;
        }
    }

    private HttpHeaders headers(String authorizationHeader) {
        HttpHeaders headers = new HttpHeaders();
        if (authorizationHeader != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }
        return headers;
    }

    public static class DriverServiceUnavailableException extends RuntimeException {
        public DriverServiceUnavailableException(String message) {
            super(message);
        }
    }

    public static class InvalidVehicleTypeException extends RuntimeException {
        public InvalidVehicleTypeException(String message) {
            super(message);
        }
    }
}
