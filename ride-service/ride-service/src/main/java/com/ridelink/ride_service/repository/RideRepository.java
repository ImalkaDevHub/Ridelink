package com.ridelink.ride_service.repository;

import com.ridelink.ride_service.model.Ride;
import com.ridelink.ride_service.model.RideStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RideRepository extends MongoRepository<Ride, String> {
    java.util.List<Ride> findByPassengerIdOrderByIdDesc(String passengerId);
    java.util.List<Ride> findByDriverIdOrderByIdDesc(String driverId);
    java.util.List<Ride> findByDriverAccountIdOrderByIdDesc(String driverAccountId);
    java.util.List<Ride> findAllByOrderByIdDesc();
    java.util.List<Ride> findByStatusOrderByIdDesc(RideStatus status);
}
