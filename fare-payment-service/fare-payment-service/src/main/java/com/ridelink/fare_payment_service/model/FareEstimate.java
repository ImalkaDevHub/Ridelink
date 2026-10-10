package com.ridelink.fare_payment_service.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

// A quote generated before (or without) a ride existing - rideId is
// nullable because an estimate can be requested pre-booking.
// vehicleType, currency and distanceSource were added later and are null
// on older documents; their doubles convert to BigDecimal on read.
@Document(collection = "fare_estimates")
public class FareEstimate {

    @Id
    private String id;

    private String rideId;

    private String pickupLocation;
    private String destinationLocation;

    private BigDecimal distanceKm;
    private BigDecimal durationMin;
    private BigDecimal estimatedFare;

    private String vehicleType;
    private String currency;
    private String distanceSource;

    private Instant createdAt = Instant.now();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRideId() {
        return rideId;
    }

    public void setRideId(String rideId) {
        this.rideId = rideId;
    }

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

    public BigDecimal getEstimatedFare() {
        return estimatedFare;
    }

    public void setEstimatedFare(BigDecimal estimatedFare) {
        this.estimatedFare = estimatedFare;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getDistanceSource() {
        return distanceSource;
    }

    public void setDistanceSource(String distanceSource) {
        this.distanceSource = distanceSource;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
