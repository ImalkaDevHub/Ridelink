package com.ridelink.driver_service.repository;

import com.ridelink.driver_service.model.Driver;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DriverRepository extends MongoRepository<Driver, String> {
    List<Driver> findByAvailableTrue();
    java.util.Optional<Driver> findByAccountId(String accountId);
}