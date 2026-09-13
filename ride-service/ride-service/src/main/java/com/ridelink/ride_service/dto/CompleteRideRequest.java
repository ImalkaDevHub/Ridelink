package com.ridelink.ride_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// Trip details supplied by the caller when completing a ride, forwarded to
// fare-payment-service to calculate the final fare. Ride Service doesn't
// track distance/duration itself (no GPS tracking in this MVP), so the
// caller provides them here rather than the Ride entity carrying fields
// that would sit unused until this point.
public class CompleteRideRequest {

    @NotNull(message = "distanceKm is required")
    @Positive(message = "distanceKm must be positive")
    private Double distanceKm;

    @NotNull(message = "durationMin is required")
    @Positive(message = "durationMin must be positive")
    private Double durationMin;

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public Double getDurationMin() {
        return durationMin;
    }

    public void setDurationMin(Double durationMin) {
        this.durationMin = durationMin;
    }
}
