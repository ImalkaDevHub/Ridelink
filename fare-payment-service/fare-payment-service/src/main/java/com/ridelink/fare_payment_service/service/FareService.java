package com.ridelink.fare_payment_service.service;

import com.ridelink.fare_payment_service.config.FareProperties;
import com.ridelink.fare_payment_service.dto.FareEstimateRequest;
import com.ridelink.fare_payment_service.dto.FareEstimateResponse;
import com.ridelink.fare_payment_service.model.FareEstimate;
import com.ridelink.fare_payment_service.model.FareRecord;
import com.ridelink.fare_payment_service.repository.FareEstimateRepository;
import com.ridelink.fare_payment_service.repository.FareRecordRepository;
import com.ridelink.fare_payment_service.util.DistanceResolver;
import com.ridelink.fare_payment_service.util.FareBreakdown;
import com.ridelink.fare_payment_service.util.FareCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

// Fare estimates and final fares. Never calls Ride Service: everything it
// needs arrives in the request, which keeps the dependency one-way
// (Ride -> Fare).
@Service
public class FareService {

    private static final BigDecimal MINUTES_PER_HOUR = new BigDecimal("60");

    @Autowired
    private FareEstimateRepository fareEstimateRepository;

    @Autowired
    private FareRecordRepository fareRecordRepository;

    @Autowired
    private FareCalculator fareCalculator;

    @Autowired
    private DistanceResolver distanceResolver;

    @Autowired
    private FareProperties fareProperties;

    public FareEstimateResponse estimate(FareEstimateRequest request) {
        DistanceResolver.ResolvedDistance distance = distanceResolver.resolve(
                toBigDecimal(request.getDistanceKm()),
                request.getPickupLat(), request.getPickupLng(),
                request.getDestinationLat(), request.getDestinationLng(),
                request.getPickupLocation(), request.getDestinationLocation());

        boolean durationEstimated = request.getDurationMin() == null;
        BigDecimal durationMin = durationEstimated
                ? estimateDurationMin(distance.km())
                : toBigDecimal(request.getDurationMin());

        FareBreakdown fare = fareCalculator.calculate(distance.km(), durationMin, request.getVehicleType());

        FareEstimate estimate = new FareEstimate();
        estimate.setPickupLocation(request.getPickupLocation());
        estimate.setDestinationLocation(request.getDestinationLocation());
        estimate.setDistanceKm(distance.km());
        estimate.setDurationMin(durationMin);
        estimate.setEstimatedFare(fare.total());
        estimate.setVehicleType(fare.vehicleType());
        estimate.setCurrency(fare.currency());
        estimate.setDistanceSource(distance.source().name());
        fareEstimateRepository.save(estimate);

        return new FareEstimateResponse(
                request.getPickupLocation(),
                request.getDestinationLocation(),
                distance.km(),
                durationMin,
                fare.baseFare(),
                fare.perKmRate(),
                fare.perMinRate(),
                fare.total(),
                fare.vehicleType(),
                fare.subtotal(),
                fare.minimumFare(),
                fare.minimumApplied(),
                fare.multiplier(),
                fare.currency(),
                distance.source().name(),
                durationEstimated,
                fare.describe());
    }

    // Idempotent per rideId: the first call stores the fare, later calls
    // return that same record unchanged (even with different inputs), so a
    // retried completion can never change what the passenger is charged.
    public FinalizeResult finalizeFare(String rideId, String passengerId, BigDecimal distanceKm,
                                       BigDecimal durationMin, String vehicleType) {
        return finalizeFare(rideId, passengerId, distanceKm, durationMin, vehicleType, false);
    }

    // Same, but unknown vehicle types use multiplier 1.0 - only for the
    // legacy POST /api/payments/finalize call.
    public FinalizeResult finalizeFareLenient(String rideId, String passengerId, BigDecimal distanceKm,
                                              BigDecimal durationMin, String vehicleType) {
        return finalizeFare(rideId, passengerId, distanceKm, durationMin, vehicleType, true);
    }

    public Optional<FareRecord> findFareRecord(String rideId) {
        return fareRecordRepository.findByRideId(rideId);
    }

    private FinalizeResult finalizeFare(String rideId, String passengerId, BigDecimal distanceKm,
                                        BigDecimal durationMin, String vehicleType, boolean lenient) {
        Optional<FareRecord> existing = fareRecordRepository.findByRideId(rideId);
        if (existing.isPresent()) {
            return new FinalizeResult(existing.get(), false);
        }

        FareBreakdown fare = lenient
                ? fareCalculator.calculateLenient(distanceKm, durationMin, vehicleType)
                : fareCalculator.calculate(distanceKm, durationMin, vehicleType);

        try {
            return new FinalizeResult(fareRecordRepository.save(toRecord(rideId, passengerId, fare)), true);
        } catch (DuplicateKeyException e) {
            // Another request finalized this ride between the check and the
            // insert - the unique index on rideId kept only one; return it.
            return new FinalizeResult(fareRecordRepository.findByRideId(rideId).orElseThrow(() -> e), false);
        }
    }

    private FareRecord toRecord(String rideId, String passengerId, FareBreakdown fare) {
        FareRecord record = new FareRecord();
        record.setRideId(rideId);
        record.setPassengerId(passengerId);
        record.setVehicleType(fare.vehicleType());
        record.setDistanceKm(fare.distanceKm());
        record.setDurationMin(fare.durationMin());
        record.setBaseFare(fare.baseFare());
        record.setPerKmRate(fare.perKmRate());
        record.setPerMinRate(fare.perMinRate());
        record.setDistanceCharge(fare.distanceCharge());
        record.setTimeCharge(fare.timeCharge());
        record.setSubtotal(fare.subtotal());
        record.setMinimumFare(fare.minimumFare());
        record.setMinimumApplied(fare.minimumApplied());
        record.setMultiplier(fare.multiplier());
        record.setTotalFare(fare.total());
        record.setCurrency(fare.currency());
        record.setBreakdown(fare.describe());
        return record;
    }

    private BigDecimal estimateDurationMin(BigDecimal distanceKm) {
        return distanceKm.multiply(MINUTES_PER_HOUR)
                .divide(fareProperties.getAverageSpeedKmh(), 2, RoundingMode.HALF_UP);
    }

    // BigDecimal.valueOf keeps the decimal the client typed (5.1 stays 5.1).
    private static BigDecimal toBigDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    public record FinalizeResult(FareRecord record, boolean created) {
    }
}
