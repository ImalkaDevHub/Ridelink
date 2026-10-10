package com.ridelink.ride_service.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

// Bound directly from the request body on creation (see RideController) -
// this service has no separate DTO layer for that endpoint, so the
// validation constraints that would normally live on a request DTO live
// here instead. Only client-supplied fields are constrained - passengerId,
// driverId, status, fares and timestamps are set by the server after
// binding, never by the client (RideService overwrites them).
//
// Everything added after the first release is nullable so existing ride
// documents keep loading.
@Document(collection = "rides")
public class Ride {

    @Id
    private String id;

    @NotBlank(message = "Vehicle type is required")
    private String vehicleType;

    @NotBlank(message = "passengerName is required")
    private String passengerName;
    // The authenticated requester's account id (from the JWT), captured at
    // ride-creation time. Used to identify the payer when this ride is
    // completed and forwarded to fare-payment-service's payment record.
    private String passengerId;

    @NotBlank(message = "pickup is required")
    private String pickup;

    @NotBlank(message = "destination is required")
    private String destination;

    // Optional coordinates, used for a more accurate fare estimate.
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

    // Driver-service profile id, plus the driver's account id (from the
    // reservation) so the assigned driver can be recognised from their JWT.
    // driverAccountId is null on rides created before it was recorded.
    private String driverId;
    private String driverAccountId;

    private RideStatus status;

    // Client-supplied trip details from the original API - kept as-is.
    private Double distance;
    private Integer duration;
    private Double fare;

    // Server-side figures: estimate from Fare Service at request time
    // (null if it was unavailable), final fare and trip measurements at
    // completion.
    private BigDecimal estimatedFare;
    private BigDecimal finalFare;

    @Positive(message = "distanceKm must be positive")
    private Double distanceKm;

    @Positive(message = "durationMin must be positive")
    private Double durationMin;

    private Instant requestedAt;
    private Instant acceptedAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant cancelledAt;
    private String cancelReason;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getPassengerName() {
        return passengerName;
    }

    public void setPassengerName(String passengerName) {
        this.passengerName = passengerName;
    }

    public String getPassengerId() {
        return passengerId;
    }

    public void setPassengerId(String passengerId) {
        this.passengerId = passengerId;
    }

    public String getPickup() {
        return pickup;
    }

    public void setPickup(String pickup) {
        this.pickup = pickup;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
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

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public String getDriverAccountId() {
        return driverAccountId;
    }

    public void setDriverAccountId(String driverAccountId) {
        this.driverAccountId = driverAccountId;
    }

    public RideStatus getStatus() {
        return status;
    }

    public void setStatus(RideStatus status) {
        this.status = status;
    }

    public Double getDistance() {
        return distance;
    }

    public void setDistance(Double distance) {
        this.distance = distance;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public Double getFare() {
        return fare;
    }

    public void setFare(Double fare) {
        this.fare = fare;
    }

    public BigDecimal getEstimatedFare() {
        return estimatedFare;
    }

    public void setEstimatedFare(BigDecimal estimatedFare) {
        this.estimatedFare = estimatedFare;
    }

    public BigDecimal getFinalFare() {
        return finalFare;
    }

    public void setFinalFare(BigDecimal finalFare) {
        this.finalFare = finalFare;
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

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }
}
