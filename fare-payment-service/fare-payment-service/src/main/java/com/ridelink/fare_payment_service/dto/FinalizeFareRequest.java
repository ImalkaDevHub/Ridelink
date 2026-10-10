package com.ridelink.fare_payment_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

// Body of POST /api/fares/final, sent when a ride is completed.
public class FinalizeFareRequest {

    @NotBlank(message = "rideId is required")
    private String rideId;

    @NotNull(message = "distanceKm is required")
    @Positive(message = "distanceKm must be positive")
    private BigDecimal distanceKm;

    @NotNull(message = "durationMin is required")
    @PositiveOrZero(message = "durationMin must not be negative")
    private BigDecimal durationMin;

    @NotBlank(message = "vehicleType is required")
    private String vehicleType;

    // Optional. When set, only this account (or an ADMIN) may pay the fare.
    private String passengerId;

    public String getRideId() {
        return rideId;
    }

    public void setRideId(String rideId) {
        this.rideId = rideId;
    }

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(BigDecimal distanceKm) {
        this.distanceKm = distanceKm;
    }

    public BigDecimal getDurationMin() {
        return durationMin;
    }

    public void setDurationMin(BigDecimal durationMin) {
        this.durationMin = durationMin;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getPassengerId() {
        return passengerId;
    }

    public void setPassengerId(String passengerId) {
        this.passengerId = passengerId;
    }
}
