package com.ridelink.fare_payment_service.repository;

import com.ridelink.fare_payment_service.model.FareRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface FareRecordRepository extends MongoRepository<FareRecord, String> {

    Optional<FareRecord> findByRideId(String rideId);
}
