package com.ridelink.ride_service.dto;

// Body sent to fare-payment-service's POST /api/payments/finalize.
public class FinalizePaymentRequest {

    private String rideId;
    private String passengerId;
    private Double distanceKm;
    private Double durationMin;

    public FinalizePaymentRequest(String rideId, String passengerId, Double distanceKm, Double durationMin) {
        this.rideId = rideId;
        this.passengerId = passengerId;
        this.distanceKm = distanceKm;
        this.durationMin = durationMin;
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
