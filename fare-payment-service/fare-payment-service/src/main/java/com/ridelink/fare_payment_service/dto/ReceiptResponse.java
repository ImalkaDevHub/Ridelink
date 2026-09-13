package com.ridelink.fare_payment_service.dto;

import java.time.Instant;

public class ReceiptResponse {

    private String rideId;
    private String passengerId;
    private double finalFare;
    private double distanceKm;
    private double durationMin;
    private String paymentMethod;
    private String status;
    private Instant completedAt;
    private String breakdown;

    public ReceiptResponse(String rideId, String passengerId, double finalFare, double distanceKm,
                            double durationMin, String paymentMethod, String status, Instant completedAt,
                            String breakdown) {
        this.rideId = rideId;
        this.passengerId = passengerId;
        this.finalFare = finalFare;
        this.distanceKm = distanceKm;
        this.durationMin = durationMin;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.completedAt = completedAt;
        this.breakdown = breakdown;
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

    public double getFinalFare() {
        return finalFare;
    }

    public void setFinalFare(double finalFare) {
        this.finalFare = finalFare;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public double getDurationMin() {
        return durationMin;
    }

    public void setDurationMin(double durationMin) {
        this.durationMin = durationMin;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getBreakdown() {
        return breakdown;
    }

    public void setBreakdown(String breakdown) {
        this.breakdown = breakdown;
    }
}
