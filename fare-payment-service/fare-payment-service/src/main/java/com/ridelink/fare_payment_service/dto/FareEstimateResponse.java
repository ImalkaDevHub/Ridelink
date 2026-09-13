package com.ridelink.fare_payment_service.dto;

public class FareEstimateResponse {

    private String pickupLocation;
    private String destinationLocation;
    private double distanceKm;
    private double durationMin;
    private double baseFare;
    private double perKmRate;
    private double perMinRate;
    private double estimatedFare;

    public FareEstimateResponse(String pickupLocation, String destinationLocation, double distanceKm,
                                 double durationMin, double baseFare, double perKmRate, double perMinRate,
                                 double estimatedFare) {
        this.pickupLocation = pickupLocation;
        this.destinationLocation = destinationLocation;
        this.distanceKm = distanceKm;
        this.durationMin = durationMin;
        this.baseFare = baseFare;
        this.perKmRate = perKmRate;
        this.perMinRate = perMinRate;
        this.estimatedFare = estimatedFare;
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

    public double getBaseFare() {
        return baseFare;
    }

    public void setBaseFare(double baseFare) {
        this.baseFare = baseFare;
    }

    public double getPerKmRate() {
        return perKmRate;
    }

    public void setPerKmRate(double perKmRate) {
        this.perKmRate = perKmRate;
    }

    public double getPerMinRate() {
        return perMinRate;
    }

    public void setPerMinRate(double perMinRate) {
        this.perMinRate = perMinRate;
    }

    public double getEstimatedFare() {
        return estimatedFare;
    }

    public void setEstimatedFare(double estimatedFare) {
        this.estimatedFare = estimatedFare;
    }
}
