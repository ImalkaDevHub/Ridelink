package com.ridelink.ride_service.dto;

// Mirrors fare-payment-service's PaymentResponse shape - only the fields
// ride-service actually reads are needed.
public class PaymentResponse {

    private Long id;
    private String rideId;
    private double finalFare;
    private String status;

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
}
