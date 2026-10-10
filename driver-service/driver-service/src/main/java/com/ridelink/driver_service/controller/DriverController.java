package com.ridelink.driver_service.controller;

import com.ridelink.driver_service.dto.DriverUpdateRequest;
import com.ridelink.driver_service.dto.LocationUpdateRequest;
import com.ridelink.driver_service.dto.ServiceAreaRequest;
import com.ridelink.driver_service.model.Driver;
import com.ridelink.driver_service.service.DriverService;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    // The owning DRIVER (profile.accountId == caller's account id) or an ADMIN.
    private static final String OWNER_OR_ADMIN =
            "hasAuthority('ADMIN') or (hasAuthority('DRIVER') and @driverAccess.isOwner(#id, authentication))";

    @Autowired
    private DriverService driverService;

    // The profile always belongs to the caller: accountId comes from the
    // token, never the body.
    @PostMapping
    @PreAuthorize("hasAuthority('DRIVER') or hasAuthority('ADMIN')")
    @ApiResponse(responseCode = "201", description = "Driver profile created",
            content = @Content(schema = @Schema(implementation = Driver.class)))
    @ApiResponse(responseCode = "403", description = "Caller is neither a DRIVER nor an ADMIN")
    public ResponseEntity<Driver> create(@Valid @RequestBody Driver driver) {
        String accountIdStr = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        driver.setAccountId(accountIdStr);
        return ResponseEntity.status(HttpStatus.CREATED).body(driverService.createDriver(driver));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<Driver>> getAllDrivers() {
        return ResponseEntity.ok(driverService.getAllDrivers());
    }

    @GetMapping("/available")
    public ResponseEntity<List<Driver>> getAvailable(@RequestParam(required = false) String type) {
        if (type != null && !type.isBlank()) {
            return ResponseEntity.ok(driverService.getAvailableDriversByType(type));
        }
        return ResponseEntity.ok(driverService.getAvailableDrivers());
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('DRIVER')")
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
    @ApiResponse(responseCode = "200", description = "Availability updated",
            content = @Content(schema = @Schema(implementation = Driver.class)))
    @ApiResponse(responseCode = "401", description = "No valid access token")
    @ApiResponse(responseCode = "403", description = "Caller is neither the owning DRIVER nor an ADMIN")
    @ApiResponse(responseCode = "404", description = "No driver with this id")
    public ResponseEntity<?> setAvailability(@PathVariable String id, @RequestParam boolean available) {
        try {
            return ResponseEntity.ok(driverService.setAvailability(id, available));
        } catch (DriverService.DriverNotFoundException e) {
            return errorResponse(HttpStatus.NOT_FOUND, "DRIVER_NOT_FOUND", e.getMessage());
        }
    }

    // Literal paths (/available, /me, /eligible, /reserve) take precedence
    // over /{id}, so these don't shadow each other.

    @GetMapping("/eligible")
    @ApiResponse(responseCode = "200", description = "Available drivers matching the optional area and vehicle type")
    @ApiResponse(responseCode = "400", description = "vehicleType is not a supported type")
    public ResponseEntity<List<Driver>> getEligible(@RequestParam(required = false) String area,
                                                    @RequestParam(required = false) String vehicleType) {
        return ResponseEntity.ok(driverService.findEligible(area, vehicleType));
    }

    // Atomically picks an eligible driver and marks them unavailable, so two
    // concurrent ride requests can never get the same driver. Intended for
    // ride-service, forwarding the caller's token.
    @PostMapping("/reserve")
    @PreAuthorize("isAuthenticated()")
    @ApiResponse(responseCode = "200", description = "Driver reserved (now unavailable); includes accountId",
            content = @Content(schema = @Schema(implementation = Driver.class)))
    @ApiResponse(responseCode = "400", description = "vehicleType is not a supported type")
    @ApiResponse(responseCode = "409", description = "No eligible driver is available")
    public ResponseEntity<Driver> reserve(@RequestParam(required = false) String area,
                                          @RequestParam(required = false) String vehicleType) {
        return ResponseEntity.ok(driverService.reserve(area, vehicleType));
    }

    @GetMapping("/{id}")
    @ApiResponse(responseCode = "200", description = "Driver found",
            content = @Content(schema = @Schema(implementation = Driver.class)))
    @ApiResponse(responseCode = "404", description = "No driver with this id")
    public ResponseEntity<Driver> getById(@PathVariable String id) {
        return ResponseEntity.ok(driverService.getDriver(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(OWNER_OR_ADMIN)
    @ApiResponse(responseCode = "200", description = "Profile updated",
            content = @Content(schema = @Schema(implementation = Driver.class)))
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "403", description = "Caller is neither the owning DRIVER nor an ADMIN")
    @ApiResponse(responseCode = "404", description = "No driver with this id")
    @ApiResponse(responseCode = "409", description = "Vehicle number already registered")
    public ResponseEntity<Driver> update(@PathVariable String id, @Valid @RequestBody DriverUpdateRequest request) {
        return ResponseEntity.ok(driverService.updateDriver(id, request));
    }

    @PatchMapping("/{id}/location")
    @PreAuthorize(OWNER_OR_ADMIN)
    @ApiResponse(responseCode = "200", description = "Location updated",
            content = @Content(schema = @Schema(implementation = Driver.class)))
    @ApiResponse(responseCode = "400", description = "lat/lng missing or out of range")
    @ApiResponse(responseCode = "403", description = "Caller is neither the owning DRIVER nor an ADMIN")
    @ApiResponse(responseCode = "404", description = "No driver with this id")
    public ResponseEntity<Driver> updateLocation(@PathVariable String id, @Valid @RequestBody LocationUpdateRequest request) {
        return ResponseEntity.ok(driverService.updateLocation(id, request.getLat(), request.getLng()));
    }

    @PatchMapping("/{id}/service-area")
    @PreAuthorize(OWNER_OR_ADMIN)
    @ApiResponse(responseCode = "200", description = "Service area updated",
            content = @Content(schema = @Schema(implementation = Driver.class)))
    @ApiResponse(responseCode = "400", description = "serviceArea missing")
    @ApiResponse(responseCode = "403", description = "Caller is neither the owning DRIVER nor an ADMIN")
    @ApiResponse(responseCode = "404", description = "No driver with this id")
    public ResponseEntity<Driver> updateServiceArea(@PathVariable String id, @Valid @RequestBody ServiceAreaRequest request) {
        return ResponseEntity.ok(driverService.updateServiceArea(id, request.getServiceArea()));
    }

    // Counterpart to /reserve: marks the driver available again when a ride
    // is completed or cancelled. Atomic, and safe to call twice.
    @PostMapping("/{id}/release")
    @PreAuthorize("isAuthenticated()")
    @ApiResponse(responseCode = "200", description = "Driver released (now available)",
            content = @Content(schema = @Schema(implementation = Driver.class)))
    @ApiResponse(responseCode = "404", description = "No driver with this id")
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
