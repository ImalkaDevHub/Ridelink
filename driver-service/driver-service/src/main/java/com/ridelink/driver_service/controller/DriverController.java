package com.ridelink.driver_service.controller;

import com.ridelink.driver_service.model.Driver;
import com.ridelink.driver_service.service.DriverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
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

    @Autowired
    private DriverService driverService;

    @PostMapping
    public ResponseEntity<Driver> create(@Valid @RequestBody Driver driver) {
        String accountIdStr = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        driver.setAccountId(accountIdStr);
        return ResponseEntity.status(HttpStatus.CREATED).body(driverService.createDriver(driver));
    }

    // Public - this is the interservice lookup ride-service's DriverClient
    // calls with no token.
    @GetMapping("/available")
    @SecurityRequirements
    public List<Driver> getAvailable() {
        return driverService.getAvailableDrivers();
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

    // Dual-purpose endpoint: called by a driver toggling their own
    // availability (requires the DRIVER role) AND by ride-service's
    // DriverClient with no token at all, to reserve a driver on ride
    // assignment. isAnonymous() lets that existing interservice call
    // through unchanged while still rejecting a non-DRIVER token (e.g. a
    // PASSENGER's) with 403. @SecurityRequirements is left empty here too,
    // since a token is optional rather than required - Swagger UI's
    // "Authorize" flow is only needed if you want to exercise this as the
    // driver-role self-service call rather than the anonymous one.
    @Operation(description = "Optionally requires a DRIVER-role bearer token: supply one to exercise this "
            + "as a driver's own self-service toggle, or omit it to exercise the anonymous interservice call "
            + "ride-service makes on ride assignment.")
    @PatchMapping("/{id}/availability")
    @PreAuthorize("isAnonymous() or hasAuthority('DRIVER')")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Availability updated",
            content = @Content(schema = @Schema(implementation = Driver.class)))
    @ApiResponse(responseCode = "403", description = "A non-DRIVER token was supplied")
    @ApiResponse(responseCode = "404", description = "No driver with this id")
    public ResponseEntity<?> setAvailability(@PathVariable String id, @RequestParam boolean available) {
        try {
            return ResponseEntity.ok(driverService.setAvailability(id, available));
        } catch (DriverService.DriverNotFoundException e) {
            return errorResponse(HttpStatus.NOT_FOUND, "DRIVER_NOT_FOUND", e.getMessage());
        }
    }

    private ResponseEntity<Map<String, String>> errorResponse(HttpStatus status, String code, String message) {
        Map<String, String> body = new HashMap<>();
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
