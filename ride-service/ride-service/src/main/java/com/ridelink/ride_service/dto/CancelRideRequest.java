package com.ridelink.ride_service.dto;

import jakarta.validation.constraints.Size;

// Body of PATCH /api/rides/{id}/cancel. The body and reason are optional.
public class CancelRideRequest {

    @Size(max = 200, message = "reason must be at most 200 characters")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
