package com.ridelink.fare_payment_service.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

// The final fare for a completed ride, with the full breakdown. Exactly one
// per ride (unique index on rideId); payments always charge totalFare from
// here, never an amount supplied by the client.
@Document(collection = "fare_records")
public class FareRecord {

    @Id
    private String id;

    @Indexed(unique = true)
    private String rideId;

    // Known when the legacy finalize call (or an optional field on
    // POST /api/fares/final) supplies it; used to restrict who may pay.
    private String passengerId;

    private String vehicleType;
    private BigDecimal distanceKm;
    private BigDecimal durationMin;

    private BigDecimal baseFare;
    private BigDecimal perKmRate;
    private BigDecimal perMinRate;
    private BigDecimal distanceCharge;
    private BigDecimal timeCharge;
    private BigDecimal subtotal;
    private BigDecimal minimumFare;
    private boolean minimumApplied;
    private BigDecimal multiplier;
    private BigDecimal totalFare;
    private String currency;
    private String breakdown;

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

    public String getPassengerId() {
        return passengerId;
    }

    public void setPassengerId(String passengerId) {
        this.passengerId = passengerId;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
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

    public BigDecimal getBaseFare() {
        return baseFare;
    }

    public void setBaseFare(BigDecimal baseFare) {
        this.baseFare = baseFare;
    }

    public BigDecimal getPerKmRate() {
        return perKmRate;
    }

    public void setPerKmRate(BigDecimal perKmRate) {
        this.perKmRate = perKmRate;
    }

    public BigDecimal getPerMinRate() {
        return perMinRate;
    }

    public void setPerMinRate(BigDecimal perMinRate) {
        this.perMinRate = perMinRate;
    }

    public BigDecimal getDistanceCharge() {
        return distanceCharge;
    }

    public void setDistanceCharge(BigDecimal distanceCharge) {
        this.distanceCharge = distanceCharge;
    }

    public BigDecimal getTimeCharge() {
        return timeCharge;
    }

    public void setTimeCharge(BigDecimal timeCharge) {
        this.timeCharge = timeCharge;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getMinimumFare() {
        return minimumFare;
    }

    public void setMinimumFare(BigDecimal minimumFare) {
        this.minimumFare = minimumFare;
    }

    public boolean isMinimumApplied() {
        return minimumApplied;
    }

    public void setMinimumApplied(boolean minimumApplied) {
        this.minimumApplied = minimumApplied;
    }

    public BigDecimal getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(BigDecimal multiplier) {
        this.multiplier = multiplier;
    }

    public BigDecimal getTotalFare() {
        return totalFare;
    }

    public void setTotalFare(BigDecimal totalFare) {
        this.totalFare = totalFare;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getBreakdown() {
        return breakdown;
    }

    public void setBreakdown(String breakdown) {
        this.breakdown = breakdown;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
