package com.ridelink.ride_service.dto;

// Body sent to fare-payment-service's POST /api/fares/estimate. Every
// numeric field is optional there: without distanceKm the Fare Service
// works the distance out from the coordinates or the place names.
public record FareEstimateRequest(
        String pickupLocation,
        String destinationLocation,
        Double distanceKm,
        Double durationMin,
        String vehicleType,
        Double pickupLat,
        Double pickupLng,
        Double destinationLat,
        Double destinationLng) {
}
