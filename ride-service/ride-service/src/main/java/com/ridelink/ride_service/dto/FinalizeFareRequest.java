package com.ridelink.ride_service.dto;

// Body sent to fare-payment-service's POST /api/fares/final.
public record FinalizeFareRequest(
        String rideId,
        Double distanceKm,
        Double durationMin,
        String vehicleType,
        String passengerId) {
}
