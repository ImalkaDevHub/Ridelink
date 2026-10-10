package com.ridelink.driver_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ServiceAreaRequest {

    @NotBlank(message = "serviceArea is required")
    @Size(max = 60, message = "serviceArea must be at most 60 characters")
    private String serviceArea;

    public String getServiceArea() {
        return serviceArea;
    }

    public void setServiceArea(String serviceArea) {
        this.serviceArea = serviceArea;
    }
}
