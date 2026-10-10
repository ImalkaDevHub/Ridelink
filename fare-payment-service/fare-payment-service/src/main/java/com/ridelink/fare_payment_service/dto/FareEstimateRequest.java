package com.ridelink.fare_payment_service.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

// distanceKm and durationMin used to be required. They are optional now:
// without distanceKm the distance comes from the coordinates or the place
// names (see util.DistanceResolver); without durationMin it is estimated
// from fare.average-speed-kmh. Requests that send both still work as before.
public class FareEstimateRequest {

    @NotBlank(message = "Pickup location is required")
    private String pickupLocation;

    @NotBlank(message = "Destination location is required")
    private String destinationLocation;

    @Positive(message = "distanceKm must be positive")
    private Double distanceKm;

    @PositiveOrZero(message = "durationMin must not be negative")
    private Double durationMin;

    @NotBlank(message = "Vehicle type is required")
    private String vehicleType;

    @DecimalMin(value = "-90.0", message = "pickupLat must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "pickupLat must be between -90 and 90")
    private Double pickupLat;

    @DecimalMin(value = "-180.0", message = "pickupLng must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "pickupLng must be between -180 and 180")
    private Double pickupLng;

    @DecimalMin(value = "-90.0", message = "destinationLat must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "destinationLat must be between -90 and 90")
    private Double destinationLat;

    @DecimalMin(value = "-180.0", message = "destinationLng must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "destinationLng must be between -180 and 180")
    private Double destinationLng;

    public String getPickupLocation() {
        return pickupLocation;
    }

    public void setPickupLocation(String pickupLocation) {
        this.pickupLocation = pickupLocation;
    }

    public String getDestinationLocation() {
        return destinationLocation;
    }

    public void setDestinationLocation(String destinationLocation) {
        this.destinationLocation = destinationLocation;
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

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public Double getPickupLat() {
        return pickupLat;
    }

    public void setPickupLat(Double pickupLat) {
        this.pickupLat = pickupLat;
    }

    public Double getPickupLng() {
        return pickupLng;
    }

    public void setPickupLng(Double pickupLng) {
        this.pickupLng = pickupLng;
    }

    public Double getDestinationLat() {
        return destinationLat;
    }

    public void setDestinationLat(Double destinationLat) {
        this.destinationLat = destinationLat;
    }

    public Double getDestinationLng() {
        return destinationLng;
    }

    public void setDestinationLng(Double destinationLng) {
        this.destinationLng = destinationLng;
    }
}
