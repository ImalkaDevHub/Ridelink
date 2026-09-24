package com.ridelink.driver_service.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

// Bound directly from the request body on creation (see DriverController) -
// this service has no separate DTO layer, so the validation constraints
// that would normally live on a request DTO live here instead.
@Entity
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long accountId;

    @NotBlank(message = "name is required")
    private String name;

    @NotBlank(message = "vehicleNumber is required")
    private String vehicleNumber;

    @NotBlank(message = "vehicleType is required")
    private String vehicleType;

    private boolean available = true;

    @NotBlank(message = "serviceArea is required")
    private String serviceArea;

    private Double currentLat;
    private Double currentLng;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public void setVehicleNumber(String vehicleNumber) {
        this.vehicleNumber = vehicleNumber;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getServiceArea() {
        return serviceArea;
    }

    public void setServiceArea(String serviceArea) {
        this.serviceArea = serviceArea;
    }

    public Double getCurrentLat() {
        return currentLat;
    }

    public void setCurrentLat(Double currentLat) {
        this.currentLat = currentLat;
    }

    public Double getCurrentLng() {
        return currentLng;
    }

    public void setCurrentLng(Double currentLng) {
        this.currentLng = currentLng;
    }
}