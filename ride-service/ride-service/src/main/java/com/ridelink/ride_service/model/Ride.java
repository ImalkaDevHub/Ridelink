package com.ridelink.ride_service.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

// Bound directly from the request body on creation (see RideController) -
// this service has no separate DTO layer for that endpoint, so the
// validation constraints that would normally live on a request DTO live
// here instead. Only client-supplied fields are constrained - passengerId,
// driverId and status are set by the server after binding, never by the
// client, so they carry no @NotBlank/@NotNull of their own.
@Entity
public class Ride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    private Long driverId;
    private String status; // REQUESTED, ASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}