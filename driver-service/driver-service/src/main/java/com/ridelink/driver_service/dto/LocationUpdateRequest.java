package com.ridelink.driver_service.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public class LocationUpdateRequest {

    @NotNull(message = "lat is required")
    @DecimalMin(value = "-90.0", message = "lat must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "lat must be between -90 and 90")
    private Double lat;

    @NotNull(message = "lng is required")
    @DecimalMin(value = "-180.0", message = "lng must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "lng must be between -180 and 180")
    private Double lng;

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLng() {
        return lng;
    }

    public void setLng(Double lng) {
        this.lng = lng;
    }
}
