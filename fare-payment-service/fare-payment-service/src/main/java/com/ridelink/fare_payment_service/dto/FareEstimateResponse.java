package com.ridelink.fare_payment_service.dto;

import java.math.BigDecimal;

// The first eight fields are the original response; the rest were added
// later. Money is BigDecimal but still serialised as a JSON number.
public record FareEstimateResponse(
        String pickupLocation,
        String destinationLocation,
        BigDecimal distanceKm,
        BigDecimal durationMin,
        BigDecimal baseFare,
        BigDecimal perKmRate,
        BigDecimal perMinRate,
        BigDecimal estimatedFare,
        String vehicleType,
        BigDecimal subtotal,
        BigDecimal minimumFare,
        boolean minimumApplied,
        BigDecimal vehicleMultiplier,
        String currency,
        String distanceSource,
        boolean durationEstimated,
        String breakdown) {
}
