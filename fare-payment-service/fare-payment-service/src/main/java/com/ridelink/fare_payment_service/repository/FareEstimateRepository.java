package com.ridelink.fare_payment_service.repository;

import com.ridelink.fare_payment_service.model.FareEstimate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FareEstimateRepository extends JpaRepository<FareEstimate, Long> {
}
