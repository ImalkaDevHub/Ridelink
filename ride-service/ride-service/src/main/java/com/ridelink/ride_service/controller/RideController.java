package com.ridelink.ride_service.controller;

import com.ridelink.ride_service.client.DriverClient;
import com.ridelink.ride_service.dto.CompleteRideRequest;
import com.ridelink.ride_service.model.Ride;
import com.ridelink.ride_service.service.RideService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/rides")
public class RideController {

    @Autowired
    private RideService rideService;

    @PostMapping
    public ResponseEntity<?> requestRide(@RequestBody Ride ride) {
        try {
            Ride created = rideService.requestRide(ride);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (RideService.NoDriverAvailableException e) {
            return errorResponse(HttpStatus.CONFLICT, "NO_DRIVER_AVAILABLE", e.getMessage());
        } catch (DriverClient.DriverServiceUnavailableException e) {
            return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, "DRIVER_SERVICE_UNAVAILABLE",
                    "A driver cannot be assigned now. Please retry.");
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getRide(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(rideService.getRide(id));
        } catch (RuntimeException e) {
            return errorResponse(HttpStatus.NOT_FOUND, "RIDE_NOT_FOUND", e.getMessage());
        }
    }

    // Only a driver (or an admin) may complete a ride. This is a role-level
    // check, not a per-resource ownership check - the JWT carries no
    // driverId claim, and there is no linkage today between an
    // account-service account and a driver-service Driver record, so
    // "only the driver actually assigned to this ride" can't be verified
    // yet. Documented limitation / follow-up for the report.
    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('DRIVER') or hasAuthority('ADMIN')")
    public ResponseEntity<?> completeRide(@PathVariable Long id, @Valid @RequestBody CompleteRideRequest request) {
        try {
            Ride completed = rideService.completeRide(id, request.getDistanceKm(), request.getDurationMin());
            return ResponseEntity.ok(completed);
        } catch (RideService.InvalidStatusTransitionException e) {
            return errorResponse(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", e.getMessage());
        } catch (RideService.PaymentAlreadyExistsException e) {
            return errorResponse(HttpStatus.CONFLICT, "DUPLICATE_PAYMENT", e.getMessage());
        } catch (RideService.PaymentFailedException e) {
            return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_FAILED", e.getMessage());
        } catch (RuntimeException e) {
            return errorResponse(HttpStatus.NOT_FOUND, "RIDE_NOT_FOUND", e.getMessage());
        }
    }

    private ResponseEntity<Map<String, String>> errorResponse(HttpStatus status, String code, String message) {
        Map<String, String> body = new HashMap<>();
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}