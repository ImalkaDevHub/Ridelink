package com.ridelink.ride_service.client;

import com.ridelink.ride_service.dto.DriverResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Component
public class DriverClient {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${driver.service.base-url}")
    private String driverServiceBaseUrl;

    public DriverResponse findAndReserveAvailableDriver() {
        try {
            DriverResponse[] drivers = restTemplate.getForObject(
                    driverServiceBaseUrl + "/api/drivers/available",
                    DriverResponse[].class
            );

            List<DriverResponse> available = Arrays.asList(drivers != null ? drivers : new DriverResponse[0]);
            if (available.isEmpty()) {
                return null; // no driver available
            }

            DriverResponse chosen = available.get(0);

            restTemplate.patchForObject(
                    driverServiceBaseUrl + "/api/drivers/" + chosen.getId() + "/availability?available=false",
                    null,
                    Void.class
            );

            return chosen;
        } catch (RestClientException e) {
            throw new DriverServiceUnavailableException("Driver service is unavailable");
        }
    }

    public static class DriverServiceUnavailableException extends RuntimeException {
        public DriverServiceUnavailableException(String message) {
            super(message);
        }
    }
}