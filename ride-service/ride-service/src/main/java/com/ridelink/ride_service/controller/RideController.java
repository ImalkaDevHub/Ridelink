package com.ridelink.ride_service.controller;

import com.ridelink.ride_service.client.DriverClient;
import com.ridelink.ride_service.dto.CompleteRideRequest;
import com.ridelink.ride_service.model.Ride;
import com.ridelink.ride_service.service.RideService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

    // Controller methods return ResponseEntity<?> because a single method
    // can return either the success DTO or an error body - springdoc can't
    // infer a schema from a wildcard generic type, so each @ApiResponse
    // below spells out the real status code and schema explicitly.

    @PostMapping
    @ApiResponse(responseCode = "201", description = "Ride created and a driver assigned",
            content = @Content(schema = @Schema(implementation = Ride.class)))
    @ApiResponse(responseCode = "400", description = "Validation failed or malformed JSON")
    @ApiResponse(responseCode = "409", description = "No driver is available right now")
    @ApiResponse(responseCode = "503", description = "Driver Service is unreachable")
    public ResponseEntity<?> requestRide(@Valid @RequestBody Ride ride) {
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
    @ApiResponse(responseCode = "200", description = "Ride found",
            content = @Content(schema = @Schema(implementation = Ride.class)))
    @ApiResponse(responseCode = "404", description = "No ride with this id")
    public ResponseEntity<?> getRide(@PathVariable String id) {
        try {
            return ResponseEntity.ok(rideService.getRide(id));
        } catch (RuntimeException e) {
            return errorResponse(HttpStatus.NOT_FOUND, "RIDE_NOT_FOUND", e.getMessage());
        }
    }

    @GetMapping
    @ApiResponse(responseCode = "200", description = "List of rides for passenger or driver")
    public ResponseEntity<?> getRides(
            @RequestParam(required = false) String passengerId,
            @RequestParam(required = false) Long driverId,
            @RequestParam(required = false) String status) {
        
        java.util.List<com.ridelink.ride_service.model.Ride> rides = java.util.Collections.emptyList();

        if (passengerId != null) {
            rides = rideService.getRidesByPassenger(passengerId);
        } else if (driverId != null) {
            rides = rideService.getRidesByDriver(driverId);
        }

        if (status != null && !rides.isEmpty()) {
            rides = rides.stream()
                         .filter(ride -> status.equalsIgnoreCase(ride.getStatus()))
                         .collect(java.util.stream.Collectors.toList());
        }

        return ResponseEntity.ok(rides);
    }

    // Only a driver (or an admin) may complete a ride. This is a role-level
    // check, not a per-resource ownership check - the JWT carries no
    // driverId claim, and there is no linkage today between an
    // account-service account and a driver-service Driver record, so
    // "only the driver actually assigned to this ride" can't be verified
    // yet. Documented limitation / follow-up for the report.
    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('DRIVER') or hasAuthority('ADMIN')")
    @ApiResponse(responseCode = "200", description = "Ride completed and payment recorded in Fare & Payment Service",
            content = @Content(schema = @Schema(implementation = Ride.class)))
    @ApiResponse(responseCode = "403", description = "Caller is neither a DRIVER nor an ADMIN")
    @ApiResponse(responseCode = "404", description = "No ride with this id")
    @ApiResponse(responseCode = "409", description = "Ride is not in a completable state, or was already paid")
    @ApiResponse(responseCode = "503", description = "Fare & Payment Service call failed - ride is left unchanged")
    public ResponseEntity<?> completeRide(@PathVariable String id, @Valid @RequestBody CompleteRideRequest request) {
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
