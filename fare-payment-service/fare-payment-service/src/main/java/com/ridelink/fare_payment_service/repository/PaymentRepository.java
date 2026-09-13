package com.ridelink.fare_payment_service.repository;

import com.ridelink.fare_payment_service.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByRideId(String rideId);
}
