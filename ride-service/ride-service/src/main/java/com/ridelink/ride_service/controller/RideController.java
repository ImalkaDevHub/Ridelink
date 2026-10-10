package com.ridelink.ride_service.controller;

import com.ridelink.ride_service.dto.CancelRideRequest;
import com.ridelink.ride_service.dto.CompleteRideRequest;
import com.ridelink.ride_service.model.Ride;
import com.ridelink.ride_service.service.RideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.ridelink.ride_service.config.ApiExamples.*;

// Error responses for every endpoint here come from GlobalExceptionHandler,
// which maps the service and client exceptions to {"code", "message"} bodies.
@RestController
@RequestMapping("/api/rides")
@Tag(name = "Rides", description = "Ride requests and lifecycle: REQUESTED -> ASSIGNED -> ACCEPTED -> IN_PROGRESS -> COMPLETED, or CANCELLED before IN_PROGRESS")
public class RideController {

    private static final String JSON = "application/json";

    @Autowired
    private RideService rideService;

    // passengerId always comes from the token.
    @PostMapping
    @PreAuthorize("hasAuthority('PASSENGER')")
    @Operation(summary = "Request a ride",
            description = "PASSENGER only; passengerId comes from the token. Atomically reserves an available driver "
                    + "(Driver Service) and asks Fare & Payment Service for an estimate - estimatedFare is null if that "
                    + "service is down. The ride is returned ASSIGNED.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = JSON, examples = @ExampleObject(value = RIDE_REQUEST))))
    @ApiResponse(responseCode = "201", description = "Ride created and a driver assigned",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = Ride.class),
                    examples = @ExampleObject(value = RIDE_ASSIGNED)))
    @ApiResponse(responseCode = "400", description = "Validation failed, malformed JSON or unsupported vehicle type",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = VALIDATION_ERROR)))
    @ApiResponse(responseCode = "401", description = "No valid access token",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = UNAUTHORIZED)))
    @ApiResponse(responseCode = "403", description = "Caller is not a PASSENGER",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = FORBIDDEN)))
    @ApiResponse(responseCode = "409", description = "No driver is available right now",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = NO_DRIVER_AVAILABLE)))
    @ApiResponse(responseCode = "503", description = "Driver Service is unreachable",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = DRIVER_SERVICE_UNAVAILABLE)))
    public ResponseEntity<Ride> requestRide(@Valid @RequestBody Ride ride) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rideService.requestRide(ride));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a ride", description = "The ride's passenger, its assigned driver, or an ADMIN.")
    @ApiResponse(responseCode = "200", description = "Ride found",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = Ride.class),
                    examples = @ExampleObject(value = RIDE_ASSIGNED)))
    @ApiResponse(responseCode = "401", description = "No valid access token",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = UNAUTHORIZED)))
    @ApiResponse(responseCode = "403", description = "Caller is not the ride's passenger, its driver or an ADMIN",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = NOT_PARTICIPANT)))
    @ApiResponse(responseCode = "404", description = "No ride with this id",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = RIDE_NOT_FOUND)))
    public ResponseEntity<Ride> getRide(@PathVariable String id) {
        return ResponseEntity.ok(rideService.getRide(id));
    }

    @GetMapping
    @Operation(summary = "Ride history",
            description = "Newest first. Passengers only see their own rides, drivers only rides assigned to them. "
                    + "Without passengerId/driverId: the caller's own rides (ADMIN: all rides).")
    @ApiResponse(responseCode = "200", description = "Matching rides",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = RIDE_LIST)))
    @ApiResponse(responseCode = "400", description = "Unknown status",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = STATUS_FILTER_ERROR)))
    @ApiResponse(responseCode = "403", description = "A passenger asked for someone else's rides",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = FORBIDDEN)))
    public ResponseEntity<List<Ride>> getRides(
            @Parameter(description = "Passenger account id", example = "66f1c0a2e4b0a1b2c3d4e5f6")
            @RequestParam(required = false) String passengerId,
            @Parameter(description = "Driver profile id", example = "66f1c1b7e4b0a1b2c3d4e5f7")
            @RequestParam(required = false) String driverId,
            @Parameter(description = "Status filter (case-insensitive)", example = "COMPLETED")
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(rideService.findRides(passengerId, driverId, status));
    }

    @PatchMapping("/{id}/accept")
    @PreAuthorize("hasAuthority('DRIVER')")
    @Operation(summary = "Accept a ride (ASSIGNED -> ACCEPTED)", description = "The assigned driver only.")
    @ApiResponse(responseCode = "200", description = "Ride accepted",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = Ride.class),
                    examples = @ExampleObject(value = RIDE_ACCEPTED)))
    @ApiResponse(responseCode = "403", description = "Caller is not the assigned driver",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = NOT_ASSIGNED_DRIVER)))
    @ApiResponse(responseCode = "404", description = "No ride with this id",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = RIDE_NOT_FOUND)))
    @ApiResponse(responseCode = "409", description = "Ride is not ASSIGNED",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = INVALID_STATUS_TRANSITION)))
    public ResponseEntity<Ride> acceptRide(@PathVariable String id) {
        return ResponseEntity.ok(rideService.acceptRide(id));
    }

    @PatchMapping("/{id}/start")
    @PreAuthorize("hasAuthority('DRIVER')")
    @Operation(summary = "Start a ride (ACCEPTED -> IN_PROGRESS)", description = "The assigned driver only.")
    @ApiResponse(responseCode = "200", description = "Ride started",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = Ride.class),
                    examples = @ExampleObject(value = RIDE_IN_PROGRESS)))
    @ApiResponse(responseCode = "403", description = "Caller is not the assigned driver",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = NOT_ASSIGNED_DRIVER)))
    @ApiResponse(responseCode = "404", description = "No ride with this id",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = RIDE_NOT_FOUND)))
    @ApiResponse(responseCode = "409", description = "Ride is not ACCEPTED",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = INVALID_STATUS_TRANSITION)))
    public ResponseEntity<Ride> startRide(@PathVariable String id) {
        return ResponseEntity.ok(rideService.startRide(id));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel a ride",
            description = "The passenger, the assigned driver or an ADMIN, while the ride is REQUESTED, ASSIGNED or "
                    + "ACCEPTED - not once IN_PROGRESS. The driver is released. The body (and reason) is optional.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(
                    mediaType = JSON, examples = @ExampleObject(value = CANCEL_REQUEST))))
    @ApiResponse(responseCode = "200", description = "Ride cancelled and the driver released",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = Ride.class),
                    examples = @ExampleObject(value = RIDE_CANCELLED)))
    @ApiResponse(responseCode = "403", description = "Caller is neither the passenger, the assigned driver nor an ADMIN",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = FORBIDDEN)))
    @ApiResponse(responseCode = "404", description = "No ride with this id",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = RIDE_NOT_FOUND)))
    @ApiResponse(responseCode = "409", description = "Ride is IN_PROGRESS, COMPLETED or already CANCELLED",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = INVALID_STATUS_TRANSITION)))
    public ResponseEntity<Ride> cancelRide(@PathVariable String id,
                                           @Valid @RequestBody(required = false) CancelRideRequest request) {
        return ResponseEntity.ok(rideService.cancelRide(id, request != null ? request.getReason() : null));
    }

    // Only the assigned driver (or an admin) may complete a ride. Rides
    // created before the driver's account id was recorded fall back to the
    // original role-only check: any DRIVER.
    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('DRIVER') or hasAuthority('ADMIN')")
    @Operation(summary = "Complete a ride (IN_PROGRESS -> COMPLETED)",
            description = "The assigned driver (or an ADMIN). Stores the final fare from Fare & Payment Service and "
                    + "releases the driver. For compatibility with the original API, an ASSIGNED or ACCEPTED ride is "
                    + "first moved through the remaining steps. If Fare & Payment is down nothing changes (503).",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = JSON, examples = @ExampleObject(value = COMPLETE_REQUEST))))
    @ApiResponse(responseCode = "200", description = "Ride completed and the final fare stored",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = Ride.class),
                    examples = @ExampleObject(value = RIDE_COMPLETED)))
    @ApiResponse(responseCode = "403", description = "Caller is not the assigned driver nor an ADMIN",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = NOT_ASSIGNED_DRIVER)))
    @ApiResponse(responseCode = "404", description = "No ride with this id",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = RIDE_NOT_FOUND)))
    @ApiResponse(responseCode = "409", description = "Ride is not in a completable state",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = INVALID_STATUS_TRANSITION)))
    @ApiResponse(responseCode = "503", description = "Fare & Payment Service call failed - ride is left unchanged",
            content = @Content(mediaType = JSON, examples = {
                    @ExampleObject(name = "fare unavailable", value = FARE_SERVICE_UNAVAILABLE),
                    @ExampleObject(name = "payment recording failed", value = PAYMENT_FAILED)}))
    public ResponseEntity<Ride> completeRide(@PathVariable String id, @Valid @RequestBody CompleteRideRequest request) {
        return ResponseEntity.ok(rideService.completeRide(id, request.getDistanceKm(), request.getDurationMin()));
    }
}
