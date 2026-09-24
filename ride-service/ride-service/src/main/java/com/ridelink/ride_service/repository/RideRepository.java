package com.ridelink.ride_service.repository;

import com.ridelink.ride_service.model.Ride;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RideRepository extends MongoRepository<Ride, String> {
    java.util.List<Ride> findByPassengerIdOrderByIdDesc(String passengerId);
    java.util.List<Ride> findByDriverIdOrderByIdDesc(Long driverId);
}