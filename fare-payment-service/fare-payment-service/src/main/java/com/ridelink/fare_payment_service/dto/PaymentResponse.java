package com.ridelink.fare_payment_service.dto;

import java.time.Instant;

public class PaymentResponse {

    private Long id;
    private String rideId;
    private double finalFare;
    private String status;
    private String paymentMethod;
    private Instant createdAt;
    private Instant completedAt;

    public PaymentResponse(Long id, String rideId, double finalFare, String status, String paymentMethod,
                            Instant createdAt, Instant completedAt) {
        this.id = id;
        this.rideId = rideId;
        this.finalFare = finalFare;
        this.status = status;
        this.paymentMethod = paymentMethod;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRideId() {
        return rideId;
    }

    public void setRideId(String rideId) {
        this.rideId = rideId;
    }

    public double getFinalFare() {
        return finalFare;
    }

    public void setFinalFare(double finalFare) {
        this.finalFare = finalFare;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
