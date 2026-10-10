package com.ridelink.fare_payment_service.repository;

import com.ridelink.fare_payment_service.model.Payment;
import com.ridelink.fare_payment_service.model.PaymentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends MongoRepository<Payment, String> {

    // Oldest first - every attempt for the ride, paid or not.
    List<Payment> findByRideIdOrderByCreatedAtAsc(String rideId);

    Optional<Payment> findFirstByRideIdAndStatusIn(String rideId, Collection<PaymentStatus> statuses);

    boolean existsByRideIdAndStatusIn(String rideId, Collection<PaymentStatus> statuses);
}
