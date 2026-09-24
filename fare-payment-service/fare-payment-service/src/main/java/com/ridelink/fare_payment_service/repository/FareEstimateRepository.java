package com.ridelink.fare_payment_service.repository;

import com.ridelink.fare_payment_service.model.FareEstimate;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FareEstimateRepository extends MongoRepository<FareEstimate, String> {
}
