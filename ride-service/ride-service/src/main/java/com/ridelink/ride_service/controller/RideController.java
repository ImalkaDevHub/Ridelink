package com.ridelink.ride_service.controller;

import com.ridelink.ride_service.dto.CancelRideRequest;
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

import java.util.List;

// Error responses for every endpoint here come from GlobalExceptionHandler,
// which maps the service and client exceptions to {"code", "message"} bodies.
@RestController
@RequestMapping("/api/rides")
public class RideController {

    @Autowired
    private RideService rideService;

    @PostMapping
    @ApiResponse(responseCode = "201", description = "Ride created and a driver assigned (status ASSIGNED)",
            content = @Content(schema = @Schema(implementation = Ride.class)))
    @ApiResponse(responseCode = "400", description = "Validation failed or malformed JSON")
    @ApiResponse(responseCode = "409", description = "No driver is available right now")
    @ApiResponse(responseCode = "503", description = "Driver Service is unreachable")
    public ResponseEntity<Ride> requestRide(@Valid @RequestBody Ride ride) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rideService.requestRide(ride));
    }

    @GetMapping("/{id}")
    @ApiResponse(responseCode = "200", description = "Ride found",
            content = @Content(schema = @Schema(implementation = Ride.class)))
    @ApiResponse(responseCode = "403", description = "The ride belongs to another passenger")
    @ApiResponse(responseCode = "404", description = "No ride with this id")
    public ResponseEntity<Ride> getRide(@PathVariable String id) {
        return ResponseEntity.ok(rideService.getRide(id));
    }

    @GetMapping
    @ApiResponse(responseCode = "200", description = "Rides, newest first. Without passengerId/driverId: the caller's own rides (ADMIN: all)")
    @ApiResponse(responseCode = "400", description = "Unknown status")
    @ApiResponse(responseCode = "403", description = "A passenger asked for someone else's rides")
    public ResponseEntity<List<Ride>> getRides(
            @RequestParam(required = false) String passengerId,
            @RequestParam(required = false) String driverId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(rideService.findRides(passengerId, driverId, status));
    }

    @PatchMapping("/{id}/accept")
    @PreAuthorize("hasAuthority('DRIVER') or hasAuthority('ADMIN')")
    @ApiResponse(responseCode = "200", description = "Ride accepted (ASSIGNED -> ACCEPTED)",
            content = @Content(schema = @Schema(implementation = Ride.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not the assigned driver")
    @ApiResponse(responseCode = "404", description = "No ride with this id")
    @ApiResponse(responseCode = "409", description = "Ride is not ASSIGNED")
    public ResponseEntity<Ride> acceptRide(@PathVariable String id) {
        return ResponseEntity.ok(rideService.acceptRide(id));
    }

    @PatchMapping("/{id}/start")
    @PreAuthorize("hasAuthority('DRIVER') or hasAuthority('ADMIN')")
    @ApiResponse(responseCode = "200", description = "Ride started (ACCEPTED -> IN_PROGRESS)",
            content = @Content(schema = @Schema(implementation = Ride.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not the assigned driver")
    @ApiResponse(responseCode = "404", description = "No ride with this id")
    @ApiResponse(responseCode = "409", description = "Ride is not ACCEPTED")
    public ResponseEntity<Ride> startRide(@PathVariable String id) {
        return ResponseEntity.ok(rideService.startRide(id));
    }

    @PatchMapping("/{id}/cancel")
    @ApiResponse(responseCode = "200", description = "Ride cancelled and the driver released",
            content = @Content(schema = @Schema(implementation = Ride.class)))
    @ApiResponse(responseCode = "403", description = "Caller is neither the passenger, the assigned driver nor an ADMIN")
    @ApiResponse(responseCode = "404", description = "No ride with this id")
    @ApiResponse(responseCode = "409", description = "Ride is IN_PROGRESS, COMPLETED or already CANCELLED")
    public ResponseEntity<Ride> cancelRide(@PathVariable String id,
                                           @Valid @RequestBody(required = false) CancelRideRequest request) {
        return ResponseEntity.ok(rideService.cancelRide(id, request != null ? request.getReason() : null));
    }

    // Only the assigned driver (or an admin) may complete a ride. Rides
    // created before the driver's account id was recorded fall back to the
    // original role-only check: any DRIVER.
    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('DRIVER') or hasAuthority('ADMIN')")
    @ApiResponse(responseCode = "200", description = "Ride completed, final fare stored (and payment recorded) in Fare & Payment Service",
            content = @Content(schema = @Schema(implementation = Ride.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not the assigned driver nor an ADMIN")
    @ApiResponse(responseCode = "404", description = "No ride with this id")
    @ApiResponse(responseCode = "409", description = "Ride is not in a completable state")
    @ApiResponse(responseCode = "503", description = "Fare & Payment Service call failed - ride is left unchanged")
    public ResponseEntity<Ride> completeRide(@PathVariable String id, @Valid @RequestBody CompleteRideRequest request) {
        return ResponseEntity.ok(rideService.completeRide(id, request.getDistanceKm(), request.getDurationMin()));
    }
}
