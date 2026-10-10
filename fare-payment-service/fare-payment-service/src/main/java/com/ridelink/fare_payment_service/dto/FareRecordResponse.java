package com.ridelink.fare_payment_service.dto;

import com.ridelink.fare_payment_service.model.FareRecord;

import java.math.BigDecimal;
import java.time.Instant;

public record FareRecordResponse(
        String id,
        String rideId,
        String passengerId,
        String vehicleType,
        BigDecimal distanceKm,
        BigDecimal durationMin,
        BigDecimal baseFare,
        BigDecimal perKmRate,
        BigDecimal perMinRate,
        BigDecimal distanceCharge,
        BigDecimal timeCharge,
        BigDecimal subtotal,
        BigDecimal minimumFare,
        boolean minimumApplied,
        BigDecimal multiplier,
        BigDecimal totalFare,
        String currency,
        String breakdown,
        Instant createdAt) {

    public static FareRecordResponse from(FareRecord record) {
        return new FareRecordResponse(
                record.getId(), record.getRideId(), record.getPassengerId(), record.getVehicleType(),
                record.getDistanceKm(), record.getDurationMin(), record.getBaseFare(), record.getPerKmRate(),
                record.getPerMinRate(), record.getDistanceCharge(), record.getTimeCharge(), record.getSubtotal(),
                record.getMinimumFare(), record.isMinimumApplied(), record.getMultiplier(), record.getTotalFare(),
                record.getCurrency(), record.getBreakdown(), record.getCreatedAt());
    }
}
