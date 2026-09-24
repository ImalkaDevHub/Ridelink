package com.ridelink.fare_payment_service.repository;

import com.ridelink.fare_payment_service.model.Payment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PaymentRepository extends MongoRepository<Payment, String> {

    Optional<Payment> findByRideId(String rideId);
}
