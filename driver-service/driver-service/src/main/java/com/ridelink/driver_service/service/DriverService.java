package com.ridelink.driver_service.service;

import com.ridelink.driver_service.model.Driver;
import com.ridelink.driver_service.repository.DriverRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DriverService {

    @Autowired
    private DriverRepository driverRepository;

    public Driver createDriver(Driver driver) {
        return driverRepository.save(driver);
    }

    public List<Driver> getAvailableDrivers() {
        return driverRepository.findByAvailableTrue();
    }

    public Driver getDriverByAccountId(String accountId) {
        return driverRepository.findByAccountId(accountId)
                .orElseThrow(() -> new DriverNotFoundException("No driver profile found for account id: " + accountId));
    }

    public Driver setAvailability(String id, boolean available) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new DriverNotFoundException("Driver not found with id: " + id));
        driver.setAvailable(available);
        return driverRepository.save(driver);
    }

    public static class DriverNotFoundException extends RuntimeException {
        public DriverNotFoundException(String message) {
            super(message);
        }
    }
}