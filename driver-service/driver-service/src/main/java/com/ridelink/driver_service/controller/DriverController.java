package com.ridelink.driver_service.controller;

import com.ridelink.driver_service.dto.DriverUpdateRequest;
import com.ridelink.driver_service.dto.LocationUpdateRequest;
import com.ridelink.driver_service.dto.ServiceAreaRequest;
import com.ridelink.driver_service.model.Driver;
import com.ridelink.driver_service.service.DriverService;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ridelink.driver_service.config.ApiExamples.*;

@RestController
@RequestMapping("/api/drivers")
@Tag(name = "Drivers", description = "Driver and vehicle profiles, availability, location, and atomic reservation for rides")
public class DriverController {

    // The owning DRIVER (profile.accountId == caller's account id) or an ADMIN.
    private static final String OWNER_OR_ADMIN =
            "hasAuthority('ADMIN') or (hasAuthority('DRIVER') and @driverAccess.isOwner(#id, authentication))";

    private static final String JSON = "application/json";

    @Autowired
    private DriverService driverService;

    // The profile always belongs to the caller: accountId comes from the
    // token, never the body.
    @PostMapping
    @PreAuthorize("hasAuthority('DRIVER') or hasAuthority('ADMIN')")
    @Operation(summary = "Create a driver profile",
            description = "DRIVER or ADMIN. accountId is taken from the token. vehicleType is stored upper-case; "
                    + "vehicleNumber must be unique.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = JSON, examples = @ExampleObject(value = CREATE_REQUEST))))
    @ApiResponse(responseCode = "201", description = "Driver profile created",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = Driver.class),
                    examples = @ExampleObject(value = DRIVER)))
    @ApiResponse(responseCode = "400", description = "Validation failed",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = VALIDATION_ERROR)))
    @ApiResponse(responseCode = "401", description = "No valid access token",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = UNAUTHORIZED)))
    @ApiResponse(responseCode = "403", description = "Caller is neither a DRIVER nor an ADMIN",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = FORBIDDEN)))
    @ApiResponse(responseCode = "409", description = "Vehicle number already registered",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = VEHICLE_ALREADY_EXISTS)))
    public ResponseEntity<Driver> create(@Valid @RequestBody Driver driver) {
        String accountIdStr = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        driver.setAccountId(accountIdStr);
        return ResponseEntity.status(HttpStatus.CREATED).body(driverService.createDriver(driver));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "List every driver", description = "ADMIN only.")
    @ApiResponse(responseCode = "200", description = "All drivers",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = DRIVER_LIST)))
    @ApiResponse(responseCode = "403", description = "Caller does not have the ADMIN authority",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = FORBIDDEN)))
    public ResponseEntity<List<Driver>> getAllDrivers() {
        return ResponseEntity.ok(driverService.getAllDrivers());
    }

    @GetMapping("/available")
    @Operation(summary = "List available drivers", description = "Any authenticated user. Optionally filtered by vehicle type (case-insensitive).")
    @ApiResponse(responseCode = "200", description = "Available drivers",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = DRIVER_LIST)))
    @ApiResponse(responseCode = "401", description = "No valid access token",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = UNAUTHORIZED)))
    public ResponseEntity<List<Driver>> getAvailable(
            @Parameter(description = "Vehicle type filter", example = "SEDAN") @RequestParam(required = false) String type) {
        if (type != null && !type.isBlank()) {
            return ResponseEntity.ok(driverService.getAvailableDriversByType(type));
        }
        return ResponseEntity.ok(driverService.getAvailableDrivers());
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('DRIVER')")
    @Operation(summary = "Get the caller's own driver profile", description = "DRIVER only.")
    @ApiResponse(responseCode = "200", description = "The caller's profile",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = Driver.class),
                    examples = @ExampleObject(value = DRIVER)))
    @ApiResponse(responseCode = "403", description = "Caller is not a DRIVER",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = FORBIDDEN)))
    @ApiResponse(responseCode = "404", description = "No driver profile for this account",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = DRIVER_NOT_FOUND)))
    public ResponseEntity<?> getMyProfile() {
        try {
            String accountIdStr = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
            return ResponseEntity.ok(driverService.getDriverByAccountId(accountIdStr));
        } catch (DriverService.DriverNotFoundException e) {
            return errorResponse(HttpStatus.NOT_FOUND, "DRIVER_NOT_FOUND", e.getMessage());
        }
    }

    // A driver toggling their own availability, or an ADMIN. Ride
    // assignment no longer uses this (see /reserve and /release).
    @PatchMapping("/{id}/availability")
    @PreAuthorize(OWNER_OR_ADMIN)
    @Operation(summary = "Go online / offline", description = "The owning DRIVER or an ADMIN.")
    @ApiResponse(responseCode = "200", description = "Availability updated",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = Driver.class),
                    examples = @ExampleObject(value = DRIVER)))
    @ApiResponse(responseCode = "401", description = "No valid access token",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = UNAUTHORIZED)))
    @ApiResponse(responseCode = "403", description = "Caller is neither the owning DRIVER nor an ADMIN",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = FORBIDDEN)))
    @ApiResponse(responseCode = "404", description = "No driver with this id",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = DRIVER_NOT_FOUND)))
    public ResponseEntity<?> setAvailability(@PathVariable String id,
                                             @Parameter(example = "true") @RequestParam boolean available) {
        try {
            return ResponseEntity.ok(driverService.setAvailability(id, available));
        } catch (DriverService.DriverNotFoundException e) {
            return errorResponse(HttpStatus.NOT_FOUND, "DRIVER_NOT_FOUND", e.getMessage());
        }
    }

    // Literal paths (/available, /me, /eligible, /reserve) take precedence
    // over /{id}, so these don't shadow each other.

    @GetMapping("/eligible")
    @Operation(summary = "Find eligible drivers",
            description = "Any authenticated user. Available drivers, optionally narrowed by service area and vehicle "
                    + "type (both case-insensitive exact matches).")
    @ApiResponse(responseCode = "200", description = "Matching available drivers",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = DRIVER_LIST)))
    @ApiResponse(responseCode = "400", description = "vehicleType is not a supported type",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = VALIDATION_ERROR)))
    public ResponseEntity<List<Driver>> getEligible(
            @Parameter(description = "Service area", example = "Malabe") @RequestParam(required = false) String area,
            @Parameter(description = "Vehicle type", example = "SEDAN") @RequestParam(required = false) String vehicleType) {
        return ResponseEntity.ok(driverService.findEligible(area, vehicleType));
    }

    // Atomically picks an eligible driver and marks them unavailable, so two
    // concurrent ride requests can never get the same driver. Intended for
    // ride-service, forwarding the caller's token.
    @PostMapping("/reserve")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Reserve an eligible driver (internal)",
            description = "Used by ride-service. One atomic findAndModify picks an available driver and marks them "
                    + "unavailable, so concurrent requests never get the same driver.")
    @ApiResponse(responseCode = "200", description = "Driver reserved (now unavailable); includes accountId",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = Driver.class),
                    examples = @ExampleObject(value = DRIVER_RESERVED)))
    @ApiResponse(responseCode = "400", description = "vehicleType is not a supported type",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = VALIDATION_ERROR)))
    @ApiResponse(responseCode = "409", description = "No eligible driver is available",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = NO_DRIVER_AVAILABLE)))
    public ResponseEntity<Driver> reserve(
            @Parameter(description = "Service area", example = "Malabe") @RequestParam(required = false) String area,
            @Parameter(description = "Vehicle type", example = "SEDAN") @RequestParam(required = false) String vehicleType) {
        return ResponseEntity.ok(driverService.reserve(area, vehicleType));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a driver", description = "Any authenticated user.")
    @ApiResponse(responseCode = "200", description = "Driver found",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = Driver.class),
                    examples = @ExampleObject(value = DRIVER)))
    @ApiResponse(responseCode = "401", description = "No valid access token",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = UNAUTHORIZED)))
    @ApiResponse(responseCode = "404", description = "No driver with this id",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = DRIVER_NOT_FOUND)))
    public ResponseEntity<Driver> getById(@PathVariable String id) {
        return ResponseEntity.ok(driverService.getDriver(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(OWNER_OR_ADMIN)
    @Operation(summary = "Update a driver profile",
            description = "The owning DRIVER or an ADMIN. Partial update: only the fields sent are changed.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = JSON, examples = @ExampleObject(value = UPDATE_REQUEST))))
    @ApiResponse(responseCode = "200", description = "Profile updated",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = Driver.class),
                    examples = @ExampleObject(value = DRIVER)))
    @ApiResponse(responseCode = "400", description = "Validation failed",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = VALIDATION_ERROR)))
    @ApiResponse(responseCode = "403", description = "Caller is neither the owning DRIVER nor an ADMIN",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = FORBIDDEN)))
    @ApiResponse(responseCode = "404", description = "No driver with this id",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = DRIVER_NOT_FOUND)))
    @ApiResponse(responseCode = "409", description = "Vehicle number already registered",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = VEHICLE_ALREADY_EXISTS)))
    public ResponseEntity<Driver> update(@PathVariable String id, @Valid @RequestBody DriverUpdateRequest request) {
        return ResponseEntity.ok(driverService.updateDriver(id, request));
    }

    @PatchMapping("/{id}/location")
    @PreAuthorize(OWNER_OR_ADMIN)
    @Operation(summary = "Update the driver's current location", description = "The owning DRIVER or an ADMIN.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = JSON, examples = @ExampleObject(value = LOCATION_REQUEST))))
    @ApiResponse(responseCode = "200", description = "Location updated",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = Driver.class),
                    examples = @ExampleObject(value = DRIVER)))
    @ApiResponse(responseCode = "400", description = "lat/lng missing or out of range",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = LOCATION_VALIDATION_ERROR)))
    @ApiResponse(responseCode = "403", description = "Caller is neither the owning DRIVER nor an ADMIN",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = FORBIDDEN)))
    @ApiResponse(responseCode = "404", description = "No driver with this id",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = DRIVER_NOT_FOUND)))
    public ResponseEntity<Driver> updateLocation(@PathVariable String id, @Valid @RequestBody LocationUpdateRequest request) {
        return ResponseEntity.ok(driverService.updateLocation(id, request.getLat(), request.getLng()));
    }

    @PatchMapping("/{id}/service-area")
    @PreAuthorize(OWNER_OR_ADMIN)
    @Operation(summary = "Change the driver's service area", description = "The owning DRIVER or an ADMIN.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = JSON, examples = @ExampleObject(value = SERVICE_AREA_REQUEST))))
    @ApiResponse(responseCode = "200", description = "Service area updated",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = Driver.class),
                    examples = @ExampleObject(value = DRIVER)))
    @ApiResponse(responseCode = "400", description = "serviceArea missing",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = VALIDATION_ERROR)))
    @ApiResponse(responseCode = "403", description = "Caller is neither the owning DRIVER nor an ADMIN",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = FORBIDDEN)))
    @ApiResponse(responseCode = "404", description = "No driver with this id",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = DRIVER_NOT_FOUND)))
    public ResponseEntity<Driver> updateServiceArea(@PathVariable String id, @Valid @RequestBody ServiceAreaRequest request) {
        return ResponseEntity.ok(driverService.updateServiceArea(id, request.getServiceArea()));
    }

    // Counterpart to /reserve: marks the driver available again when a ride
    // is completed or cancelled. Atomic, and safe to call twice.
    @PostMapping("/{id}/release")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Release a reserved driver (internal)",
            description = "Used by ride-service when a ride is completed or cancelled. Atomic and idempotent.")
    @ApiResponse(responseCode = "200", description = "Driver released (now available)",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = Driver.class),
                    examples = @ExampleObject(value = DRIVER)))
    @ApiResponse(responseCode = "401", description = "No valid access token",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = UNAUTHORIZED)))
    @ApiResponse(responseCode = "404", description = "No driver with this id",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = DRIVER_NOT_FOUND)))
    public ResponseEntity<Driver> release(@PathVariable String id) {
        return ResponseEntity.ok(driverService.release(id));
    }

    private ResponseEntity<Map<String, String>> errorResponse(HttpStatus status, String code, String message) {
        Map<String, String> body = new HashMap<>();
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
