package com.ridelink.driver_service.dto;

import com.ridelink.driver_service.model.Driver;
import com.ridelink.driver_service.model.VehicleType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Partial profile update for PATCH /api/drivers/{id}: every field is
// optional and only non-null fields are applied. Availability, location
// and service area have their own endpoints.
public class DriverUpdateRequest {

    @Size(min = 2, max = 60, message = "name must be 2-60 characters")
    private String name;

    @Email(message = "email must be valid")
    private String email;

    @Size(min = 2, max = 15, message = "vehicleNumber must be 2-15 characters")
    @Pattern(regexp = Driver.VEHICLE_NUMBER_PATTERN, message = Driver.VEHICLE_NUMBER_MESSAGE)
    private String vehicleNumber;

    @Pattern(regexp = VehicleType.PATTERN, message = VehicleType.MESSAGE)
    private String vehicleType;

    @Size(max = 40, message = "vehicleModel must be at most 40 characters")
    private String vehicleModel;

    @Size(max = 30, message = "vehicleColor must be at most 30 characters")
    private String vehicleColor;

    @Min(value = 1, message = "seats must be at least 1")
    @Max(value = 20, message = "seats must be at most 20")
    private Integer seats;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public String getVehicleModel() {
        return vehicleModel;
    }

    public void setVehicleModel(String vehicleModel) {
        this.vehicleModel = vehicleModel;
    }

    public String getVehicleColor() {
        return vehicleColor;
    }

    public void setVehicleColor(String vehicleColor) {
        this.vehicleColor = vehicleColor;
    }

    public Integer getSeats() {
        return seats;
    }

    public void setSeats(Integer seats) {
        this.seats = seats;
    }
}
