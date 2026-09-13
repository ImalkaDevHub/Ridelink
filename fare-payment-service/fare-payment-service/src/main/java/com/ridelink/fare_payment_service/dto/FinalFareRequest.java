package com.ridelink.fare_payment_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// Body of the interservice call Ride Management makes on ride completion.
public class FinalFareRequest {

    @NotBlank(message = "rideId is required")
    private String rideId;

    @NotBlank(message = "passengerId is required")
    private String passengerId;

    @NotNull(message = "distanceKm is required")
    @Positive(message = "distanceKm must be positive")
    private Double distanceKm;

    @NotNull(message = "durationMin is required")
    @Positive(message = "durationMin must be positive")
    private Double durationMin;

    public String getRideId() {
        return rideId;
    }

    public void setRideId(String rideId) {
        this.rideId = rideId;
    }

    public String getPassengerId() {
        return passengerId;
    }

    public void setPassengerId(String passengerId) {
        this.passengerId = passengerId;
    }

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
