package com.ridelink.driver_service.service;

import com.ridelink.driver_service.dto.DriverUpdateRequest;
import com.ridelink.driver_service.model.Driver;
import com.ridelink.driver_service.model.VehicleType;
import com.ridelink.driver_service.repository.DriverRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class DriverService {

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    public Driver createDriver(Driver driver) {
        // Server-owned fields: never trust what the client sent.
        driver.setId(null);
        driver.setVersion(null);
        driver.setVehicleType(normalizeVehicleType(driver.getVehicleType()));
        driver.setVehicleNumber(normalizeVehicleNumber(driver.getVehicleNumber()));

        if (driver.getVehicleNumber() != null
                && driverRepository.findByVehicleNumber(driver.getVehicleNumber()).isPresent()) {
            throw new DuplicateVehicleException("A driver with vehicle number " + driver.getVehicleNumber() + " already exists.");
        }

        return saveUnique(driver);
    }

    public List<Driver> getAllDrivers() {
        return driverRepository.findAll();
    }

    public List<Driver> getAvailableDrivers() {
        return driverRepository.findByAvailableTrue();
    }

    public List<Driver> getAvailableDriversByType(String vehicleType) {
        return driverRepository.findByAvailableTrueAndVehicleTypeIgnoreCase(vehicleType);
    }

    public Driver getDriverByAccountId(String accountId) {
        return driverRepository.findByAccountId(accountId)
                .orElseThrow(() -> new DriverNotFoundException("No driver profile found for account id: " + accountId));
    }

    public Driver getDriver(String id) {
        return driverRepository.findById(id)
                .orElseThrow(() -> new DriverNotFoundException("Driver not found with id: " + id));
    }

    public Driver setAvailability(String id, boolean available) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new DriverNotFoundException("Driver not found with id: " + id));
        driver.setAvailable(available);
        return driverRepository.save(driver);
    }

    // Partial update: only non-null fields are applied.
    public Driver updateDriver(String id, DriverUpdateRequest request) {
        Driver driver = getDriver(id);

        if (request.getVehicleNumber() != null) {
            String vehicleNumber = normalizeVehicleNumber(request.getVehicleNumber());
            driverRepository.findByVehicleNumber(vehicleNumber)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new DuplicateVehicleException("A driver with vehicle number " + vehicleNumber + " already exists.");
                    });
            driver.setVehicleNumber(vehicleNumber);
        }
        if (request.getVehicleType() != null) {
            driver.setVehicleType(normalizeVehicleType(request.getVehicleType()));
        }
        if (request.getName() != null) {
            driver.setName(request.getName());
        }
        if (request.getEmail() != null) {
            driver.setEmail(request.getEmail());
        }
        if (request.getVehicleModel() != null) {
            driver.setVehicleModel(request.getVehicleModel());
        }
        if (request.getVehicleColor() != null) {
            driver.setVehicleColor(request.getVehicleColor());
        }
        if (request.getSeats() != null) {
            driver.setSeats(request.getSeats());
        }

        return saveUnique(driver);
    }

    public Driver updateLocation(String id, double lat, double lng) {
        Driver driver = getDriver(id);
        driver.setCurrentLat(lat);
        driver.setCurrentLng(lng);
        return driverRepository.save(driver);
    }

    public Driver updateServiceArea(String id, String serviceArea) {
        Driver driver = getDriver(id);
        driver.setServiceArea(serviceArea.trim());
        return driverRepository.save(driver);
    }

    // Available drivers, optionally narrowed by service area and vehicle
    // type (both case-insensitive exact matches).
    public List<Driver> findEligible(String area, String vehicleType) {
        Query query = new Query(eligibleCriteria(area, vehicleType)).with(Sort.by(Sort.Direction.ASC, "id"));
        return mongoTemplate.find(query, Driver.class);
    }

    // Race-free reservation: one findAndModify both matches an available
    // driver and flips it to unavailable, so two concurrent callers can
    // never be handed the same driver. Never read-then-write here.
    public Driver reserve(String area, String vehicleType) {
        Query query = new Query(eligibleCriteria(area, vehicleType)).with(Sort.by(Sort.Direction.ASC, "id"));
        Update update = new Update().set("available", false).inc("version", 1);

        Driver reserved = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), Driver.class);

        if (reserved == null) {
            throw new NoDriverAvailableException("No driver is available right now.");
        }
        return reserved;
    }

    // Atomic too, so a release can't overwrite a concurrent update.
    public Driver release(String id) {
        Query query = new Query(Criteria.where("id").is(id));
        Update update = new Update().set("available", true).inc("version", 1);

        Driver released = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), Driver.class);

        if (released == null) {
            throw new DriverNotFoundException("Driver not found with id: " + id);
        }
        return released;
    }

    private Criteria eligibleCriteria(String area, String vehicleType) {
        Criteria criteria = Criteria.where("available").is(true);
        if (vehicleType != null && !vehicleType.isBlank()) {
            criteria = criteria.and("vehicleType").regex(exactIgnoreCase(normalizeVehicleType(vehicleType)));
        }
        if (area != null && !area.isBlank()) {
            criteria = criteria.and("serviceArea").regex(exactIgnoreCase(area.trim()));
        }
        return criteria;
    }

    // Older documents store vehicleType/serviceArea in mixed case, so match
    // case-insensitively. Pattern.quote keeps user input literal.
    private Pattern exactIgnoreCase(String value) {
        return Pattern.compile("^" + Pattern.quote(value) + "$", Pattern.CASE_INSENSITIVE);
    }

    private String normalizeVehicleType(String vehicleType) {
        if (vehicleType == null) {
            return null;
        }
        return VehicleType.parse(vehicleType)
                .map(Enum::name)
                .orElseThrow(() -> new InvalidVehicleTypeException(VehicleType.MESSAGE));
    }

    private String normalizeVehicleNumber(String vehicleNumber) {
        return vehicleNumber == null ? null : vehicleNumber.trim().toUpperCase(Locale.ROOT);
    }

    // The pre-checks give clean errors in the normal case; the unique
    // indexes catch two concurrent requests that both pass them.
    private Driver saveUnique(Driver driver) {
        try {
            return driverRepository.save(driver);
        } catch (DuplicateKeyException e) {
            String message = String.valueOf(e.getMessage());
            if (message.contains("vehicleNumber")) {
                throw new DuplicateVehicleException("A driver with vehicle number " + driver.getVehicleNumber() + " already exists.");
            }
            throw e;
        }
    }

    public static class DriverNotFoundException extends RuntimeException {
        public DriverNotFoundException(String message) {
            super(message);
        }
    }

    public static class DuplicateVehicleException extends RuntimeException {
        public DuplicateVehicleException(String message) {
            super(message);
        }
    }

    public static class InvalidVehicleTypeException extends RuntimeException {
        public InvalidVehicleTypeException(String message) {
            super(message);
        }
    }

    public static class NoDriverAvailableException extends RuntimeException {
        public NoDriverAvailableException(String message) {
            super(message);
        }
    }
}
